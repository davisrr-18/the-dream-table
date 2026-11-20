package com.davisr.thedreamtable.screen.ui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.input.AbstractInput;

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

	/**
	 * Vanilla routes a click to the first child under the pointer and counts it handled whether or not that child takes it,
	 * so a control that must not take the mouse (scrolled out, or the feedback field the box itself serves) must not be hovered.
	 */
	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return mouseInteractive && super.isMouseOver(mouseX, mouseY);
	}

	/** No pointing hand over a control that takes no mouse; over the feedback field it would hide the text cursor. */
	@Override
	protected void setCursor(DrawContext context) {
		if (mouseInteractive) {
			super.setCursor(context);
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		return mouseInteractive && super.mouseClicked(click, doubled);
	}

	@Override
	public void onPress(AbstractInput input) {
		if (spec.enabled()) {
			action.run();
		}
	}

	@Override
	protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
		// Painter owns the pixel-art visuals; this widget owns interaction and accessibility only.
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		builder.put(NarrationPart.TITLE, spec.narration());
		appendDefaultNarrations(builder);
	}
}
