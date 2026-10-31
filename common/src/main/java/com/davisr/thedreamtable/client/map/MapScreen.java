package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.client.DreamTableHologram;
import com.davisr.thedreamtable.client.DreamTableKeys;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.util.Window;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.EnumSet;
import java.util.List;

/**
 * The transparent screen of map mode. It keeps the movement keys held, turns the view while the right button is dragged,
 * and hands the pointer, clicks, scrolling and keys to {@link MapMode} and the hologram, so the world behind it is never
 * dimmed or blurred.
 */
public final class MapScreen extends Screen {
	/** A drag of one GUI pixel turns the view this many degrees at the game's default mouse sensitivity. */
	private static final double DEGREES_PER_UNIT = 0.25;
	private static final int MARGIN = 8;
	private static final int LINE = 10;
	private static final int HINT_COLOR = 0xFFFFFFFF;

	/** The held movement: forward and strafe (right is positive) follow the view, vertical is world Y. Each is -1, 0 or 1. */
	public record Movement(double forward, double strafe, double vertical) {
	}

	private enum Move {
		FORWARD,
		BACK,
		LEFT,
		RIGHT,
		UP,
		DOWN
	}

	/** Minecraft clears the key bindings' states while a screen is open, so the held keys are tracked here. */
	private final EnumSet<Move> held = EnumSet.noneOf(Move.class);

	public MapScreen() {
		super(Text.translatable("gui.the_dream_table.map_title"));
	}

	@Override
	protected void init() {
		Mouse mouse = client.mouse;
		Window window = client.getWindow();
		MapMode.pointer(mouse.getX() * width / window.getWidth(), mouse.getY() * height / window.getHeight(), width, height);
	}

	public Movement movement() {
		return new Movement(axis(Move.FORWARD, Move.BACK), axis(Move.RIGHT, Move.LEFT), axis(Move.UP, Move.DOWN));
	}

	private double axis(Move positive, Move negative) {
		return (held.contains(positive) ? 1 : 0) - (held.contains(negative) ? 1 : 0);
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
		held.clear();
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
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			// Dragging the mouse up gives a negative delta, which lifts the view, as the game's own look does.
			double scale = DEGREES_PER_UNIT * lookFactor();
			MapMode.look(deltaX * scale, deltaY * scale);
		}
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
		MapMode.scroll(verticalAmount);
		return true;
	}

	/** The game's mouse sensitivity as its own look scales it: 1 at the default of one half. */
	private double lookFactor() {
		double sensitivity = client.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
		return sensitivity * sensitivity * sensitivity * 8.0;
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
		Move move = moveFor(keyCode, scanCode);
		if (move != null) {
			held.add(move);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		Move move = moveFor(keyCode, scanCode);
		if (move != null) {
			held.remove(move);
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	/** The movement a key drives, through the player's own bindings so a rebound key works too; null for other keys. */
	private Move moveFor(int keyCode, int scanCode) {
		GameOptions options = client.options;
		if (options.forwardKey.matchesKey(keyCode, scanCode)) {
			return Move.FORWARD;
		}
		if (options.backKey.matchesKey(keyCode, scanCode)) {
			return Move.BACK;
		}
		if (options.leftKey.matchesKey(keyCode, scanCode)) {
			return Move.LEFT;
		}
		if (options.rightKey.matchesKey(keyCode, scanCode)) {
			return Move.RIGHT;
		}
		if (options.jumpKey.matchesKey(keyCode, scanCode)) {
			return Move.UP;
		}
		if (options.sneakKey.matchesKey(keyCode, scanCode)) {
			return Move.DOWN;
		}
		return null;
	}
}
