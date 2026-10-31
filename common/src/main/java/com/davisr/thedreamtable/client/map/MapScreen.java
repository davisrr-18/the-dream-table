package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.client.DreamTableHologram;
import com.davisr.thedreamtable.client.DreamTableKeys;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The transparent screen of map mode. It draws only a hint line and hands the pointer, clicks, scrolling and keys to
 * {@link MapMode} and the hologram, so the world behind it is never dimmed or blurred.
 */
public final class MapScreen extends Screen {
	private static final int MARGIN = 8;
	private static final int LINE = 10;
	private static final int HINT_COLOR = 0xFFFFFFFF;

	public MapScreen() {
		super(Text.translatable("gui.the_dream_table.map_title"));
	}

	@Override
	protected void init() {
		Mouse mouse = client.mouse;
		Window window = client.getWindow();
		MapMode.pointer(mouse.getX() * width / window.getWidth(), mouse.getY() * height / window.getHeight(), width, height);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	@Override
	public void close() {
		MapMode.exit(client);
		super.close();
	}

	@Override
	public void removed() {
		MapMode.exit(client);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// Left empty on purpose: the world stays undimmed and unblurred.
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		List<OrderedText> lines = textRenderer.wrapLines(Text.translatable("gui.the_dream_table.map_hint"), width - 2 * MARGIN);
		int y = height - MARGIN - lines.size() * LINE;
		for (OrderedText line : lines) {
			context.drawTextWithShadow(textRenderer, line, (width - textRenderer.getWidth(line)) / 2, y, HINT_COLOR);
			y += LINE;
		}
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		MapMode.pointer(mouseX, mouseY, width, height);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		MapMode.pointer(mouseX, mouseY, width, height);
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		MapMode.pointer(mouseX, mouseY, width, height);
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && DreamTableHologram.mapPlace()) {
			close();
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		MapMode.zoom(verticalAmount);
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (DreamTableKeys.MAP.matchesKey(keyCode, scanCode)) {
			close();
			return true;
		}
		if (DreamTableKeys.ROTATE.matchesKey(keyCode, scanCode)) {
			DreamTableHologram.rotate();
			return true;
		}
		if (DreamTableKeys.MIRROR.matchesKey(keyCode, scanCode)) {
			DreamTableHologram.mirror();
			return true;
		}
		if (DreamTableKeys.LAYERS.matchesKey(keyCode, scanCode)) {
			DreamTableHologram.toggleLayers();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
}
