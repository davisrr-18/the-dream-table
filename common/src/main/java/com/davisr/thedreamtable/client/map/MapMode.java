package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.client.DreamTableHologram;
import com.davisr.thedreamtable.client.TableNotices;
import com.davisr.thedreamtable.farm.FarmGeometry;
import com.davisr.thedreamtable.farm.capture.CaptureTables;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Map placement mode: a spectator-like free camera that starts at the player's eyes and flies inside the table's reach. The
 * crosshair is the aim and the mouse turns the view as in the game. The camera never enters solid ground and never goes far
 * below the table. It is a client-only entity that is never added to the world, so the player's body stays put.
 */
public final class MapMode {
	/** One block short of the aim's reach, so the camera never sees farther than the aim can place. */
	private static final double WALL_RADIUS = FarmGeometry.AIM_RANGE - 1.0;
	/** How far below the table's block the camera may go. Deeper, the ground could be scouted through open shafts. */
	private static final int FLOOR_BELOW_TABLE = 12;
	/** Blocks per tick of held movement, about 10 a second. Ctrl multiplies it by {@link #SPRINT_FACTOR}. */
	private static final double MOVE_PER_TICK = 0.5;
	private static final double SPRINT_FACTOR = 3.0;
	/** How fast the movement eases in and out: about two thirds of the way in this many ticks, near full in half a second. */
	private static final double EASE_TICKS = 3.0;
	/** A frame longer than this many ticks (100 ms) is cut short, so a stall does not throw the camera across the reach. */
	private static final double MAX_FRAME_TICKS = 2.0;
	private static final double BLOCKS_PER_NOTCH = 3.0;
	/** Ticks between two notices that the camera is at the edge of the reach, so pushing against the wall does not repeat it. */
	private static final int LIMIT_NOTICE_TICKS = 40;

	private static boolean active;
	/** The capture variant: the same camera, range and limits, centred on the nearest table, with only the capture's actions (see {@link MapCapture}). */
	private static boolean capture;
	/** The capture's name step is open over the map, so the map's own screen being replaced does not end the mode. */
	private static boolean naming;
	private static BlockPos table;
	private static ClientWorld world;
	private static MapCamera view;
	private static MapCameraEntity camera;
	private static int limitCooldown;
	/** The movement as it eases toward the held keys, so the camera speeds up and slows down instead of jumping. */
	private static double easedForward;
	private static double easedStrafe;
	private static double easedVertical;
	private static double easedSpeed;

	private MapMode() {
	}

	public static boolean active() {
		return active;
	}

	/** The map key: opens the mode and its screen when a farm is chosen at the followed table, otherwise says why not. */
	public static void open(MinecraftClient client) {
		if (active) {
			return;
		}
		BlockPos followed = DreamTableHologram.mapTable();
		if (followed == null) {
			refuse("gui.the_dream_table.map_no_table");
		} else if (client.world == null || !MapChecks.terrainLoaded(client.world, followed)) {
			refuse("gui.the_dream_table.map_terrain_not_loaded");
		} else if (!DreamTableHologram.mapAvailable()) {
			refuse(MapChecks.farmChosen(client, followed) ? "gui.the_dream_table.map_not_ready" : "gui.the_dream_table.map_no_farm");
		} else if (enter(client, followed)) {
			client.setScreen(new MapScreen());
		}
	}

	/**
	 * The capture key: opens the capture variant of the map, centred on the nearest Dream Table the client knows within the
	 * distance the server accepts, with no farm needed. Says why not when the player may not capture or no table is near.
	 */
	public static void openCapture(MinecraftClient client) {
		if (active) {
			return;
		}
		if (!MapCapture.allowed(client)) {
			TableNotices.problem(Text.translatable("gui.the_dream_table.capture_problem.not_allowed"));
			return;
		}
		// The nearest table the map can actually start at: a nearer one the camera could not start beside is passed over, and when none
		// can be entered the nearest one's reason is told.
		List<BlockPos> tables = CaptureTables.byDistance(client.player == null ? Vec3d.ZERO : client.player.getPos(), MapCapture.tablesNear(client));
		if (tables.isEmpty()) {
			TableNotices.problem(Text.translatable("gui.the_dream_table.capture_problem.no_table"));
			return;
		}
		String firstReason = null;
		for (BlockPos candidate : tables) {
			Plan plan = plan(client, candidate, true);
			if (plan.refusal() == null) {
				begin(client, candidate, plan.start(), true);
				MapCapture.start();
				client.setScreen(new MapScreen());
				return;
			}
			if (firstReason == null) {
				firstReason = plan.refusal();
			}
		}
		refuse(firstReason);
	}

