package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

/** What map mode asks of the client's world before it opens and while it runs. */
final class MapChecks {
	private MapChecks() {
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
