package com.davisr.thedreamtable.platform;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;

/** What a world render listener may draw with. Any part may be null when the loader has none at that stage. */
public record RenderFrame(ClientWorld world, MatrixStack matrixStack, VertexConsumerProvider consumers, Camera camera) {
}
