package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.farm.FarmLoader;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import com.davisr.thedreamtable.network.codec.RegistryByteBuf;
import com.davisr.thedreamtable.network.codec.PacketCodec;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarmDetailsPayloadTest {
	private static <T> T roundTrip(PacketCodec<RegistryByteBuf, T> codec, T payload) {
		RegistryByteBuf out = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
		codec.encode(out, payload);
		RegistryByteBuf in = new RegistryByteBuf(Unpooled.wrappedBuffer(ByteBufUtil.getBytes(out)), DynamicRegistryManager.EMPTY);
		T decoded = codec.decode(in);
		assertEquals(0, in.readableBytes());
		return decoded;
	}

	@Test
	void storageAndConditionRoundTripWithTheirItemLists() {
		FarmDetailsPayload details = new FarmDetailsPayload("minecraft:overworld|farm", true, 40, 108, 2, 96,
				List.of("minecraft:wheat", "minecraft:carrot"), List.of(12, 3), 4, 1,
				List.of("minecraft:dirt"), List.of(4));

		assertEquals(details, roundTrip(FarmDetailsPayload.CODEC, details));
	}

	@Test
	void anUnloadedFarmRoundTripsWithoutStorage() {
		FarmDetailsPayload details = new FarmDetailsPayload("minecraft:overworld|farm", false, 0, 0, 0, 96,
				List.of(), List.of(), FarmLoader.UNKNOWN, 0, List.of(), List.of());

		assertEquals(details, roundTrip(FarmDetailsPayload.CODEC, details));
	}
}