	/** Whether a screen is one this mode owns: its own, or, in the capture variant, the capture's name step laid over it. Pure, so it is tested without a client. */
	static boolean ownsScreen(boolean mapScreen, boolean nameScreen, boolean captureVariant) {
		return mapScreen || (nameScreen && captureVariant);
	}

	private static boolean ownsScreen(MinecraftClient client) {
		return ownsScreen(client.currentScreen instanceof MapScreen, client.currentScreen instanceof CaptureNameScreen, capture);
	}

	/** Starts the mode at the player's eyes; false with a notice when it cannot start here. */
	public static boolean enter(MinecraftClient client, BlockPos tablePos) {
		return enter(client, tablePos, false);
	}

	private static boolean enter(MinecraftClient client, BlockPos tablePos, boolean forCapture) {
		if (active) {
			return true;
		}
		Plan plan = plan(client, tablePos, forCapture);
		if (plan.refusal() != null) {
			refuse(plan.refusal());
			return false;
		}
		begin(client, tablePos, plan.start(), forCapture);
		return true;
	}

	/** Where the camera would start, or the lang key of why the mode cannot start at this table from here. */
	private record Plan(MapCamera start, String refusal) {
		static Plan refused(String key) {
			return new Plan(null, key);
		}
	}

	private static Plan plan(MinecraftClient client, BlockPos tablePos, boolean forCapture) {
		ClientPlayerEntity player = client.player;
		ClientWorld clientWorld = client.world;
		if (player == null || clientWorld == null) {
			return Plan.refused("gui.the_dream_table.map_terrain_not_loaded");
		}
		if (client.getCameraEntity() != player || !client.options.getPerspective().isFirstPerson()) {
			return Plan.refused("gui.the_dream_table.map_first_person");
		}
		if (!MapChecks.terrainLoaded(clientWorld, tablePos)) {
			return Plan.refused("gui.the_dream_table.map_terrain_not_loaded");
		}
		if (!(clientWorld.getBlockState(tablePos).getBlock() instanceof DreamTableBlock)) {
			return Plan.refused("gui.the_dream_table.map_no_table");
		}
		Vec3d eye = player.getEyePos();
		// The camera starts at the eyes (pulled inside the reach), and the server answers only for a player near the table: a capture
		// accepts the player a little farther than a placement does.
		double near = forCapture ? CaptureTables.MAX_PLAYER_DISTANCE : FarmGeometry.AIM_RANGE;
		if (eye.squaredDistanceTo(centreOf(tablePos)) > near * near) {
			return Plan.refused("gui.the_dream_table.map_too_far");
		}
		if (eye.y < tablePos.getY() - FLOOR_BELOW_TABLE) {
			return Plan.refused("gui.the_dream_table.map_too_deep");
		}
		MapCamera start = new MapCamera(eye, player.getYaw(), player.getPitch());
		boolean pulledIn = start.clampTo(centreOf(tablePos), WALL_RADIUS);
		if (MapChecks.solidGround(clientWorld, start.position())) {
			// A capture can start from past the wall; pulled toward the table the camera can land in terrain, and then the player is
			// simply too far to start from here.
			return Plan.refused(pulledIn && forCapture ? "gui.the_dream_table.capture_move_closer" : "gui.the_dream_table.map_too_close");
		}
		return new Plan(start, null);
	}

	/** Starts the mode with a camera the plan accepted. */
	private static void begin(MinecraftClient client, BlockPos tablePos, MapCamera start, boolean forCapture) {
		ClientPlayerEntity player = client.player;
		ClientWorld clientWorld = client.world;
		table = tablePos.toImmutable();
		world = clientWorld;
		view = start;
		camera = new MapCameraEntity(clientWorld, player.getStandingEyeHeight());
		camera.place(view);
		limitCooldown = 0;
		easedForward = 0.0;
		easedStrafe = 0.0;
		easedVertical = 0.0;
		easedSpeed = 1.0;
		active = true;
		capture = forCapture;
		naming = false;
		client.setCameraEntity(camera);
		// The first-person hand would be drawn from the free camera, so it is off while the mode is open.
		client.gameRenderer.setRenderHand(false);
		if (forCapture) {
			// No farm is aimed or drawn in the capture variant.
			DreamTableHologram.setSuppressed(true);
		} else {
			DreamTableHologram.setMapActive(true);
		}
	}

	/** Whether this is the capture variant of the map, with only the capture's actions. */
	public static boolean capture() {
		return active && capture;
	}

	/** The table the map is centred on, the one a capture is sent with; null when the mode is off. */
	static BlockPos tablePos() {
		return active ? table : null;
	}

	/** Tells the mode the capture's name step is open over its screen (true) or closed again. */
	static void setNaming(boolean open) {
		naming = open;
	}

	static boolean naming() {
		return naming;
	}

