package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.client.CaptureOutline;
import com.davisr.thedreamtable.farm.capture.AreaCapture;
import com.davisr.thedreamtable.farm.capture.CaptureAccess;
import com.davisr.thedreamtable.farm.capture.CaptureBox;
import com.davisr.thedreamtable.farm.capture.CaptureName;
import com.davisr.thedreamtable.farm.capture.CaptureProblem;
import com.davisr.thedreamtable.farm.capture.CaptureScan;
import com.davisr.thedreamtable.farm.capture.CaptureSelection;
import com.davisr.thedreamtable.farm.capture.CaptureStructure;
import com.davisr.thedreamtable.farm.capture.CaptureSteps;
import com.davisr.thedreamtable.farm.capture.CaptureTables;
import com.davisr.thedreamtable.network.CaptureAreaPayload;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Capture inside the capture map mode ({@link MapMode#openCapture}), for operators and the host of a single-player world: the
 * map's own camera, range and crosshair, with only the capture's actions. A left click sets corner 1 at the block the crosshair
 * hits; the box then follows the crosshair until a second click fixes corner 2 at the aimed block (nothing aimed at keeps the
 * preview's corner); the top and bottom move with the capture keys; the name screen saves it. The table is the map's centre and
 * the one sent with the capture. The server checks everything again.
 */
final class MapCapture {
	/** How far the crosshair's ray looks for a block, as far as the placement map's aim. */
	private static final double RAY_LENGTH = 128.0;
	/** The fixed box must stay unchanged this many ticks before its blocks are read. */
	private static final long SETTLE_TICKS = 5;
	/** A box that had an unloaded chunk is read again after this many ticks. */
	private static final long RETRY_TICKS = 40;

	private static final CaptureSelection SELECTION = new CaptureSelection();
	private static final ConfirmGuard CONFIRM = new ConfirmGuard();
	private static CaptureBox box;
	private static CaptureProblem problem;
	private static String typed = "";
	private static CaptureBox scanned;
	private static CaptureProblem scanProblem;
	private static int scanBlocks;
	private static long scannedAt;
	private static CaptureBox seen;
	private static long seenAt;
	private static boolean pending;

	private MapCapture() {
	}

	/** Whether this player may capture: the same rule the server applies, so the mode is never offered to anyone it would refuse. */
	static boolean allowed(MinecraftClient client) {
		return client.player != null
				&& CaptureAccess.allowed(client.player.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS), client.isIntegratedServerRunning());
	}

	/** The Dream Tables the client holds near the player; the caller orders them and keeps those within the distance the server accepts. */
	static List<BlockPos> tablesNear(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		if (player == null || world == null) {
			return List.of();
		}
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

	static void start() {
		stop();
	}

	static void stop() {
		box = null;
		problem = null;
		scanned = null;
		seen = null;
		pending = false;
		SELECTION.clear();
		CaptureOutline.hide();
	}

	/** Once per rendered frame: the block under the crosshair, the box, what is wrong with it, and the outline. */
	static void frame(MinecraftClient client) {
		ClientWorld world = client.world;
		ClientPlayerEntity player = client.player;
		BlockPos table = MapMode.tablePos();
		if (world == null || player == null || table == null) {
			return;
		}
		if (SELECTION.started() && !SELECTION.fixed()) {
			SELECTION.follow(aimedBlock(client, world));
		}
		box = SELECTION.box();
		problem = null;
		pending = false;
		if (box == null) {
			CaptureOutline.hide();
			return;
		}
		problem = box.sizeProblem(world.getBottomY(), world.getBottomY() + world.getHeight());
		if (problem == null && !CaptureTables.usable(player.getEntityPos(), table, box)) {
			problem = CaptureProblem.of(CaptureProblem.Kind.TOO_FAR);
		}
		if (problem == null && SELECTION.fixed()) {
			problem = scan(world);
		}
		CaptureOutline.show(box.outline(), problem != null ? CaptureOutline.Status.REFUSED
				: SELECTION.fixed() && !pending ? CaptureOutline.Status.READY : CaptureOutline.Status.AIMING);
	}

	/** The block itself that the map crosshair's ray meets first, or null when it meets none. */
	private static BlockPos aimedBlock(MinecraftClient client, ClientWorld world) {
		Vec3d origin = MapMode.rayOrigin(client);
		Vec3d direction = MapMode.rayDirection(client);
		if (origin == null || direction == null || client.player == null) {
			return null;
		}
		BlockHitResult hit = world.raycast(new RaycastContext(origin, origin.add(direction.multiply(RAY_LENGTH)), RaycastContext.ShapeType.OUTLINE,
				RaycastContext.FluidHandling.NONE, client.player));
		return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
	}

	/** A left click: corner 1, or corner 2 at the aimed block (at the preview's corner when nothing is aimed at), or a new box. */
	static void click(MinecraftClient client) {
		if (client.world == null) {
			return;
		}
		if (SELECTION.fixed()) {
			// A new box is a new structure: its name starts empty again.
			typed = "";
		}
		SELECTION.click(aimedBlock(client, client.world));
	}

	/** Up grows the top and Down grows the bottom; with Shift they do the opposite. */
	static void up(boolean shift) {
		SELECTION.moveTop(shift ? -1 : 1);
	}

	static void down(boolean shift) {
		SELECTION.moveBottom(shift ? 1 : -1);
	}

	/**
	 * Reads the fixed box once it has stayed the same for {@link #SETTLE_TICKS} (so holding a key to move a big box reads nothing in
	 * between), and again only to retry a box that had an unloaded chunk. The importer's verdict is asked on the result, as the
	 * server will ask it. While the box is still moving the answer is "none yet" and {@link #pending} is set.
	 */
	private static CaptureProblem scan(ClientWorld world) {
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

	/** Whether Enter would open the name step: a fixed box with nothing wrong and its blocks counted. */
	static boolean canSave() {
		return SELECTION.fixed() && box != null && problem == null && !pending && MapMode.tablePos() != null;
	}

	/** Whether an Enter arriving now is a real press and not the tail of the hold that closed the name screen. */
	static boolean confirmAccepted(boolean physicallyDown) {
		return CONFIRM.accept(physicallyDown);
	}

	static void nameScreenClosed(boolean physicallyDown) {
		CONFIRM.screenClosed(physicallyDown);
	}

	/** Save in the name screen: sends the capture. The box and the name stay, so a refused save can be renamed or adjusted; the next click starts a new box. */
	static boolean save(String name) {
		BlockPos table = MapMode.tablePos();
		if (CaptureName.sanitize(name) == null || !canSave()) {
			return false;
		}
		typed = name;
		Platform.clientNetwork().send(new CaptureAreaPayload(table, box.min(), box.max(), name));
		return true;
	}

	static CaptureSteps.Step step() {
		return CaptureSteps.current(SELECTION);
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
