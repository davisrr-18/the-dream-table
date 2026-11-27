package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.block.DreamTableBlock;
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
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Capture in normal play, for operators and the host of a single-player world. K turns it on; a right click (taken from the
 * game: nothing is placed, opened or used by it) sets corner 1 at the block aimed at, up to {@link #REACH} blocks away; corner 2
 * then follows the block the player stands on until a second right click fixes it; the arrow keys and Page Up and Page Down move the top and
 * bottom; Enter opens {@link CaptureNameScreen}. The server checks everything again.
 */
public final class CaptureMode {
	/** How far the crosshair picks corner 1, as far as farm aiming reaches. */
	private static final double REACH = 64.0;
	/** The fixed box must stay unchanged this many ticks before its blocks are read. */
	private static final long SETTLE_TICKS = 5;
	/** A box that had an unloaded chunk is read again after this many ticks. */
	private static final long RETRY_TICKS = 40;
	private static final int TABLE_REFRESH_TICKS = 20;
	/** A held arrow or page key repeats every second tick after this many ticks. */
	private static final int REPEAT_DELAY = 10;
	/** Up, Down, Page Up, Page Down, Enter and keypad Enter: none is a default binding in 1.21.1 (checked in the options' key list), so they do nothing else in play. */
	private static final int[] KEYS = {GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER};
	private static final int[] HELD = new int[KEYS.length];

	private static boolean active;
	private static ClientWorld world;
	private static final CaptureSelection SELECTION = new CaptureSelection();
	private static CaptureBox box;
	private static CaptureProblem problem;
	private static BlockPos table;
	private static List<BlockPos> tables = List.of();
	private static long tablesAt;
	/** False until the first look for tables: a start time like Long.MIN_VALUE would overflow the elapsed-time subtraction. */
	private static boolean tablesScanned;
	/** Whether a screen was open when the previous tick started. */
	private static boolean screenWasOpen;
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

	/** The capture key: turns the mode on or off. */
	public static void toggle(MinecraftClient client) {
		if (active) {
			stop();
		} else if (!allowed(client)) {
			TableNotices.problem(Text.translatable("gui.the_dream_table.capture_problem.not_allowed"));
		} else if (client.world != null) {
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
		tables = List.of();
		tablesScanned = false;
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

	/** Corner 1 at the block the crosshair hits within {@link #REACH}, or corner 2 at the block the player stands on. */
	private static void record(PlayerEntity player, MinecraftClient client) {
		lastClickAge = player.age;
		if (SELECTION.fixed()) {
			// A new box is a new structure: its name starts empty again.
			typed = "";
		}
		HitResult hit = player.raycast(REACH, 1.0F, false);
		BlockPos aimed = hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : standingOn(player);
		SELECTION.click(aimed, standingOn(player));
		refresh(client);
	}

	/** The block under the player's feet: corner 2 follows it, so both corners are on the ground layer and the top is raised with the keys. */
	private static BlockPos standingOn(PlayerEntity player) {
		return player.getBlockPos().down();
	}

	private static void tick(MinecraftClient client) {
		boolean afterScreen = screenWasOpen;
		screenWasOpen = client.currentScreen != null;
		// The game reads the use key's presses itself earlier in the tick, so only its state is left to see here. A press that fired no
		// use event (empty hand, air or a block past the game's reach) shows as the key going down.
		boolean useWithoutEvent = USE.onTick(client.options.useKey.isPressed());
		boolean[] fire = new boolean[KEYS.length];
		for (int index = 0; index < KEYS.length; index++) {
			// Counted while a screen is open too, so a key held from a screen is not read as a new press.
			HELD[index] = KeyPresses.held(HELD[index], InputUtil.isKeyPressed(client.getWindow().getHandle(), KEYS[index]));
			fire[index] = KeyPresses.acts(HELD[index], index < 4, REPEAT_DELAY, afterScreen);
		}
		if (!active) {
			return;
		}
		if (!allowed(client) || client.world != world || client.player == null) {
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
		SELECTION.follow(standingOn(client.player));
		if (fire[0]) {
			SELECTION.moveTop(1);
		}
		if (fire[1]) {
			SELECTION.moveTop(-1);
		}
		if (fire[2]) {
			SELECTION.moveBottom(1);
		}
		if (fire[3]) {
			SELECTION.moveBottom(-1);
		}
		refresh(client);
		if ((fire[4] || fire[5]) && SELECTION.fixed() && box != null && problem == null && !pending && table != null) {
			client.setScreen(new CaptureNameScreen());
		}
	}

	/** Works out the box, the table it would be sent with, what is wrong, and the outline. */
	private static void refresh(MinecraftClient client) {
		box = SELECTION.box();
		problem = null;
		pending = false;
		ClientPlayerEntity player = client.player;
		if (player == null || world == null) {
			CaptureOutline.hide();
			return;
		}
		if (KeyPresses.rescanDue(tablesScanned, world.getTime(), tablesAt, TABLE_REFRESH_TICKS)) {
			tables = findTables(player);
			tablesAt = world.getTime();
			tablesScanned = true;
		}
		table = CaptureTables.nearest(player.getPos(), tables, box);
		if (box == null) {
			CaptureOutline.hide();
			problem = CaptureTables.nearest(player.getPos(), tables, null) == null ? CaptureProblem.of(CaptureProblem.Kind.NO_TABLE) : null;
			return;
		}
		problem = box.sizeProblem(world.getBottomY(), world.getTopY());
		if (problem == null && table == null) {
			problem = CaptureTables.nearest(player.getPos(), tables, null) == null
					? CaptureProblem.of(CaptureProblem.Kind.NO_TABLE) : CaptureProblem.of(CaptureProblem.Kind.TOO_FAR);
		}
		if (problem == null && SELECTION.fixed()) {
			problem = scan();
		}
		CaptureOutline.show(box.outline(), problem != null ? CaptureOutline.Status.REFUSED
				: SELECTION.fixed() && !pending ? CaptureOutline.Status.READY : CaptureOutline.Status.AIMING);
	}

	/** The Dream Tables in the chunks around the player that the client holds. */
	private static List<BlockPos> findTables(ClientPlayerEntity player) {
		List<BlockPos> found = new ArrayList<>();
		int radius = (int) Math.ceil(CaptureTables.MAX_PLAYER_DISTANCE / 16.0) + 1;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				WorldChunk chunk = world.getChunkManager().getWorldChunk(player.getChunkPos().x + dx, player.getChunkPos().z + dz);
				if (chunk == null) {
					continue;
				}
				for (BlockPos pos : chunk.getBlockEntityPositions()) {
					if (chunk.getBlockState(pos).getBlock() instanceof DreamTableBlock) {
						found.add(pos.toImmutable());
					}
				}
			}
		}
		return found;
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
