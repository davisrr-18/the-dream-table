package com.davisr.thedreamtable.client.hologram;

import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.client.FarmFaces;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Draws the translucent blocks still to be built and the yellow aim marker. The next block glows, or turns red when it is blocked.
 * The whole farm turns red when the server says it can't be built where it was placed.
 * <p>
 * The parts are split into {@link GhostPlan#CHUNK chunks}. A chunk that is entirely unbuilt and inside the visible layers is
 * baked once into a GPU buffer and redrawn each frame with one tint, passed through the shader's color modulator, so the
 * pulse needs no rebuild. The chunk with the next block is drawn directly each frame, since its parts change as blocks are
 * placed. A baked chunk is rebuilt only when the visible layer window changes, and first bakes and face builds are spread
 * over frames by {@link #BUDGET}.
 * <p>
 * Parts are not ordered by height (water, lava and plants come after every other block), so a face may be covered by a
 * built block above the window. Baked chunks keep such faces out of their buffer ("seams") and draw them each frame with
 * the exact rule, so the result matches drawing every part directly.
 */
public final class GhostRenderer {
	/** Per frame, how long the hologram may spend building faces and baking buffers. */
	private static final long BUILD_NANOS = 3_000_000L;
	/** The baked buffers kept, as a number of block parts (about 70 bytes of GPU memory each). */
	static final long BAKED_PARTS = 90_000L;
	private static final long PLAN_CHUNKS = 8_000L;
	private static final FrameBudget BUDGET = new FrameBudget(BUILD_NANOS, System::nanoTime);
	private static final WeightedCache<MeshKey, Mesh> MESHES = new WeightedCache<>(BAKED_PARTS, (key, mesh) -> mesh.close());
	private static final WeightedCache<String, Planned> PLANS = new WeightedCache<>(PLAN_CHUNKS, (key, planned) -> { });
	private static final Matrix4f IDENTITY = new Matrix4f();
	private static final Matrix4f SCRATCH = new Matrix4f();
	private static final Tint NEUTRAL = new Tint(1.0F, 1.0F, 1.0F, 255);
	private static final List<VertexBuffer> BAKED = new ArrayList<>();
	private static final List<Mesh> BAKED_MESHES = new ArrayList<>();
	private static int bakedThisFrame;

	private GhostRenderer() {
	}

	/** Once per rendered frame, before anything is drawn: restarts the build budget and the frame count the caches use. */
	public static void beginFrame() {
		FarmFaces.beginFrame(BUDGET.beginFrame());
		bakedThisFrame = 0;
	}

	/** Frees every baked buffer. Called when resources reload (the atlas changed) and when the player leaves the world. */
	public static void release() {
		MESHES.clear();
		PLANS.clear();
	}

	public static void drawCursor(MatrixStack matrices, BlockPos cursor, float wave) {
		BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		float mark = 0.35F + 0.2F * wave;
		WorldRenderer.renderFilledBox(matrices, buffer, cursor.getX() + 0.15, cursor.getY(), cursor.getZ() + 0.15, cursor.getX() + 0.85, cursor.getY() + 0.08, cursor.getZ() + 0.85, 0.95F, 0.86F, 0.45F, mark);
		BuiltBuffer built = buffer.endNullable();
		if (built == null) {
			return;
		}
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		BufferRenderer.drawWithGlobalProgram(built);
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	/**
	 * Draws the blocks still to build from the cached {@link FarmFaces}. A face covered by another block of the farm is
	 * skipped when that block is already built or drawn too, so the top of the last shown layer still gets its faces.
	 */
	public static void drawGhosts(MatrixStack matrices, FarmTemplate template, TableSession table,
			BlockPos anchor, Direction facing, boolean mirrored, int shownPlaced, boolean blocked, boolean refused, int topLayer, float wave) {
		String swapKey = table == null ? "" : table.swapKey();
		UnaryOperator<BlockState> swap = table == null ? state -> state : table::swapState;
		List<FarmFaces.Part> parts = FarmFaces.ready(template, swapKey, swap, facing, mirrored, BUDGET);
		if (parts == null || parts.isEmpty()) {
			return;
		}
		String key = FarmFaces.key(template, swapKey, facing, mirrored);
		long frame = BUDGET.frame();
		GhostPlan plan = plan(key, parts, template.steps(), frame);
		int window = plan.window(topLayer);
		int first = plan.firstUnbuilt(shownPlaced);
		if (first >= plan.parts()) {
			return;
		}
		List<FarmStep> steps = template.steps();
		matrices.push();
		matrices.translate(anchor.getX(), anchor.getY(), anchor.getZ());
		Matrix4f world = new Matrix4f(matrices.peek().getPositionMatrix());
		BAKED.clear();
		BAKED_MESHES.clear();
		for (int chunk = 0; chunk < plan.chunks(); chunk++) {
			if (plan.kind(chunk, first, window) != GhostPlan.Kind.BAKED) {
				continue;
			}
			Mesh mesh = mesh(key, parts, plan, steps, chunk, window, frame);
			if (mesh != null) {
				BAKED_MESHES.add(mesh);
				if (mesh.buffer() != null) {
					BAKED.add(mesh.buffer());
				}
			}
		}
		prepareState();
		if (!BAKED.isEmpty()) {
			drawBaked(world, Tint.of(false, blocked, refused, wave));
		}
		BAKED.clear();
		drawLive(matrices, parts, steps, plan, first / GhostPlan.CHUNK, shownPlaced, window, blocked, refused, wave);
		BAKED_MESHES.clear();
		matrices.pop();
		// The world render after this stage must not inherit blending.
		RenderSystem.disableBlend();
	}

	private static GhostPlan plan(String key, List<FarmFaces.Part> parts, List<FarmStep> steps, long frame) {
		Planned planned = PLANS.get(key, frame);
		if (planned == null || planned.parts != parts) {
			int[] stepOf = new int[parts.size()];
			int[] layerOf = new int[parts.size()];
			for (int index = 0; index < parts.size(); index++) {
				stepOf[index] = parts.get(index).step();
				layerOf[index] = steps.get(stepOf[index]).y();
			}
			planned = new Planned(parts, new GhostPlan(stepOf, layerOf));
			PLANS.put(key, planned, planned.plan.chunks() + 1, frame);
		}
		return planned.plan;
	}

	/** The baked chunk, or null when this frame's build budget is used up and it has to wait. */
	private static Mesh mesh(String key, List<FarmFaces.Part> parts, GhostPlan plan, List<FarmStep> steps, int chunk, int window, long frame) {
		MeshKey meshKey = new MeshKey(key, window, chunk);
		Mesh mesh = MESHES.get(meshKey, frame);
		if (mesh != null) {
			return mesh;
		}
		// At least one chunk per frame, so a slow machine still finishes.
		if (bakedThisFrame > 0 && BUDGET.spent()) {
			return null;
		}
		bakedThisFrame++;
		mesh = bake(parts, plan, steps, chunk, window);
		MESHES.put(meshKey, mesh, plan.chunkEnd(chunk) - plan.chunkStart(chunk), frame);
		return mesh;
	}

	/**
	 * Bakes the faces of a chunk that the window shows. A face covered by a block inside the window is left out, as the
	 * live rule does whatever the covering block's build state. A face covered by a block above the window depends on
	 * whether that block is built, so it is recorded as a seam and drawn each frame instead.
	 */
	private static Mesh bake(List<FarmFaces.Part> parts, GhostPlan plan, List<FarmStep> steps, int chunk, int window) {
		BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		List<Integer> seams = new ArrayList<>();
		for (int index = plan.chunkStart(chunk); index < plan.chunkEnd(chunk); index++) {
			if (plan.layer(index) > window) {
				continue;
			}
			FarmFaces.Part part = parts.get(index);
			Matrix4f matrix = place(IDENTITY, part);
			if (part.quads().isEmpty()) {
				ghostCube(buffer, matrix, part.fallback(), NEUTRAL);
				continue;
			}
			for (int quad = 0; quad < part.quads().size(); quad++) {
				int cover = part.coveredBy()[quad];
				if (cover < 0) {
					ghostQuad(buffer, matrix, part.quads().get(quad), part.tints()[quad], NEUTRAL);
				} else if (steps.get(cover).y() > window) {
					seams.add(index);
					seams.add(quad);
				}
			}
		}
		BuiltBuffer built = buffer.endNullable();
		int[] seamList = seams.stream().mapToInt(Integer::intValue).toArray();
		if (built == null) {
			return new Mesh(null, seamList);
		}
		VertexBuffer vertices = new VertexBuffer(VertexBuffer.Usage.STATIC);
		vertices.bind();
		vertices.upload(built);
		VertexBuffer.unbind();
		return new Mesh(vertices, seamList);
	}

	private static void prepareState() {
		RenderSystem.setShaderTexture(0, PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
		RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
	}

	/** Draws the baked chunks with the shader set up once; the tint goes through the shader's color modulator. */
	private static void drawBaked(Matrix4f world, Tint tint) {
		ShaderProgram shader = RenderSystem.getShader();
		if (shader == null) {
			return;
		}
		Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(world);
		shader.initializeUniforms(VertexFormat.DrawMode.QUADS, view, RenderSystem.getProjectionMatrix(), MinecraftClient.getInstance().getWindow());
		if (shader.colorModulator != null) {
			shader.colorModulator.set(tint.red(), tint.green(), tint.blue(), tint.alpha() / 255.0F);
		}
		shader.bind();
		for (VertexBuffer vertices : BAKED) {
			vertices.bind();
			vertices.draw();
		}
		VertexBuffer.unbind();
		shader.unbind();
	}

	/**
	 * The live chunk, rebuilt every frame since its parts get built one by one and the next one is lit, plus the seams of
	 * the baked chunks, which depend on what is built.
	 */
	private static void drawLive(MatrixStack matrices, List<FarmFaces.Part> parts, List<FarmStep> steps, GhostPlan plan, int chunk,
			int shownPlaced, int window, boolean blocked, boolean refused, float wave) {
		BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		Matrix4f base = matrices.peek().getPositionMatrix();
		for (int index = plan.chunkStart(chunk); index < plan.chunkEnd(chunk); index++) {
			FarmFaces.Part part = parts.get(index);
			if (part.step() < shownPlaced || plan.layer(index) > window) {
				continue;
			}
			emit(buffer, base, part, steps, shownPlaced, window, Tint.of(part.step() == shownPlaced, blocked, refused, wave));
		}
		Tint seam = Tint.of(false, blocked, refused, wave);
		for (Mesh mesh : BAKED_MESHES) {
			int[] seams = mesh.seams();
			for (int at = 0; at < seams.length; at += 2) {
				FarmFaces.Part part = parts.get(seams[at]);
				emitQuad(buffer, place(base, part), part, seams[at + 1], steps, shownPlaced, window, seam);
			}
		}
		BuiltBuffer built = buffer.endNullable();
		if (built == null) {
			return;
		}
		BufferRenderer.drawWithGlobalProgram(built);
	}

	private record Tint(float red, float green, float blue, int alpha) {
		static Tint of(boolean next, boolean blocked, boolean refused, float wave) {
			if (refused) {
				return new Tint(1.0F, 0.42F, 0.38F, 150 + (int) (40 * wave));
			}
			if (next && blocked) {
				return new Tint(1.0F, 0.48F, 0.42F, 220);
			}
			if (next) {
				return new Tint(0.82F, 0.94F, 1.0F, 210 + (int) (30 * wave));
			}
			return new Tint(0.62F, 0.78F, 1.0F, 185);
		}
	}

	private record MeshKey(String faces, int window, int chunk) {
	}

	private record Planned(List<FarmFaces.Part> parts, GhostPlan plan) {
	}

	/**
	 * One baked chunk: its buffer (null when nothing in the window has faces) and the seams, pairs of part index and quad
	 * index, that are drawn each frame.
	 */
	private record Mesh(VertexBuffer buffer, int[] seams) {
		void close() {
			if (buffer != null) {
				buffer.close();
			}
		}
	}

	/** The matrix that places a part: the anchor's base, moved to the part and shrunk a hair so neighbors don't z-fight. */
	private static Matrix4f place(Matrix4f base, FarmFaces.Part part) {
		return SCRATCH.set(base).translate(part.pos().getX() + 0.01F, part.pos().getY() + 0.01F, part.pos().getZ() + 0.01F).scale(0.98F);
	}

	/** Adds every face of one part that the rule keeps; returns whether any was added. */
	private static boolean emit(BufferBuilder buffer, Matrix4f base, FarmFaces.Part part, List<FarmStep> steps, int shownPlaced, int topLayer, Tint tint) {
		Matrix4f matrix = place(base, part);
		boolean drawn = false;
		for (int quad = 0; quad < part.quads().size(); quad++) {
			drawn |= emitQuad(buffer, matrix, part, quad, steps, shownPlaced, topLayer, tint);
		}
		if (part.quads().isEmpty()) {
			ghostCube(buffer, matrix, part.fallback(), tint);
			drawn = true;
		}
		return drawn;
	}

	/** Adds one face unless the block covering it is built or within the shown layers. */
	private static boolean emitQuad(BufferBuilder buffer, Matrix4f matrix, FarmFaces.Part part, int quad, List<FarmStep> steps, int shownPlaced, int topLayer, Tint tint) {
		int cover = part.coveredBy()[quad];
		if (cover >= 0 && (cover < shownPlaced || steps.get(cover).y() <= topLayer)) {
			return false;
		}
		ghostQuad(buffer, matrix, part.quads().get(quad), part.tints()[quad], tint);
		return true;
	}

	private static void ghostQuad(BufferBuilder buffer, Matrix4f matrix, BakedQuad quad, int color, Tint tint) {
		int[] data = quad.getVertexData();
		int stride = data.length / 4;
		if (stride < 6) {
			return;
		}
		float tintRed = color == -1 ? 1.0F : ((color >> 16) & 255) / 255.0F;
		float tintGreen = color == -1 ? 1.0F : ((color >> 8) & 255) / 255.0F;
		float tintBlue = color == -1 ? 1.0F : (color & 255) / 255.0F;
		int red = Math.min(255, (int) (tintRed * tint.red() * 255.0F));
		int green = Math.min(255, (int) (tintGreen * tint.green() * 255.0F));
		int blue = Math.min(255, (int) (tintBlue * tint.blue() * 255.0F));
		for (int vertex = 0; vertex < 4; vertex++) {
			int offset = vertex * stride;
			float x = Float.intBitsToFloat(data[offset]);
			float y = Float.intBitsToFloat(data[offset + 1]);
			float z = Float.intBitsToFloat(data[offset + 2]);
			float u = Float.intBitsToFloat(data[offset + 4]);
			float v = Float.intBitsToFloat(data[offset + 5]);
			buffer.vertex(matrix, x, y, z).texture(u, v).color(red, green, blue, tint.alpha());
		}
	}

	/** Fallback for models without quads: a plain cube with the particle texture. */
	private static void ghostCube(BufferBuilder buffer, Matrix4f matrix, Sprite sprite, Tint tint) {
		if (sprite == null) {
			return;
		}
		int[] color = {Math.min(255, (int) (tint.red() * 255.0F)), Math.min(255, (int) (tint.green() * 255.0F)), Math.min(255, (int) (tint.blue() * 255.0F)), tint.alpha()};
		float[] uv = {sprite.getMinU(), sprite.getMinV(), sprite.getMaxU(), sprite.getMaxV()};
		face(buffer, matrix, new float[]{0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1}, uv, color);
		face(buffer, matrix, new float[]{0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0}, uv, color);
		face(buffer, matrix, new float[]{0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0}, uv, color);
		face(buffer, matrix, new float[]{0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1}, uv, color);
		face(buffer, matrix, new float[]{1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1}, uv, color);
		face(buffer, matrix, new float[]{0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0}, uv, color);
	}

	private static void face(BufferBuilder buffer, Matrix4f matrix, float[] corners, float[] uv, int[] color) {
		float u0 = uv[0];
		float v0 = uv[1];
		float u1 = uv[2];
		float v1 = uv[3];
		buffer.vertex(matrix, corners[0], corners[1], corners[2]).texture(u0, v1).color(color[0], color[1], color[2], color[3]);
		buffer.vertex(matrix, corners[3], corners[4], corners[5]).texture(u0, v0).color(color[0], color[1], color[2], color[3]);
		buffer.vertex(matrix, corners[6], corners[7], corners[8]).texture(u1, v0).color(color[0], color[1], color[2], color[3]);
		buffer.vertex(matrix, corners[9], corners[10], corners[11]).texture(u1, v1).color(color[0], color[1], color[2], color[3]);
	}
}
