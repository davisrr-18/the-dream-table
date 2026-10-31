package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.client.DreamTableHologram;
import com.davisr.thedreamtable.client.TableNotices;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Map placement mode: the camera rises straight above the followed Dream Table and looks down, north at the top of the
 * screen. The camera follows a client-only entity that is never added to the world, so the player's body stays put.
 */
public final class MapMode {
	private static final double DEFAULT_HEIGHT = 80.0;
	private static final double MIN_HEIGHT = 10.0;
	private static final double MAX_HEIGHT = 110.0;
	private static final double ZOOM_PER_NOTCH = 0.85;
	/** Yaw 180 faces north; with pitch 90 the screen's top is north and its right is east. */
	private static final float YAW = 180.0F;
	private static final float PITCH = 90.0F;

	private static boolean active;
	private static BlockPos table;
	private static ClientWorld world;
	private static ArmorStandEntity camera;
	private static double height = DEFAULT_HEIGHT;
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
		} else if (client.world == null || !terrainLoaded(client.world, followed)) {
			refuse("gui.the_dream_table.map_terrain_not_loaded");
		} else if (!DreamTableHologram.mapAvailable()) {
			refuse(farmChosen(client, followed) ? "gui.the_dream_table.map_not_ready" : "gui.the_dream_table.map_no_farm");
		} else if (enter(client, followed)) {
			client.setScreen(new MapScreen());
		}
	}

	/** Starts the mode above the table; false with a notice when it cannot start here. */
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
		if (!terrainLoaded(clientWorld, tablePos)) {
			refuse("gui.the_dream_table.map_terrain_not_loaded");
			return false;
		}
		if (!(clientWorld.getBlockState(tablePos).getBlock() instanceof DreamTableBlock)) {
			refuse("gui.the_dream_table.map_no_table");
			return false;
		}
		table = tablePos.toImmutable();
		world = clientWorld;
		camera = new ArmorStandEntity(EntityType.ARMOR_STAND, clientWorld);
		height = DEFAULT_HEIGHT;
		active = true;
		place();
		client.setCameraEntity(camera);
		// The first-person hand would be drawn from the overhead camera, so it is off while the mode is open.
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
		table = null;
		world = null;
		client.gameRenderer.setRenderHand(true);
		DreamTableHologram.setMapActive(false);
	}

	/** Keeps the camera above the table, and ends the mode when the player, world, screen or table no longer fit it. */
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
		place();
	}

	/** Scroll up zooms in. Each notch scales the height by 0.85, kept between 10 and 110 blocks above the table. */
	public static void zoom(double scrollDelta) {
		if (!active) {
			return;
		}
		height = Math.clamp(height * Math.pow(ZOOM_PER_NOTCH, scrollDelta), MIN_HEIGHT, MAX_HEIGHT);
		place();
	}

	public static double height() {
		return height;
	}

	/** The camera's position: the table block's centre at the set height, or null when the mode is off. */
	@Nullable
	public static Vec3d cameraPos() {
		return active ? eyePos() : null;
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
	 * The entity sits so that the camera, which adds the entity's standing eye height to its position, lands exactly on the
	 * eye point. The previous position and angles equal the current ones, so no interpolation drifts between ticks.
	 */
	private static void place() {
		Vec3d eye = eyePos();
		camera.refreshPositionAndAngles(eye.x, eye.y - camera.getStandingEyeHeight(), eye.z, YAW, PITCH);
		camera.prevYaw = YAW;
		camera.prevPitch = PITCH;
	}

	private static Vec3d eyePos() {
		return new Vec3d(table.getX() + 0.5, table.getY() + height, table.getZ() + 0.5);
	}

	private static boolean stillFits(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		return player != null && player.isAlive() && client.world == world && client.getCameraEntity() == camera
				&& client.currentScreen instanceof MapScreen && tableStanding(world, table);
	}

	private static boolean farmChosen(MinecraftClient client, BlockPos followed) {
		BlockEntity entity = client.world == null ? null : client.world.getBlockEntity(followed);
		return client.player != null && entity instanceof DreamTableBlockEntity tableEntity
				&& !tableEntity.session(client.player).getSelectedFarm().isEmpty();
	}

	private static boolean tableStanding(ClientWorld clientWorld, BlockPos pos) {
		return chunkLoaded(clientWorld, ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()))
				&& clientWorld.getBlockState(pos).getBlock() instanceof DreamTableBlock;
	}

	/** The table's chunk and the four chunks next to it, which is what the view over the farm site reads. */
	private static boolean terrainLoaded(ClientWorld clientWorld, BlockPos pos) {
		int chunkX = ChunkSectionPos.getSectionCoord(pos.getX());
		int chunkZ = ChunkSectionPos.getSectionCoord(pos.getZ());
		return chunkLoaded(clientWorld, chunkX, chunkZ)
				&& chunkLoaded(clientWorld, chunkX + 1, chunkZ)
				&& chunkLoaded(clientWorld, chunkX - 1, chunkZ)
				&& chunkLoaded(clientWorld, chunkX, chunkZ + 1)
				&& chunkLoaded(clientWorld, chunkX, chunkZ - 1);
	}

	/** The client's chunk manager, not {@code ClientWorld.isChunkLoaded}, which always answers true. */
	private static boolean chunkLoaded(ClientWorld clientWorld, int chunkX, int chunkZ) {
		return clientWorld.getChunkManager().isChunkLoaded(chunkX, chunkZ);
	}

	private static void refuse(String key) {
		TableNotices.problem(Text.translatable(key));
	}
}
