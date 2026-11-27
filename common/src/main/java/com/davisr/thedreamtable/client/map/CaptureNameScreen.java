package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.farm.capture.CaptureName;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The name step of a capture, opened over the capture map: a text field with Save and Cancel, and the cursor released. Cancel,
 * Escape and a saved name all go back to the map, which stays open (the mode is told the step is running, so it does not end
 * when its screen is replaced).
 */
final class CaptureNameScreen extends Screen {
	private static final int FIELD_WIDTH = 200;
	private static final int BUTTON_WIDTH = 98;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int PROBLEM = 0xFFFF7777;

	private final MapScreen map;
	private TextFieldWidget field;
	private ButtonWidget save;
	/** False while the Enter that opened the screen is still held. */
	private boolean armed;

	CaptureNameScreen(MapScreen map) {
		super(Text.translatable("gui.the_dream_table.capture_name_title"));
		this.map = map;
	}

	@Override
	protected void init() {
		// An Enter still down when the screen opens is the one that opened it, if any.
		armed = armed || !InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_ENTER)
				&& !InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_KP_ENTER);
		InputUtil.setCursorParameters(client.getWindow().getHandle(), InputUtil.GLFW_CURSOR_NORMAL, client.mouse.getX(), client.mouse.getY());
		int left = (width - FIELD_WIDTH) / 2;
		int top = height / 2 - 6;
		field = new TextFieldWidget(textRenderer, left, top, FIELD_WIDTH, 18, Text.translatable("gui.the_dream_table.capture_name"));
		field.setMaxLength(CaptureName.MAX_LENGTH);
		field.setPlaceholder(Text.translatable("gui.the_dream_table.capture_name"));
		field.setText(MapCapture.typed());
		field.setChangedListener(MapCapture::remember);
		save = ButtonWidget.builder(Text.translatable("gui.the_dream_table.capture_save"), button -> submit())
				.dimensions(left, top + 42, BUTTON_WIDTH, 20).build();
		addDrawableChild(field);
		addDrawableChild(save);
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.the_dream_table.capture_cancel"), button -> close())
				.dimensions(left + FIELD_WIDTH - BUTTON_WIDTH, top + 42, BUTTON_WIDTH, 20).build());
		setInitialFocus(field);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	/** Back to the map. A key still held (the Enter that saved) is remembered, so its repeats don't open this screen again. */
	@Override
	public void close() {
		MapMode.setNaming(false);
		MapCapture.nameScreenClosed(MapScreen.confirmDown(client));
		client.setScreen(map);
	}

	private void submit() {
		if (MapCapture.save(field.getText())) {
			close();
		}
	}

	/** Enter saves, but not while it is the same hold that opened this screen: its repeats would save the name typed last time. */
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			if (armed) {
				submit();
			}
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			armed = true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// 1.20.1's Screen.render draws no background of its own.
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		int left = (width - FIELD_WIDTH) / 2;
		int top = height / 2 - 6;
		String name = CaptureName.sanitize(field.getText());
		save.active = name != null;
		context.drawTextWithShadow(textRenderer, getTitle(), left, top - 14, TEXT);
		context.drawTextWithShadow(textRenderer, name == null ? Text.translatable("gui.the_dream_table.capture_name_empty")
				: Text.translatable("gui.the_dream_table.capture_saves_as", name + ".nbt"), left, top + 24, name == null ? PROBLEM : TEXT);
	}
}
