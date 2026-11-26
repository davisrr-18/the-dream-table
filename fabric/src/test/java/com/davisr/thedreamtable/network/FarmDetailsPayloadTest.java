package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.farm.FarmLoader;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
				List.of("minecraft:dirt"), List.of(4), List.of("minecraft:wheat", "minecraft:carrot"), List.of(240, 36), 75);

		assertEquals(details, roundTrip(FarmDetailsPayload.CODEC, details));
	}

	@Test
	void anUnloadedFarmRoundTripsWithoutStorage() {
		FarmDetailsPayload details = new FarmDetailsPayload("minecraft:overworld|farm", false, 0, 0, 0, 96,
				List.of(), List.of(), FarmLoader.UNKNOWN, 0, List.of(), List.of(), List.of(), List.of(), 0);

		assertEquals(details, roundTrip(FarmDetailsPayload.CODEC, details));
	}

	@Test
	void moreThanEightProductionItemsAreRejected() {
		List<String> ids = java.util.stream.IntStream.range(0, 9).mapToObj(index -> "test:item_" + index).toList();
		List<Integer> rates = java.util.Collections.nCopies(9, 10);
		FarmDetailsPayload details = new FarmDetailsPayload("minecraft:overworld|farm", true, 0, 54, 1, 96,
				List.of(), List.of(), 0, 0, List.of(), List.of(), ids, rates, 30);

		assertThrows(IllegalArgumentException.class, () -> roundTrip(FarmDetailsPayload.CODEC, details));
	}
}
