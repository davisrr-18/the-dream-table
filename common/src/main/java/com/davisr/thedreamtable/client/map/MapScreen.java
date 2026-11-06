package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.client.DreamTableHologram;
import com.davisr.thedreamtable.client.DreamTableKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * The transparent screen of map mode. While it is open the cursor is grabbed, so the mouse turns the view as in the game and
 * the crosshair is the aim. It keeps the movement keys held, and hands clicks, scrolling and keys to {@link MapMode} and the
 * hologram, so the world behind it is never dimmed or blurred.
 */
public final class MapScreen extends Screen {
	/** The controls panel: its distance from the screen's bottom, inner padding, row height and the gaps between items. */
	private static final int MARGIN = 6;
	private static final int PADDING = 5;
	private static final int ROW = 14;
	private static final int ITEM_GAP = 12;
	private static final int KEY_PAD = 3;
	private static final int PANEL_COLOR = 0x99000000;
	private static final int KEY_FILL = 0xCC1E2A33;
	private static final int KEY_BORDER = 0xFF8CD8FF;
	private static final int KEY_TEXT = 0xFFFFFFFF;
	private static final int ACTION_TEXT = 0xFFD8D8D8;

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
	/** The controls panel as last laid out, kept until a binding or the screen width changes. */
	private String controlsKey = "";
	private List<Control> controls = List.of();
	private int[] widths = new int[0];

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
	public void renderBackground(DrawContext context) {
		// Left empty on purpose: the world stays undimmed and unblurred.
	}

