package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.client.DreamTableHologram;
import com.davisr.thedreamtable.client.TableNotices;
import com.davisr.thedreamtable.farm.FarmGeometry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Map placement mode: a free camera that starts at the player's eyes, flies inside the table's reach and looks wherever the
 * player turns it. The camera is a client-only entity that is never added to the world, so the player's body stays put.
 */
public final class MapMode {
	/** One block short of the aim's reach, so the camera never sees farther than the aim can place. */
	private static final double WALL_RADIUS = FarmGeometry.AIM_RANGE - 1.0;
	/** Blocks per tick of held movement, about 10 a second. */
	private static final double MOVE_PER_TICK = 0.5;
	private static final double BLOCKS_PER_NOTCH = 3.0;
	/** Ticks between two notices that the camera is at the edge of the reach, so pushing against the wall does not repeat it. */
	private static final int LIMIT_NOTICE_TICKS = 40;

	private static boolean active;
	private static BlockPos table;
	private static ClientWorld world;
	private static MapCamera view;
	private static ArmorStandEntity camera;
	private static int limitCooldown;
	private static double ndcX;
	private static double ndcY;
	private static double aspect = 1.0;

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
		table = tablePos.toImmutable();
		world = clientWorld;
		view = new MapCamera(player.getEyePos(), player.getYaw(), player.getPitch());
		view.clampTo(centre(), WALL_RADIUS);
		camera = new ArmorStandEntity(EntityType.ARMOR_STAND, clientWorld);
		limitCooldown = 0;
		active = true;
		setPose(false);
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
	 * Applies the held keys, keeps the camera inside the reach and gives the game's camera this tick's pose. Ends the mode when
	 * the player, world, screen or table no longer fit it.
	 */
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
		if (client.currentScreen instanceof MapScreen screen) {
			MapScreen.Movement held = screen.movement();
			view.move(held.forward(), held.strafe(), held.vertical(), MOVE_PER_TICK);
		}
		if (view.clampTo(centre(), WALL_RADIUS) && limitCooldown == 0) {
			TableNotices.problem(Text.translatable("gui.the_dream_table.map_limit"));
			limitCooldown = LIMIT_NOTICE_TICKS;
		}
		setPose(true);
	}

	/** Turns the view by the mouse drag, in degrees. */
	public static void look(double deltaYaw, double deltaPitch) {
		if (active) {
			view.look(deltaYaw, deltaPitch);
		}
	}

	/** Scroll forward moves the camera forward along the look direction, {@link #BLOCKS_PER_NOTCH} blocks per notch. */
	public static void scroll(double notches) {
		if (active) {
			view.dolly(notches * BLOCKS_PER_NOTCH);
		}
	}

	/** Half the vertical field of view's tangent, from the FOV option. Status-effect and zoom changes to the FOV are not applied. */
	public static double tanHalfFovY(MinecraftClient client) {
		return Math.tan(Math.toRadians(client.options.getFov().getValue()) / 2.0);
	}

	/** The latest pointer, scaled to the window and converted to normalized device coordinates (x right, y up, each -1 to 1). */
	public static void pointer(double scaledX, double scaledY, int scaledWidth, int scaledHeight) {
		if (scaledWidth <= 0 || scaledHeight <= 0) {
			return;
		}
		ndcX = scaledX / scaledWidth * 2.0 - 1.0;
		ndcY = 1.0 - scaledY / scaledHeight * 2.0;
		aspect = (double) scaledWidth / scaledHeight;
	}

	public static double ndcX() {
		return ndcX;
	}

	public static double ndcY() {
		return ndcY;
	}

	public static double aspect() {
		return aspect;
	}

	/**
	 * Where the aim ray starts: the camera as the game draws it this frame, which lags the view state by up to a tick while
	 * the camera moves, so the spot under the pointer is the one on screen.
	 */
	@Nullable
	public static Vec3d rayOrigin(MinecraftClient client) {
		return active ? client.gameRenderer.getCamera().getPos() : null;
	}

	/** The aim ray's unit direction through the pointer, from the camera as it is drawn; null when the mode is off. */
	@Nullable
	public static Vec3d rayDirection(MinecraftClient client) {
		if (!active) {
			return null;
		}
		Camera drawn = client.gameRenderer.getCamera();
		return new MapCamera(drawn.getPos(), drawn.getYaw(), drawn.getPitch()).rayThrough(ndcX, ndcY, tanHalfFovY(client), aspect);
	}

	/**
	 * Gives the dummy camera the view's pose. With {@code glide} the pose it had is kept as the previous one, so the game's
	 * camera interpolates between ticks; without it both are the same, for the first pose. The yaw is unwrapped to the side
	 * of the previous yaw, so turning across south does not spin the camera the long way round.
	 */
	private static void setPose(boolean glide) {
		Vec3d eye = view.position();
		double eyeY = eye.y - camera.getStandingEyeHeight();
		float previousYaw = camera.getYaw();
		float yaw = (float) (previousYaw + MathHelper.wrapDegrees(view.yaw() - previousYaw));
		float pitch = (float) view.pitch();
		if (glide) {
			camera.prevX = camera.getX();
			camera.prevY = camera.getY();
			camera.prevZ = camera.getZ();
			camera.prevYaw = previousYaw;
			camera.prevPitch = camera.getPitch();
			camera.setPosition(eye.x, eyeY, eye.z);
			camera.setYaw(yaw);
			camera.setPitch(pitch);
		} else {
			camera.refreshPositionAndAngles(eye.x, eyeY, eye.z, yaw, pitch);
		}
	}

	private static Vec3d centre() {
		return new Vec3d(table.getX() + 0.5, table.getY() + 0.5, table.getZ() + 0.5);
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
