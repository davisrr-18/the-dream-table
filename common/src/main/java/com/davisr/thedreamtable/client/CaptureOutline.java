package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.platform.RenderFrame;
import com.davisr.thedreamtable.platform.RenderStage;
import net.minecraft.util.math.Box;

/** The outline of the area a capture is selecting: amber while corner 2 follows the crosshair, green once fixed and usable, red when it can't be saved. */
public final class CaptureOutline {
	private static Box box;
	private static Status status = Status.AIMING;

	public enum Status {
		AIMING(1.0F, 0.75F, 0.2F),
		READY(0.3F, 1.0F, 0.4F),
		REFUSED(1.0F, 0.25F, 0.25F);

		private final float red;
		private final float green;
		private final float blue;

		Status(float red, float green, float blue) {
			this.red = red;
			this.green = green;
			this.blue = blue;
		}
	}

	private CaptureOutline() {
	}

	public static void register() {
		Platform.clientRendering().onWorldRender(RenderStage.AFTER_ENTITIES, CaptureOutline::render);
	}

	/** Sets the box drawn from now on; null draws nothing. */
	public static void show(Box shown, Status state) {
		box = shown;
		status = state;
	}

	public static void hide() {
		box = null;
	}

	private static void render(RenderFrame context) {
		Box shown = box;
		if (shown != null) {
			Status state = status;
			AreaOutlines.draw(context, outliner -> outliner.box(shown, state.red, state.green, state.blue, 1.0F));
		}
	}
}
