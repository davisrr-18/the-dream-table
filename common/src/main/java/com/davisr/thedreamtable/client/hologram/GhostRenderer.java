package com.davisr.thedreamtable.client.hologram;

import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.client.FarmFaces;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.mojang.blaze3d.systems.RenderSystem;
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

import java.util.List;

/**
 * Draws the translucent blocks still to be built and the yellow aim marker. The next block glows, or turns red when it is blocked.
 * The whole farm turns red when the server says it can't be built where it was placed.
 */
public final class GhostRenderer {
	private GhostRenderer() {
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
		List<FarmFaces.Part> parts = table == null
				? FarmFaces.of(template, "", state -> state, facing, mirrored)
				: FarmFaces.of(template, table.swapKey(), table::swapState, facing, mirrored);
		List<FarmStep> steps = template.steps();
		BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		boolean any = false;
		for (FarmFaces.Part part : parts) {
			int index = part.step();
			if (index < shownPlaced || steps.get(index).y() > topLayer) {
				continue;
			}
			Tint tint = Tint.of(index == shownPlaced, blocked, refused, wave);
			matrices.push();
			matrices.translate(anchor.getX() + part.pos().getX() + 0.01, anchor.getY() + part.pos().getY() + 0.01, anchor.getZ() + part.pos().getZ() + 0.01);
			matrices.scale(0.98F, 0.98F, 0.98F);
			boolean drawn = false;
			for (int quad = 0; quad < part.quads().size(); quad++) {
				int cover = part.coveredBy()[quad];
				if (cover >= 0 && (cover < shownPlaced || steps.get(cover).y() <= topLayer)) {
					continue;
				}
				ghostQuad(buffer, matrices, part.quads().get(quad), part.tints()[quad], tint);
				drawn = true;
			}
			if (part.quads().isEmpty()) {
				ghostCube(buffer, matrices, part.fallback(), tint);
				drawn = true;
			}
			matrices.pop();
			any |= drawn;
		}
		BuiltBuffer built = buffer.endNullable();
		if (!any || built == null) {
			return;
		}
		RenderSystem.setShaderTexture(0, PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
		RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		BufferRenderer.drawWithGlobalProgram(built);
		// The world render after this stage must not inherit blending.
		RenderSystem.disableBlend();
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

	private static void ghostQuad(BufferBuilder buffer, MatrixStack matrices, BakedQuad quad, int color, Tint tint) {
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
		Matrix4f matrix = matrices.peek().getPositionMatrix();
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
	private static void ghostCube(BufferBuilder buffer, MatrixStack matrices, Sprite sprite, Tint tint) {
		if (sprite == null) {
			return;
		}
		int[] color = {Math.min(255, (int) (tint.red() * 255.0F)), Math.min(255, (int) (tint.green() * 255.0F)), Math.min(255, (int) (tint.blue() * 255.0F)), tint.alpha()};
		float[] uv = {sprite.getMinU(), sprite.getMinV(), sprite.getMaxU(), sprite.getMaxV()};
		Matrix4f matrix = matrices.peek().getPositionMatrix();
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
