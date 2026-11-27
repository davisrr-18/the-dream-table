package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.client.map.MapMode;
import com.davisr.thedreamtable.farm.capture.AreaCapture;
import com.davisr.thedreamtable.farm.capture.CaptureAccess;
import com.davisr.thedreamtable.farm.capture.CaptureBox;
import com.davisr.thedreamtable.farm.capture.CaptureName;
import com.davisr.thedreamtable.farm.capture.CaptureProblem;
import com.davisr.thedreamtable.farm.capture.CaptureScan;
import com.davisr.thedreamtable.farm.capture.CaptureSelection;
import com.davisr.thedreamtable.farm.capture.CaptureStructure;
import com.davisr.thedreamtable.farm.capture.CaptureTables;
import com.davisr.thedreamtable.network.CaptureAreaPayload;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Capture in farm placement, for operators and the host of a single-player world. K turns it on while a farm is chosen at a
 * table (the hologram's placement mode); a right click (taken from the game: nothing is placed, opened or used by it) sets
 * corner 1 at the block aimed at, up to {@link #REACH} blocks away; the box then follows the crosshair until a second right click
 * fixes corner 2 at the aimed block; Up grows the box upward and Down downward (Shift reverses them); Enter opens
 * {@link CaptureNameScreen}. The table sent with the capture is the one the placement follows. The server checks everything again.
 */
public final class CaptureMode {
	/** How far the crosshair picks a corner, as far as farm aiming reaches. */
	private static final double REACH = 64.0;
	/** The fixed box must stay unchanged this many ticks before its blocks are read. */
	private static final long SETTLE_TICKS = 5;
	/** A box that had an unloaded chunk is read again after this many ticks. */
	private static final long RETRY_TICKS = 40;

	private static boolean active;
	private static ClientWorld world;
	private static final CaptureSelection SELECTION = new CaptureSelection();
	private static CaptureBox box;
	private static CaptureProblem problem;
	private static BlockPos table;
	/** Whether a screen was open when the previous tick started, so a key still held when one closes is not read as a new press. */
	private static boolean screenWasOpen;
	private static final ConfirmGuard CONFIRM = new ConfirmGuard();
	/** The use key's presses, so a click is recorded once whether the game's use event or the tick saw it, and a held key's repeats are not clicks. */
	private static final UsePress USE = new UsePress();
	private static String typed = "";
	private static int lastClickAge = -1;
	private static CaptureBox scanned;
	private static CaptureProblem scanProblem;
	private static int scanBlocks;
	private static long scannedAt;
	private static CaptureBox seen;
	private static long seenAt;
	private static boolean pending;

	private CaptureMode() {
	}

	public static void register() {
		// Registered before the hologram's click listener, so a capture click is never also a farm placement.
		Platform.clientEvents().onUseBlock((player, level, hand, hit) -> take(player, level, hand) ? ActionResult.FAIL : ActionResult.PASS);
		Platform.clientEvents().onUseItem((player, level, hand) -> take(player, level, hand) ? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand)));
		Platform.clientEvents().onTickEnd(CaptureMode::tick);
		Platform.clientEvents().onDisconnect(client -> stop());
		Platform.clientRendering().onHud(CaptureHud::render);
	}

	/** Whether this player may capture: the same rule the server applies, so the mode is never offered to anyone it would refuse. */
	static boolean allowed(MinecraftClient client) {
		return client.player != null
				&& CaptureAccess.allowed(client.player.hasPermissionLevel(CaptureAccess.OPERATOR_LEVEL), client.isIntegratedServerRunning());
	}

	public static boolean active() {
		return active;
	}

	/** The capture key: turns the mode on, only while a farm is chosen at a table (placement), or off. */
	public static void toggle(MinecraftClient client) {
		if (active) {
			stop();
		} else if (!allowed(client)) {
			TableNotices.problem(Text.translatable("gui.the_dream_table.capture_problem.not_allowed"));
		} else if (client.world == null || !DreamTableHologram.mapAvailable()) {
			TableNotices.problem(Text.translatable("gui.the_dream_table.capture_problem.not_placing"));
		} else {
			active = true;
			world = client.world;
			SELECTION.clear();
		}
	}

	private static void stop() {
		active = false;
		world = null;
		box = null;
		problem = null;
		table = null;
		scanned = null;
		seen = null;
		pending = false;
		SELECTION.clear();
		CaptureOutline.hide();
	}

	/**
	 * A use event while capturing: takes the game's own action away (true) and records the corner when it is a new press. Only the
	 * main hand records. The game fires this only for a block within its reach or an item in hand; an empty hand at air or at a
	 * block past that reach fires nothing (see {@link #tick}).
	 */
	private static boolean take(PlayerEntity player, World level, Hand hand) {
		if (!active || !level.isClient()) {
			return false;
		}
		if (hand == Hand.MAIN_HAND && USE.onUseEvent() && player.age != lastClickAge && MinecraftClient.getInstance().currentScreen == null) {
			record(player, MinecraftClient.getInstance());
		}
		return true;
	}

	/** Corner 1, or corner 2 once corner 1 is set: the block the crosshair hits within {@link #REACH}, or the block the player stands on. */
	private static void record(PlayerEntity player, MinecraftClient client) {
		lastClickAge = player.age;
		if (SELECTION.fixed()) {
			// A new box is a new structure: its name starts empty again.
			typed = "";
		}
		BlockPos aimed = aimedBlock(player);
		// Corner 1 falls back to the block under the feet; corner 2 with nothing aimed at is fixed where the preview shows it.
		if (aimed == null && (!SELECTION.started() || SELECTION.fixed())) {
			aimed = player.getBlockPos().down();
		}
		SELECTION.click(aimed);
		refresh(client);
	}

	/** The block the crosshair points at, or null when it points at nothing within reach. */
	private static BlockPos aimedBlock(PlayerEntity player) {
		HitResult hit = player.raycast(REACH, 1.0F, false);
		return hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null;
	}

	/** The presses of a key binding since the last tick, counted by the game as they happen (a tap shorter than a tick too). */
	private static int presses(KeyBinding key) {
		int count = 0;
		while (key.wasPressed()) {
			count++;
		}
		return count;
	}

	/** The key's real state, not the game's count of presses: a keyboard key through GLFW, anything else through the binding. */
	private static boolean physicallyDown(MinecraftClient client, KeyBinding key) {
		InputUtil.Key bound = InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey());
		return bound.getCategory() == InputUtil.Type.KEYSYM ? InputUtil.isKeyPressed(client.getWindow().getHandle(), bound.getCode()) : key.isPressed();
	}

	private static void tick(MinecraftClient client) {
		// Always counted, so presses made while the mode is off don't fire when it turns on. The game counts a binding's presses, the
		// OS key repeats of a held key included (so a held Up or Down repeats by itself), only while no screen is open.
		int upSteps = presses(DreamTableKeys.CAPTURE_UP);
		int downSteps = presses(DreamTableKeys.CAPTURE_DOWN);
		boolean screenOpen = client.currentScreen != null || screenWasOpen;
		screenWasOpen = client.currentScreen != null;
		boolean confirm = CONFIRM.accept(presses(DreamTableKeys.CAPTURE_CONFIRM), screenOpen, physicallyDown(client, DreamTableKeys.CAPTURE_CONFIRM));
		// The game reads the use key's presses itself earlier in the tick, so only its state is left to see here. A press that fired no
		// use event (empty hand, air or a block past the game's reach) shows as the key going down.
		boolean useWithoutEvent = USE.onTick(client.options.useKey.isPressed());
		if (!active) {
			return;
		}
		if (!allowed(client) || client.world != world || client.player == null || !DreamTableHologram.mapAvailable() || MapMode.active()) {
			// Leaving placement (another table, a build started, the map opened) leaves capture and returns to normal placement.
			stop();
			return;
		}
		if (client.currentScreen instanceof GameMenuScreen) {
			// Escape ends the capture instead of pausing.
			stop();
			client.setScreen(null);
			return;
		}
		if (client.currentScreen != null) {
			return;
		}
		if (useWithoutEvent && client.player.age != lastClickAge) {
			record(client.player, client);
		}
		if (SELECTION.started() && !SELECTION.fixed()) {
			SELECTION.follow(aimedBlock(client.player));
		}
		boolean shift = Screen.hasShiftDown();
		for (int step = 0; step < upSteps; step++) {
			SELECTION.moveTop(shift ? -1 : 1);
		}
		for (int step = 0; step < downSteps; step++) {
			SELECTION.moveBottom(shift ? 1 : -1);
		}
		refresh(client);
		if (confirm && SELECTION.fixed() && box != null && problem == null && !pending && table != null) {
			client.setScreen(new CaptureNameScreen());
		}
	}

	/** Works out the box, what is wrong with it for the table placement follows, and the outline. */
	private static void refresh(MinecraftClient client) {
		box = SELECTION.box();
		problem = null;
		pending = false;
		table = DreamTableHologram.trackedTable();
		ClientPlayerEntity player = client.player;
		if (player == null || world == null) {
			CaptureOutline.hide();
			return;
		}
		if (box == null) {
			CaptureOutline.hide();
			return;
		}
		problem = box.sizeProblem(world.getBottomY(), world.getTopY());
		if (problem == null && table == null) {
			problem = CaptureProblem.of(CaptureProblem.Kind.NO_TABLE);
		}
		if (problem == null && !CaptureTables.usable(player.getPos(), table, box)) {
			problem = CaptureProblem.of(CaptureProblem.Kind.TOO_FAR);
		}
		if (problem == null && SELECTION.fixed()) {
			problem = scan();
		}
		CaptureOutline.show(box.outline(), problem != null ? CaptureOutline.Status.REFUSED
				: SELECTION.fixed() && !pending ? CaptureOutline.Status.READY : CaptureOutline.Status.AIMING);
	}

	/**
	 * Reads the fixed box once it has stayed the same for {@link #SETTLE_TICKS} (so holding a key to move a big box reads nothing in
	 * between), and again only to retry a box that had an unloaded chunk. The importer's verdict is asked on the result, as the
	 * server will ask it. While the box is still moving the answer is "none yet" and {@link #pending} is set.
	 */
	private static CaptureProblem scan() {
		long now = world.getTime();
		if (!box.equals(seen) || now < seenAt) {
			seen = box;
			seenAt = now;
		}
		boolean changed = !box.equals(scanned);
		if (changed && now - seenAt < SETTLE_TICKS) {
			pending = true;
			return null;
		}
		boolean retry = !changed && scanProblem != null && scanProblem.kind() == CaptureProblem.Kind.UNLOADED && now - scannedAt >= RETRY_TICKS;
		if (changed || retry) {
			// The client's chunk manager answers null for a chunk it does not hold, unlike ClientWorld.isChunkLoaded, which always says yes.
			CaptureScan.Result result = CaptureScan.read(box, (chunkX, chunkZ) -> {
				WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
				return chunk == null ? null : chunk::getBlockState;
			});
			scanned = box;
			scanProblem = result.problem() != null ? result.problem()
					: AreaCapture.importProblem(CaptureStructure.toNbt(box, result.blocks(), 0));
			scanBlocks = result.blocks().size();
			scannedAt = now;
		}
		return scanProblem;
	}

	/** Enter in the name screen: sends the capture. The box and the name stay, so a refused save can be renamed or adjusted; the next click starts a new box. */
	static boolean save(String name) {
		if (CaptureName.sanitize(name) == null || table == null || box == null || problem != null || pending || !SELECTION.fixed()) {
			return false;
		}
		typed = name;
		Platform.clientNetwork().send(new CaptureAreaPayload(table, box.min(), box.max(), name));
		return true;
	}

	static String typed() {
		return typed;
	}

	static void remember(String name) {
		typed = name;
	}

	static CaptureSelection selection() {
		return SELECTION;
	}

	static CaptureBox box() {
		return box;
	}

	static CaptureProblem problem() {
		return problem;
	}

	static boolean pending() {
		return pending;
	}

	static int blocks() {
		return scanBlocks;
	}
}
