package com.davisr.dreamtablepoc.network;

import com.davisr.dreamtablepoc.DreamTablePoc;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CounterSyncPayload(int value) implements CustomPayload {
	public static final Id<CounterSyncPayload> ID = new Id<>(Identifier.of(DreamTablePoc.MOD_ID, "counter_sync"));
	public static final PacketCodec<RegistryByteBuf, CounterSyncPayload> CODEC =
			PacketCodecs.VAR_INT.<RegistryByteBuf>cast().xmap(CounterSyncPayload::new, CounterSyncPayload::value);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
