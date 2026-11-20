package com.davisr.thedreamtable.client.hologram;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ScissorState;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * The hologram's render pipelines and the one way it draws with them. 1.21.11 has no global GL state: each draw is a render pass
 * on the main framebuffer with a pipeline that fixes blending, depth and culling, and the per-draw tint and matrix travel in the
 * {@code DynamicTransforms} uniform ({@code ColorModulator} multiplies every fragment, so a baked buffer is tinted and pulsed without
 * a rebuild). The game's shaders {@code core/position_color} and {@code core/position_tex_color} are reused; the pipelines are
 * compiled by the game on first use, and own no GPU memory.
 * <p>
 * None of these draws lines: the frame, brackets and beam are quads (see {@link PlacementOutline}).
 */
final class GhostDraw {
	/** Opaque quads that write depth: the frame ribbons and the brackets. */
	static final RenderPipeline SOLID = pipeline("ghost_solid", false, true);
	/** Blended quads that do not write depth: the floor and the beam. */
	static final RenderPipeline TRANSLUCENT = pipeline("ghost_translucent", true, true);
	/** Opaque quads drawn over everything, depth test off: the map's edge brackets. */
	static final RenderPipeline OVER_TERRAIN = pipeline("ghost_over_terrain", false, false);
	/** The ghost blocks: block-atlas quads, blended, writing depth, back faces culled, as the old shader state was. */
	static final RenderPipeline BLOCKS = RenderPipeline.builder(TRANSFORMS_SNIPPET())
			.withLocation(Identifier.of(TheDreamTableMod.MOD_ID, "pipeline/ghost_blocks"))
			.withVertexShader("core/position_tex_color")
			.withFragmentShader("core/position_tex_color")
			.withSampler("Sampler0")
			.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
			.withBlend(BlendFunction.TRANSLUCENT)
			.build();
	static final Vector4fc WHITE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);

	private GhostDraw() {
	}

	private static RenderPipeline.Snippet TRANSFORMS_SNIPPET() {
		return RenderPipeline.builder()
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.buildSnippet();
	}

	/** Position-colour quads, never culled. Blended draws do not write depth; without the depth test nothing does. */
	private static RenderPipeline pipeline(String name, boolean blend, boolean depthTest) {
		RenderPipeline.Builder builder = RenderPipeline.builder(TRANSFORMS_SNIPPET())
				.withLocation(Identifier.of(TheDreamTableMod.MOD_ID, "pipeline/" + name))
				.withVertexShader("core/position_color")
				.withFragmentShader("core/position_color")
				.withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
				.withCull(false);
		if (blend) {
			builder.withBlend(BlendFunction.TRANSLUCENT).withDepthWrite(false);
		}
		if (!depthTest) {
			builder.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false);
		}
		return builder.build();
	}

	/** The game's camera rotation, as the world render has set it for this frame; a copy, so callers may multiply into it. */
	static Matrix4f view() {
		return new Matrix4f(RenderSystem.getModelViewMatrix());
	}

	/** Draws one finished immediate buffer with the pipeline and closes it. */
	static void draw(RenderPipeline pipeline, BuiltBuffer built, Matrix4fc modelView, Vector4fc tint) {
		try (built; RenderPass pass = begin(pipeline, modelView, tint)) {
			BuiltBuffer.DrawParameters parameters = built.getDrawParameters();
			GpuBuffer vertices = pipeline.getVertexFormat().uploadImmediateVertexBuffer(built.getBuffer());
			RenderSystem.ShapeIndexBuffer shape = RenderSystem.getSequentialBuffer(parameters.mode());
			pass.setVertexBuffer(0, vertices);
			pass.setIndexBuffer(shape.getIndexBuffer(parameters.indexCount()), shape.getIndexType());
			pass.drawIndexed(0, 0, parameters.indexCount(), 1);
		}
	}

	/** Uploads a finished buffer to a GPU buffer that is kept and drawn many times, and closes the built buffer. */
	static GpuBuffer upload(BuiltBuffer built) {
		try (built) {
			return RenderSystem.getDevice().createBuffer(() -> "The Dream Table ghost chunk", GpuBuffer.USAGE_VERTEX, built.getBuffer());
		}
	}

	/**
	 * The shared quad index buffer, large enough for {@code indexCount} indices. Ask once with the largest count before a pass:
	 * the buffer is replaced when it has to grow, so it must not change while the pass is recording.
	 */
	static GpuBuffer quadIndices(int indexCount) {
		return RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS).getIndexBuffer(indexCount);
	}

	/** Draws one kept buffer of quads inside a pass started by {@link #begin}, with the indices from {@link #quadIndices}. */
	static void drawKept(RenderPass pass, GpuBuffer vertices, GpuBuffer indices, int indexCount) {
		pass.setVertexBuffer(0, vertices);
		pass.setIndexBuffer(indices, RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS).getIndexType());
		pass.drawIndexed(0, 0, indexCount, 1);
	}

	/**
	 * Starts a pass on the main framebuffer with the pipeline, the model-view matrix and the tint, and the block atlas bound for the
	 * textured pipeline. The caller draws and closes it. The target and the scissor are chosen as the game's own immediate draws do.
	 */
	static RenderPass begin(RenderPipeline pipeline, Matrix4fc modelView, Vector4fc tint) {
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().write(modelView, tint, new Vector3f(), new Matrix4f());
		Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
		GpuTextureView color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : framebuffer.getColorAttachmentView();
		GpuTextureView depth = framebuffer.useDepthAttachment
				? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : framebuffer.getDepthAttachmentView())
				: null;
		RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
				.createRenderPass(() -> "The Dream Table hologram", color, OptionalInt.empty(), depth, OptionalDouble.empty());
		pass.setPipeline(pipeline);
		ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
		if (scissor.isEnabled()) {
			pass.enableScissor(scissor.getX(), scissor.getY(), scissor.getWidth(), scissor.getHeight());
		}
		RenderSystem.bindDefaultUniforms(pass);
		pass.setUniform("DynamicTransforms", transforms);
		if (pipeline == BLOCKS) {
			AbstractTexture atlas = MinecraftClient.getInstance().getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
			pass.bindTexture("Sampler0", atlas.getGlTextureView(), atlas.getSampler());
		}
		return pass;
	}
}
