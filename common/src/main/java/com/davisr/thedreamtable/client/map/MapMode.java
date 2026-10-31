package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.client.DreamTableHologram;
import com.davisr.thedreamtable.client.TableNotices;
import com.davisr.thedreamtable.farm.FarmGeometry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

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

	/** Starts the mode at the player's eyes; false with a notice when it cannot start here. */
	public static boolean enter(MinecraftClient client, BlockPos tablePos) {
		if (active) {
			return true;
		}
		ClientPlayerEntity player = client.player;
		ClientWorld clientWorld = client.world;
		if (player == null || clientWorld == null) {
			refuse("gui.the_dream_table.map_terrain_not_loaded");
			return false;
		}
		if (client.getCameraEntity() != player || !client.options.getPerspective().isFirstPerson()) {
			refuse("gui.the_dream_table.map_first_person");
			return false;
		}
		if (!MapChecks.terrainLoaded(clientWorld, tablePos)) {
			refuse("gui.the_dream_table.map_terrain_not_loaded");
			return false;
		}
		if (!(clientWorld.getBlockState(tablePos).getBlock() instanceof DreamTableBlock)) {
			refuse("gui.the_dream_table.map_no_table");
			return false;
		}
		Vec3d eye = player.getEyePos();
		// The camera starts at the eyes, and the server answers placement checks only for a player near the table.
		if (eye.squaredDistanceTo(centreOf(tablePos)) > (double) FarmGeometry.AIM_RANGE * FarmGeometry.AIM_RANGE) {
			refuse("gui.the_dream_table.map_too_far");
			return false;
		}
		if (eye.y < tablePos.getY() - FLOOR_BELOW_TABLE) {
			refuse("gui.the_dream_table.map_too_deep");
			return false;
		}
		MapCamera start = new MapCamera(eye, player.getYaw(), player.getPitch());
		start.clampTo(centreOf(tablePos), WALL_RADIUS);
		if (MapChecks.solidGround(clientWorld, start.position())) {
			refuse("gui.the_dream_table.map_too_close");
			return false;
		}
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
		client.setCameraEntity(camera);
		// The first-person hand would be drawn from the free camera, so it is off while the mode is open.
		client.gameRenderer.setRenderHand(false);
		DreamTableHologram.setMapActive(true);
		return true;
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
			exit(client);
			if (client.currentScreen instanceof MapScreen) {
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
				&& client.currentScreen instanceof MapScreen && MapChecks.tableStanding(world, table);
	}

	private static void refuse(String key) {
		TableNotices.problem(Text.translatable(key));
	}
}
