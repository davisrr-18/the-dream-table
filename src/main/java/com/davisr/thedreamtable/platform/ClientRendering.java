package com.davisr.thedreamtable.platform;

import net.minecraft.client.gui.DrawContext;

import java.util.function.Consumer;

/** Drawing hooks. Installed only on a client; listeners run in registration order. */
public interface ClientRendering {
	/** Draws in the world at the given stage of every frame. */
	void onWorldRender(RenderStage stage, Consumer<RenderFrame> listener);

	/** Draws on the in-game HUD every frame. */
	void onHud(Consumer<DrawContext> listener);
}
