package com.davisr.thedreamtable.screen.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.PressableWidget;

/** Invisible vanilla widget that gives custom-drawn controls standard focus, keyboard, and narration behavior. */
public final class UiControl extends PressableWidget {
	private ControlSpec spec;
	private Runnable action;
	private boolean mouseInteractive = true;

	public UiControl(ControlSpec spec, Runnable action) {
		super(spec.bounds().x(), spec.bounds().y(), spec.bounds().width(), spec.bounds().height(), spec.narration());
		update(spec, action);
	}

	public ControlId id() {
		return spec.id();
	}

	public void update(ControlSpec spec, Runnable action) {
		this.spec = spec;
		this.action = action;
		setDimensionsAndPosition(spec.bounds().width(), spec.bounds().height(), spec.bounds().x(), spec.bounds().y());
		setMessage(spec.narration());
		active = spec.enabled();
		visible = true;
	}

	public void setMouseInteractive(boolean mouseInteractive) {
		this.mouseInteractive = mouseInteractive;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return mouseInteractive && super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void onPress() {
		if (spec.enabled()) {
			action.run();
		}
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		// Painter owns the pixel-art visuals; this widget owns interaction and accessibility only.
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		builder.put(NarrationPart.TITLE, spec.narration());
		appendDefaultNarrations(builder);
	}
}