	/**
	 * Runs every rendered frame while the screen is open, so the camera moves with the frame rate. The frame's length comes from
	 * the game's counter in ticks; the render's own delta is divided by the tick rate instead.
	 */
	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// A key let go while the window is in the background sends no release, so nothing counts as held without focus.
		if (!client.isWindowFocused()) {
			held.clear();
		}
		MapMode.frame(client, movement(), client.getLastFrameDuration());
		drawControls(context);
	}

	/**
	 * The controls panel at the bottom: a dark translucent band with one item per control, the key in a framed chip and what
	 * it does beside it. Items flow into centred rows and are never split.
	 */
	private void drawControls(DrawContext context) {
		layOutControls();
		List<List<Integer>> rows = MapControls.rows(widths, width - 2 * (MARGIN + PADDING), ITEM_GAP);
		int panelWidth = 0;
		for (List<Integer> row : rows) {
			panelWidth = Math.max(panelWidth, MapControls.rowWidth(row, widths, ITEM_GAP));
		}
		panelWidth += 2 * PADDING;
		int panelHeight = rows.size() * ROW + 2 * PADDING - (ROW - 12);
		int panelX = (width - panelWidth) / 2;
		int panelY = height - MARGIN - panelHeight;
		context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_COLOR);
		int y = panelY + PADDING;
		for (List<Integer> row : rows) {
			int x = (width - MapControls.rowWidth(row, widths, ITEM_GAP)) / 2;
			for (int index : row) {
				drawControl(context, controls.get(index), x, y);
				x += widths[index] + ITEM_GAP;
			}
			y += ROW;
		}
	}

	/** Builds the controls and their widths again only when a binding, the language or the screen width changed. */
	private void layOutControls() {
		StringBuilder key = new StringBuilder().append(width).append(client.options.language);
		for (KeyBinding binding : shownBindings()) {
			key.append('|').append(binding.getBoundKeyTranslationKey());
		}
		String now = key.toString();
		if (now.equals(controlsKey)) {
			return;
		}
		controlsKey = now;
		controls = controls();
		widths = new int[controls.size()];
		for (int i = 0; i < widths.length; i++) {
			widths[i] = itemWidth(controls.get(i));
		}
	}

	private List<KeyBinding> shownBindings() {
		GameOptions options = client.options;
		return List.of(options.forwardKey, options.leftKey, options.backKey, options.rightKey, options.jumpKey, options.sneakKey,
				options.sprintKey, DreamTableKeys.ROTATE, DreamTableKeys.MIRROR, DreamTableKeys.LAYERS, DreamTableKeys.HOLOGRAM, DreamTableKeys.MAP);
	}

	private int itemWidth(Control control) {
		return textRenderer.getWidth(control.key()) + 2 * KEY_PAD + 4 + textRenderer.getWidth(control.action());
	}

	private void drawControl(DrawContext context, Control control, int x, int y) {
		int keyWidth = textRenderer.getWidth(control.key()) + 2 * KEY_PAD;
		context.fill(x, y, x + keyWidth, y + 12, KEY_BORDER);
		context.fill(x + 1, y + 1, x + keyWidth - 1, y + 11, KEY_FILL);
		context.drawTextWithShadow(textRenderer, control.key(), x + KEY_PAD, y + 2, KEY_TEXT);
		context.drawTextWithShadow(textRenderer, control.action(), x + keyWidth + 4, y + 2, ACTION_TEXT);
	}

	/** One control of the panel: the key as the player has it bound, and what it does. */
	private record Control(Text key, Text action) {
	}

	/** The map's controls, from the player's own key bindings, so a rebound key shows as it is. */
	private List<Control> controls() {
		GameOptions options = client.options;
		Text move = Text.empty().append(options.forwardKey.getBoundKeyLocalizedText()).append(" ")
				.append(options.leftKey.getBoundKeyLocalizedText()).append(" ")
				.append(options.backKey.getBoundKeyLocalizedText()).append(" ")
				.append(options.rightKey.getBoundKeyLocalizedText());
		Text back = Text.empty().append(Text.translatable("key.keyboard.escape")).append(" / ").append(DreamTableKeys.MAP.getBoundKeyLocalizedText());
		return List.of(
				control(move, "move"),
				control(options.jumpKey.getBoundKeyLocalizedText(), "up"),
				control(options.sneakKey.getBoundKeyLocalizedText(), "down"),
				control(options.sprintKey.getBoundKeyLocalizedText(), "faster"),
				control(Text.translatable("key.mouse.left"), "place"),
				control(DreamTableKeys.ROTATE.getBoundKeyLocalizedText(), "turn"),
				control(DreamTableKeys.MIRROR.getBoundKeyLocalizedText(), "mirror"),
				control(DreamTableKeys.LAYERS.getBoundKeyLocalizedText(), "layers"),
				control(DreamTableKeys.HOLOGRAM.getBoundKeyLocalizedText(), "hologram"),
				control(back, "back"));
	}

	private static Control control(Text key, String action) {
		return new Control(key, Text.translatable("gui.the_dream_table.map_action." + action));
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
			return true;
		}
		// Other buttons drive whatever the player bound to them, as keys do.
		Predicate<KeyBinding> bound = binding -> binding.matchesMouse(button);
		if (!act(bound)) {
			Move move = moveFor(bound);
			if (move != null) {
				held.add(move);
			}
		}
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		Move move = moveFor(binding -> binding.matchesMouse(button));
		if (move != null) {
			held.remove(move);
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		MapMode.scroll(verticalAmount);
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		Predicate<KeyBinding> bound = binding -> binding.matchesKey(keyCode, scanCode);
		if (act(bound)) {
			return true;
		}
		Move move = moveFor(bound);
		if (move != null) {
			held.add(move);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/** Runs the map's own action bound to the pressed key or button; false when it drives none. */
	private boolean act(Predicate<KeyBinding> bound) {
		if (bound.test(DreamTableKeys.MAP)) {
			close();
		} else if (bound.test(DreamTableKeys.ROTATE)) {
			DreamTableHologram.rotate();
		} else if (bound.test(DreamTableKeys.MIRROR)) {
			DreamTableHologram.mirror();
		} else if (bound.test(DreamTableKeys.HOLOGRAM)) {
			DreamTableHologram.toggleGhosts();
		} else if (bound.test(DreamTableKeys.LAYERS)) {
			DreamTableHologram.toggleLayers();
		} else {
			return false;
		}
		return true;
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		Move move = moveFor(binding -> binding.matchesKey(keyCode, scanCode));
		if (move != null) {
			held.remove(move);
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	/** The movement a key or button drives, through the player's own bindings so a rebound one works too; null for others. */
	private Move moveFor(Predicate<KeyBinding> bound) {
		GameOptions options = client.options;
		if (bound.test(options.forwardKey)) {
			return Move.FORWARD;
		}
		if (bound.test(options.backKey)) {
			return Move.BACK;
		}
		if (bound.test(options.leftKey)) {
			return Move.LEFT;
		}
		if (bound.test(options.rightKey)) {
			return Move.RIGHT;
		}
		if (bound.test(options.jumpKey)) {
			return Move.UP;
		}
		if (bound.test(options.sneakKey)) {
			return Move.DOWN;
		}
		if (bound.test(options.sprintKey)) {
			return Move.FAST;
		}
		return null;
	}
}
