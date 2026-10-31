package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;

/** What map mode asks of the client's world before it opens and while it runs. */
final class MapChecks {
	private MapChecks() {
	}

	/** How far the camera keeps from any solid shape, so the near plane never reaches inside a block. */
	private static final double CAMERA_MARGIN = 0.3;

	/**
	 * Whether a camera at this point would touch solid ground: any block with a collision shape within {@link #CAMERA_MARGIN}
	 * of it, except leaves and logs, so slabs, stairs and block faces cannot be looked through. Unloaded chunks are solid.
	 */
	static boolean solidGround(ClientWorld clientWorld, Vec3d point) {
		Box camera = new Box(point, point).expand(CAMERA_MARGIN);
		for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(camera.minX, camera.minY, camera.minZ), BlockPos.ofFloored(camera.maxX, camera.maxY, camera.maxZ))) {
			if (blocks(clientWorld, pos, camera)) {
				return true;
			}
		}
		return false;
	}

	private static boolean blocks(ClientWorld clientWorld, BlockPos pos, Box camera) {
		if (!chunkLoaded(clientWorld, ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()))) {
			return true;
		}
		BlockState state = clientWorld.getBlockState(pos);
		if (state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS)) {
			return false;
		}
		for (Box part : state.getCollisionShape(clientWorld, pos).getBoundingBoxes()) {
			if (part.offset(pos).intersects(camera)) {
				return true;
			}
		}
		return false;
	}

	/** Whether the table is still a table, in a chunk the client holds. */
	static boolean tableStanding(ClientWorld clientWorld, BlockPos pos) {
		return chunkLoaded(clientWorld, ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()))
				&& clientWorld.getBlockState(pos).getBlock() instanceof DreamTableBlock;
	}

	/** The table's chunk and the four chunks next to it, which is what the view over the farm site reads. */
	static boolean terrainLoaded(ClientWorld clientWorld, BlockPos pos) {
		int chunkX = ChunkSectionPos.getSectionCoord(pos.getX());
		int chunkZ = ChunkSectionPos.getSectionCoord(pos.getZ());
		return chunkLoaded(clientWorld, chunkX, chunkZ)
				&& chunkLoaded(clientWorld, chunkX + 1, chunkZ)
				&& chunkLoaded(clientWorld, chunkX - 1, chunkZ)
				&& chunkLoaded(clientWorld, chunkX, chunkZ + 1)
				&& chunkLoaded(clientWorld, chunkX, chunkZ - 1);
	}

	/** Whether the player has chosen a farm at the table, as far as this client's session knows. */
	static boolean farmChosen(MinecraftClient client, BlockPos followed) {
		BlockEntity entity = client.world == null ? null : client.world.getBlockEntity(followed);
		return client.player != null && entity instanceof DreamTableBlockEntity tableEntity
				&& !tableEntity.session(client.player).getSelectedFarm().isEmpty();
	}

	/** The client's chunk manager, not {@code ClientWorld.isChunkLoaded}, which always answers true. */
	private static boolean chunkLoaded(ClientWorld clientWorld, int chunkX, int chunkZ) {
		return clientWorld.getChunkManager().isChunkLoaded(chunkX, chunkZ);
	}
}
