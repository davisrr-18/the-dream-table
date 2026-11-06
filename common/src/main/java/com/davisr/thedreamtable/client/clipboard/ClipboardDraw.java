package com.davisr.thedreamtable.client.clipboard;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BufferBuilder.BuiltBuffer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

import static com.davisr.thedreamtable.client.clipboard.ClipboardLayout.*;

/** Flat colored quads, text and item models drawn in the board's own space. */
public final class ClipboardDraw {
	private static final int INK = 0xFF3A2918;
	private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

	private ClipboardDraw() {
	}

	/** 1.20.1 has one shared tessellator buffer, begun here; each pass is drawn before the next one begins. */
	static BufferBuilder begin(VertexFormat.DrawMode mode, VertexFormat format) {
		BufferBuilder buffer = Tessellator.getInstance().getBuffer();
		buffer.begin(mode, format);
		return buffer;
	}

	public static BufferBuilder quads() {
		return begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
	}

	public static BufferBuilder triangles() {
		return begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
	}

	public static void flat(BufferBuilder buffer, MatrixStack matrices, float x0, float y0, float x1, float y1, float z, float r, float g, float b) {
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		buffer.vertex(matrix, x0, y0, z).color(r, g, b, 1.0F).next();
		buffer.vertex(matrix, x0, y1, z).color(r, g, b, 1.0F).next();
		buffer.vertex(matrix, x1, y1, z).color(r, g, b, 1.0F).next();
		buffer.vertex(matrix, x1, y0, z).color(r, g, b, 1.0F).next();
	}

	public static void flush(BufferBuilder buffer) {
		BuiltBuffer built = buffer.endNullable();
		if (built == null) {
			return;
		}
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		BufferRenderer.drawWithGlobalProgram(built);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	public static void board(MatrixStack matrices) {
		BufferBuilder buffer = quads();
		flat(buffer, matrices, -HALF_W - 0.08F, -HALF_H - 0.06F, HALF_W + 0.08F, HALF_H + 0.1F, -0.02F, 0.49F, 0.33F, 0.16F);
		flat(buffer, matrices, -HALF_W, -HALF_H, HALF_W, HALF_H - 0.02F, 0.0F, 0.93F, 0.86F, 0.68F);
		flat(buffer, matrices, -0.16F, HALF_H - 0.1F, 0.16F, HALF_H + 0.09F, 0.012F, 0.35F, 0.37F, 0.40F);
		flat(buffer, matrices, -0.1F, HALF_H - 0.02F, 0.1F, HALF_H + 0.06F, 0.02F, 0.75F, 0.76F, 0.78F);
		flush(buffer);
	}

	public static void progress(BufferBuilder buffer, MatrixStack matrices, float top, float amount, float red, float green, float blue) {
		float bottom = top - BAR_H;
		flat(buffer, matrices, LEFT - 0.012F, bottom - 0.012F, RIGHT + 0.012F, top + 0.012F, 0.012F, 0.33F, 0.22F, 0.12F);
		flat(buffer, matrices, LEFT, bottom, RIGHT, top, 0.016F, 0.42F, 0.31F, 0.20F);
		float width = (RIGHT - LEFT) * Math.min(1.0F, Math.max(0.0F, amount));
		if (width > 0.004F) {
			flat(buffer, matrices, LEFT, bottom, LEFT + width, top, 0.02F, red, green, blue);
		}
	}

	public static void plate(BufferBuilder buffer, MatrixStack matrices, float x0, float y0, float x1, float y1, boolean enabled, boolean hover) {
		float[] color = !enabled ? new float[]{0.68F, 0.60F, 0.48F} : hover ? new float[]{0.97F, 0.84F, 0.52F} : new float[]{0.80F, 0.64F, 0.38F};
		flat(buffer, matrices, x0 - 0.014F, y0 - 0.014F, x1 + 0.014F, y1 + 0.014F, 0.014F, 0.34F, 0.22F, 0.11F);
		float inset = hover && enabled ? 0.012F : 0.0F;
		flat(buffer, matrices, x0 + inset, y0 + inset, x1 - inset, y1 - inset, 0.02F, color[0], color[1], color[2]);
		if (enabled) {
			flat(buffer, matrices, x0 + 0.02F, y1 - 0.028F, x1 - 0.02F, y1 - 0.012F, 0.026F, hover ? 1.0F : 0.90F, hover ? 0.92F : 0.76F, hover ? 0.64F : 0.50F);
		}
	}

	public static void arrow(BufferBuilder buffer, MatrixStack matrices, float x0, float y0, float x1, float y1, boolean right, boolean enabled) {
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		float centerX = (x0 + x1) * 0.5F;
		float centerY = (y0 + y1) * 0.5F;
		float direction = right ? 1.0F : -1.0F;
		float red = enabled ? 0.27F : 0.52F;
		float green = enabled ? 0.16F : 0.44F;
		float blue = enabled ? 0.07F : 0.32F;
		float z = 0.032F;
		buffer.vertex(matrix, centerX + direction * 0.075F, centerY, z).color(red, green, blue, 1.0F).next();
		buffer.vertex(matrix, centerX - direction * 0.045F, centerY + 0.055F, z).color(red, green, blue, 1.0F).next();
		buffer.vertex(matrix, centerX - direction * 0.045F, centerY - 0.055F, z).color(red, green, blue, 1.0F).next();
	}

	public static void label(MatrixStack matrices, TextRenderer text, float x, float y, Text content, float scale) {
		VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
		matrices.push();
		matrices.translate(x, y, 0.04F);
		matrices.scale(scale, -scale, scale);
		text.draw(content, 0, 0, INK, false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL, 0, LIGHT);
		matrices.pop();
		consumers.draw();
	}

	public static void labelRight(MatrixStack matrices, TextRenderer text, float right, float y, Text content, float scale) {
		label(matrices, text, right - text.getWidth(content) * scale, y, content, scale);
	}

	public static void labelCenter(MatrixStack matrices, TextRenderer text, float center, float y, Text content, float scale) {
		label(matrices, text, center - text.getWidth(content) * scale * 0.5F, y, content, scale);
	}

	/** Draws the item's own model facing forward, filling a square of the given size. */
	public static void item(MatrixStack matrices, ItemStack stack, float x, float y, float size) {
		if (stack.isEmpty()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		BakedModel model = client.getItemRenderer().getModel(stack, client.world, null, 0);
		VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
		matrices.push();
		matrices.translate(x + size * 0.5F, y + size * 0.5F, size * 0.5F + 0.02F);
		matrices.scale(size, size, size);
		DiffuseLighting.disableGuiDepthLighting();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		client.getItemRenderer().renderItem(stack, ModelTransformationMode.NONE, false, matrices, consumers, LIGHT, OverlayTexture.DEFAULT_UV, model);
		consumers.draw();
		RenderSystem.disableBlend();
		matrices.pop();
	}
}
