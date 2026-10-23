package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** A player's own session at a table, sent only to that player. Other players' sessions never reach a client. */
public record SessionSyncPayload(BlockPos table, NbtCompound session) implements CustomPayload {
	public static final Id<SessionSyncPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "session_sync"));
	public static final PacketCodec<RegistryByteBuf, SessionSyncPayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, SessionSyncPayload::table,
			PacketCodecs.NBT_COMPOUND, SessionSyncPayload::session,
			SessionSyncPayload::new
	);

	public static void register() {
		PayloadTypeRegistry.playS2C().register(ID, CODEC);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
