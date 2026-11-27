package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.ImportedStructures;
import com.davisr.thedreamtable.farm.capture.AreaCapture;
import com.davisr.thedreamtable.farm.capture.CaptureAccess;
import com.davisr.thedreamtable.farm.capture.CaptureBox;
import com.davisr.thedreamtable.farm.capture.CaptureFiles;
import com.davisr.thedreamtable.farm.capture.CaptureName;
import com.davisr.thedreamtable.farm.capture.CaptureProblem;
import com.davisr.thedreamtable.farm.capture.CaptureTables;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.SharedConstants;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Saves the blocks between two corners, picked in the world in capture mode, as a structure file in the mod's own folder so it shows in My
 * Schematics. Only operators and the host of a single-player or LAN world may; the server checks that, the box, the chunks and
 * the name again, and reads the world itself (the client sends no blocks).
 */
public record CaptureAreaPayload(BlockPos table, BlockPos from, BlockPos to, String name) implements CustomPayload {
	public static final Id<CaptureAreaPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "capture_area"));
	public static final PacketCodec<RegistryByteBuf, CaptureAreaPayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, CaptureAreaPayload::table,
			BlockPos.PACKET_CODEC, CaptureAreaPayload::from,
			BlockPos.PACKET_CODEC, CaptureAreaPayload::to,
			PacketCodecs.string(CaptureName.MAX_LENGTH), CaptureAreaPayload::name,
			CaptureAreaPayload::new
	);

	/** The player may stand anywhere a farm can be aimed from, as for {@link CheckPlacementPayload}; the client picks its table by the same number. */
	private static final double MAX_DISTANCE = CaptureTables.MAX_PLAYER_DISTANCE;
	/** One capture reads up to a million cells and writes a file, so a person's one click every two seconds is the most allowed. */
	static final RequestThrottle THROTTLE = new RequestThrottle(1, 40);

	public static void register() {
		THROTTLE.forgetOnDisconnect();
		Platform.network().registerClientToServer(ID, CODEC, (payload, player) -> {
			if (!THROTTLE.allow(player.getUuid(), player.getServer().getTicks())) {
				return;
			}
			CaptureProblem problem = handle(player, payload);
			if (problem != null) {
				TableNoticePayload.problem(player, problem.text());
			}
		});
	}

	/** Null when the capture was saved (and told to the player), otherwise why not. */
	private static CaptureProblem handle(ServerPlayerEntity player, CaptureAreaPayload payload) {
		if (!CaptureAccess.allowed(player.hasPermissionLevel(CaptureAccess.OPERATOR_LEVEL), player.getServer().isHost(player.getGameProfile()))) {
			return CaptureProblem.of(CaptureProblem.Kind.NOT_ALLOWED);
		}
		if (!(player.getWorld() instanceof ServerWorld world)
				|| player.squaredDistanceTo(payload.table().toCenterPos()) > MAX_DISTANCE * MAX_DISTANCE) {
			return CaptureProblem.of(CaptureProblem.Kind.TOO_FAR);
		}
		// getWorldChunk answers only for a chunk that is ready now and never waits for one, so the server thread is never held up.
		ServerChunkManager chunks = world.getChunkManager();
		WorldChunk tableChunk = chunks.getWorldChunk(payload.table().getX() >> 4, payload.table().getZ() >> 4);
		if (tableChunk == null || !(tableChunk.getBlockEntity(payload.table()) instanceof DreamTableBlockEntity)) {
			return CaptureProblem.of(CaptureProblem.Kind.TOO_FAR);
		}
		CaptureBox box = CaptureBox.of(payload.from(), payload.to());
		// The size is checked before the reach so a corner a hundred million blocks away is a size problem, not arithmetic.
		CaptureProblem size = box.sizeProblem(world.getBottomY(), world.getTopY());
		if (size != null) {
			return size;
		}
		if (!box.inReachOf(payload.table())) {
			return CaptureProblem.of(CaptureProblem.Kind.TOO_FAR);
		}
		CaptureFiles.Saved saved = AreaCapture.run(box, payload.name(), world.getBottomY(), world.getTopY(),
				SharedConstants.getGameVersion().getSaveVersion().getId(),
				(chunkX, chunkZ) -> {
					WorldChunk chunk = chunks.getWorldChunk(chunkX, chunkZ);
					return chunk == null ? null : chunk::getBlockState;
				},
				ImportedStructures.ownFolder(Platform.paths().gameDir()),
				ImportedStructures.folders(Platform.paths().gameDir(), player.getServer().getSavePath(WorldSavePath.ROOT)));
		if (saved.problem() != null) {
			return saved.problem();
		}
		Text done = Text.translatable("gui.the_dream_table.capture_saved", saved.fileName());
		TableNoticePayload.info(player, done);
		player.sendMessage(done, false);
		// The list is read on the importer's worker; a read already running is followed by the next table open's refresh.
		FarmTemplates.reloadImportsAsync(player.getServer(), imports -> { }, failure -> { });
		return null;
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