	/** Leaves the mode and gives the camera back to the player. Does nothing when already off, so it can run from any close path. */
	public static void exit(MinecraftClient client) {
		if (!active) {
			return;
		}
		active = false;
		if (camera != null && client.getCameraEntity() == camera) {
			client.setCameraEntity(client.player);
		}
		camera = null;
		view = null;
		table = null;
		world = null;
		client.gameRenderer.setRenderHand(true);
		DreamTableHologram.setMapActive(false);
		if (capture) {
			capture = false;
			naming = false;
			DreamTableHologram.setSuppressed(false);
			MapCapture.stop();
		}
	}

	/**
	 * Moves the camera by the held keys for one rendered frame and gives the game's camera the new pose. The screen calls it
	 * from its render, so the view moves with the frame rate rather than in 20 steps a second.
	 */
	public static void frame(MinecraftClient client, MapScreen.Movement held, float frameTicks) {
		if (!active || client.world != world) {
			return;
		}
		double ticks = Math.min(Math.max(frameTicks, 0.0), MAX_FRAME_TICKS);
		// Each frame closes the same share of the gap per tick, whatever the frame rate.
		double ease = 1.0 - Math.exp(-ticks / EASE_TICKS);
		easedForward += (held.forward() - easedForward) * ease;
		easedStrafe += (held.strafe() - easedStrafe) * ease;
		easedVertical += (held.vertical() - easedVertical) * ease;
		easedSpeed += ((held.fast() ? SPRINT_FACTOR : 1.0) - easedSpeed) * ease;
		double speed = MOVE_PER_TICK * ticks * easedSpeed;
		Vec3d before = view.position();
		view.move(easedForward, easedStrafe, easedVertical, speed, blocked(), floorY());
		settle(before);
	}

	/** Ends the mode when the player, world, screen or table no longer fit it, and counts down the limit notice. */
	public static void tick(MinecraftClient client) {
		if (!active) {
			return;
		}
		if (!stillFits(client)) {
			// Only a screen that is this mode's own is closed with it; any other (a kick reason, a loading screen) is left alone.
			boolean ours = ownsScreen(client);
			exit(client);
			if (ours) {
				client.setScreen(null);
			}
			return;
		}
		if (limitCooldown > 0) {
			limitCooldown--;
		}
	}

	/** Turns the view by the mouse, in degrees, and gives the game's camera the new pose at once. */
	public static void look(double deltaYaw, double deltaPitch) {
		if (active) {
			view.look(deltaYaw, deltaPitch);
			camera.place(view);
		}
	}

	/** Scroll forward moves the camera forward along the look direction, {@link #BLOCKS_PER_NOTCH} blocks per notch. */
	public static void scroll(double notches) {
		if (!active) {
			return;
		}
		Vec3d before = view.position();
		view.dolly(notches * BLOCKS_PER_NOTCH, blocked(), floorY());
		settle(before);
	}

	/** Where the aim ray starts: the camera as the game draws it this frame, so the spot under the crosshair is the one on screen. */
	@Nullable
	public static Vec3d rayOrigin(MinecraftClient client) {
		return active ? client.gameRenderer.getCamera().getPos() : null;
	}

	/** The aim ray's unit direction: the drawn camera's look vector, which runs through the centre of the screen. Null when the mode is off. */
	@Nullable
	public static Vec3d rayDirection(MinecraftClient client) {
		if (!active) {
			return null;
		}
		Vector3f look = client.gameRenderer.getCamera().getHorizontalPlane();
		return new Vec3d(look.x(), look.y(), look.z()).normalize();
	}

	/**
	 * Keeps the camera inside the reach. A move the reach pulls into ground is undone, so the camera never ends in solid ground;
	 * the limit notice is shown either way. Then the game's camera gets the pose.
	 */
	private static void settle(Vec3d before) {
		if (view.clampTo(centre(), WALL_RADIUS)) {
			if (blocked().test(view.position())) {
				view.moveTo(before);
			}
			notifyLimit();
		}
		camera.place(view);
	}

	private static void notifyLimit() {
		if (limitCooldown == 0) {
			TableNotices.problem(Text.translatable("gui.the_dream_table.map_limit"));
			limitCooldown = LIMIT_NOTICE_TICKS;
		}
	}

	private static Predicate<Vec3d> blocked() {
		return point -> MapChecks.solidGround(world, point);
	}

	private static double floorY() {
		return table.getY() - FLOOR_BELOW_TABLE;
	}

	private static Vec3d centre() {
		return centreOf(table);
	}

	private static Vec3d centreOf(BlockPos pos) {
		return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
	}

	private static boolean stillFits(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		return player != null && player.isAlive() && client.world == world && client.getCameraEntity() == camera
				&& ownsScreen(client) && MapChecks.tableStanding(world, table);
	}

	private static void refuse(String key) {
		TableNotices.problem(Text.translatable(key));
	}
}
