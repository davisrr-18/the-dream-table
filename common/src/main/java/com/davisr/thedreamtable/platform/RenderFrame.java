package com.davisr.thedreamtable.platform;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;

/**
 * What a world render listener may draw with. Any part may be null when the loader has none at that stage. {@code matrixStack}
 * starts as an identity stack in camera-relative space (the camera rotation is in the game's model-view matrix, not in the
 * stack), as the world render's own stack does; listeners translate by the negated camera position themselves. {@code camera}
 * is the game's camera for the frame (read it from {@code gameRenderer.getCamera()} when the loader gives none).
 */
public record RenderFrame(ClientWorld world, MatrixStack matrixStack, VertexConsumerProvider consumers, Camera camera) {
}
