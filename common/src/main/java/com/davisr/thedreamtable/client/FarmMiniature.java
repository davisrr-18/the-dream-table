package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A small turning model of the local player's chosen farm floating above the table, with their material swaps.
 * It draws the visible faces from {@link FarmFaces}, worked out once per farm, and only within {@link #RENDER_DISTANCE}
 * blocks, since a large farm or schematic can have thousands of blocks.
 */
public final class FarmMiniature implements BlockEntityRenderer<DreamTableBlockEntity> {
	private static final int RENDER_DISTANCE = 32;
	private static final float MAX_WIDTH = 1.4F;
	private static final float MAX_HEIGHT = 1.8F;
	private static final float LIFT = 1.5F;
	private static final float DEGREES_PER_TICK = 0.75F;
	private static final Map<String, Fit> FITS = new HashMap<>();

	private final BlockRenderManager blocks;

	public FarmMiniature(BlockEntityRendererFactory.Context context) {
		this.blocks = context.getRenderManager();
	}

	/** How to center and shrink a farm to fit above the table. */
	private record Fit(long signature, float centerX, float bottom, float centerZ, float scale) {
	}

	@Override
	public void render(DreamTableBlockEntity table, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
		PlayerEntity player = MinecraftClient.getInstance().player;
		// A broken table can stay in the renderer's list of unculled block entities: when its chunk section is left with only
		// air, the game rebuilds the section without updating that list, so the model would float there until the chunk unloads.
		if (player == null || table.getWorld() == null || table.isRemoved()) {
			return;
		}
		TableSession session = table.session(player);
		FarmTemplate template = session.template();
		if (template == null || template.steps().isEmpty()) {
			return;
		}
		List<FarmFaces.Part> parts = FarmFaces.outside(template, session.swapKey(), session::swapState);
		Fit fit = fit(template);
		float time = table.getWorld().getTime() + tickDelta;
		matrices.push();
		matrices.translate(0.5F, LIFT + 0.05F * (float) Math.sin(time * 0.05F), 0.5F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * DEGREES_PER_TICK % 360.0F));
		matrices.scale(fit.scale(), fit.scale(), fit.scale());
		matrices.translate(-fit.centerX(), -fit.bottom(), -fit.centerZ());
		for (FarmFaces.Part part : parts) {
			matrices.push();
			matrices.translate(part.pos().getX(), part.pos().getY(), part.pos().getZ());
			if (part.animated()) {
				blocks.renderBlockAsEntity(part.state(), matrices, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
			} else {
				draw(part, matrices.peek(), consumers.getBuffer(RenderLayers.getEntityBlockLayer(part.state(), false)));
			}
			matrices.pop();
		}
		matrices.pop();
	}

	private static void draw(FarmFaces.Part part, MatrixStack.Entry entry, VertexConsumer buffer) {
		for (int index = 0; index < part.quads().size(); index++) {
			int tint = part.tints()[index];
			float red = tint == -1 ? 1.0F : ((tint >> 16) & 255) / 255.0F;
			float green = tint == -1 ? 1.0F : ((tint >> 8) & 255) / 255.0F;
			float blue = tint == -1 ? 1.0F : (tint & 255) / 255.0F;
			buffer.quad(entry, part.quads().get(index), red, green, blue, 1.0F, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
		}
	}

	private static Fit fit(FarmTemplate template) {
		Fit cached = FITS.get(template.id());
		long signature = template.signature();
		if (cached != null && cached.signature() == signature) {
			return cached;
		}
		int[] low = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
		int[] high = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (FarmStep step : template.steps()) {
			int[] at = {step.x(), step.y(), step.z()};
			for (int axis = 0; axis < 3; axis++) {
				low[axis] = Math.min(low[axis], at[axis]);
				high[axis] = Math.max(high[axis], at[axis]);
			}
		}
		float width = Math.max(high[0] - low[0], high[2] - low[2]) + 1;
		float height = high[1] - low[1] + 1;
		Fit fit = new Fit(signature, (low[0] + high[0] + 1) / 2.0F, low[1], (low[2] + high[2] + 1) / 2.0F, Math.min(MAX_WIDTH / width, MAX_HEIGHT / height));
		FITS.put(template.id(), fit);
		return fit;
	}

	@Override
	public boolean rendersOutsideBoundingBox(DreamTableBlockEntity table) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return RENDER_DISTANCE;
	}
}
