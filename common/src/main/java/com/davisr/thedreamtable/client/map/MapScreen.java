package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.client.DreamTableHologram;
import com.davisr.thedreamtable.client.DreamTableKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.EnumSet;
import java.util.List;

/**
 * The transparent screen of map mode. While it is open the cursor is grabbed, so the mouse turns the view as in the game and
 * the crosshair is the aim. It keeps the movement keys held, and hands clicks, scrolling and keys to {@link MapMode} and the
 * hologram, so the world behind it is never dimmed or blurred.
 */
public final class MapScreen extends Screen {
	private static final int MARGIN = 8;
	private static final int LINE = 10;
	private static final int HINT_COLOR = 0xFFFFFFFF;

	/**
	 * The held movement: forward and strafe (right is positive) follow the view, vertical is world Y, each is -1, 0 or 1.
	 * {@code fast} is the sprint key.
	 */
	public record Movement(double forward, double strafe, double vertical, boolean fast) {
	}

	private enum Move {
		FORWARD,
		BACK,
		LEFT,
		RIGHT,
		UP,
		DOWN,
		FAST
	}

	/** Minecraft clears the key bindings' states while a screen is open, so the held keys are tracked here. */
	private final EnumSet<Move> held = EnumSet.noneOf(Move.class);
	/** The last pointer position in scaled GUI pixels, once the grab has delivered one. */
	private double lastX;
	private double lastY;
	private boolean hasLast;

	public MapScreen() {
		super(Text.translatable("gui.the_dream_table.map_title"));
	}

	/**
	 * The cursor is unlocked when a screen is set, so grabbing it here comes after that. Vanilla's own lock would close the
	 * screen, so the grab is done directly and released again in {@link #removed()}.
	 */
	@Override
	protected void init() {
		Window window = client.getWindow();
		InputUtil.setCursorParameters(window.getHandle(), InputUtil.GLFW_CURSOR_DISABLED, client.mouse.getX(), client.mouse.getY());
		hasLast = false;
	}

	public Movement movement() {
		return new Movement(axis(Move.FORWARD, Move.BACK), axis(Move.RIGHT, Move.LEFT), axis(Move.UP, Move.DOWN), held.contains(Move.FAST));
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
		Window window = client.getWindow();
		InputUtil.setCursorParameters(window.getHandle(), InputUtil.GLFW_CURSOR_NORMAL, client.mouse.getX(), client.mouse.getY());
		MapMode.exit(client);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// Left empty on purpose: the world stays undimmed and unblurred.
	}

	/**
	 * Runs every rendered frame while the screen is open, so the camera moves with the frame rate. The frame's length comes from
	 * the game's counter in ticks; the render's own delta is divided by the tick rate instead.
	 */
	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		MapMode.frame(client, movement(), client.getRenderTickCounter().getLastFrameDuration());
		List<OrderedText> lines = textRenderer.wrapLines(Text.translatable("gui.the_dream_table.map_hint"), width - 2 * MARGIN);
		int y = height - MARGIN - lines.size() * LINE;
		for (OrderedText line : lines) {
			context.drawTextWithShadow(textRenderer, line, (width - textRenderer.getWidth(line)) / 2, y, HINT_COLOR);
			y += LINE;
		}
	}

	/**
	 * Turns the view by the distance the pointer moved since the last event, in raw pixels, as the game's own look does. The
	 * first event after the grab only sets the starting point.
	 */
	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		if (hasLast) {
			Window window = client.getWindow();
			double pixelsX = (mouseX - lastX) * window.getWidth() / window.getScaledWidth();
			double pixelsY = (mouseY - lastY) * window.getHeight() / window.getScaledHeight();
			if (MapLook.plausible(pixelsX) && MapLook.plausible(pixelsY)) {
				double factor = MapLook.factor(client.options.getMouseSensitivity().getValue());
				double pitchSign = client.options.getInvertYMouse().getValue() ? -1.0 : 1.0;
				MapMode.look(MapLook.degrees(pixelsX, factor), pitchSign * MapLook.degrees(pixelsY, factor));
			}
		}
		lastX = mouseX;
		lastY = mouseY;
		hasLast = true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// The view stays open after a click, so the placed farm can be judged and placed again elsewhere.
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			DreamTableHologram.mapPlace();
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		MapMode.scroll(verticalAmount);
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
		if (options.sprintKey.matchesKey(keyCode, scanCode)) {
			return Move.FAST;
		}
		return null;
	}
}
