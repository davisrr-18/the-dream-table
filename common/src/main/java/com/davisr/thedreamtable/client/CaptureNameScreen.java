package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.farm.capture.CaptureName;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** The name step of a capture: a text field with Save and Cancel. Cancel, Escape and a saved name all return to the game, with the capture mode still on. */
final class CaptureNameScreen extends Screen {
	private static final int FIELD_WIDTH = 200;
	private static final int BUTTON_WIDTH = 98;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int PROBLEM = 0xFFFF7777;

	private TextFieldWidget field;
	private ButtonWidget save;

	CaptureNameScreen() {
		super(Text.translatable("gui.the_dream_table.capture_name_title"));
	}

	@Override
	protected void init() {
		int left = (width - FIELD_WIDTH) / 2;
		int top = height / 2 - 6;
		field = new TextFieldWidget(textRenderer, left, top, FIELD_WIDTH, 18, Text.translatable("gui.the_dream_table.capture_name"));
		field.setMaxLength(CaptureName.MAX_LENGTH);
		field.setPlaceholder(Text.translatable("gui.the_dream_table.capture_name"));
		field.setText(CaptureMode.typed());
		field.setChangedListener(CaptureMode::remember);
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

	private void submit() {
		if (CaptureMode.save(field.getText())) {
			close();
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			submit();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
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
