package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmRole;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Wire formats of the payloads whose codecs need no world: they must read back what they wrote, byte for byte. */
class PayloadCodecsTest {
	private static final BlockPos TABLE = new BlockPos(1, 2, 3);
	private static final String KEY = "minecraft:overworld|10000000-0000-0000-0000-000000000001";

	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	private static <T extends CustomPayload> byte[] roundTrip(PacketCodec<RegistryByteBuf, T> codec, T payload) {
		RegistryByteBuf out = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
		codec.encode(out, payload);
		byte[] bytes = ByteBufUtil.getBytes(out);
		RegistryByteBuf in = new RegistryByteBuf(Unpooled.wrappedBuffer(bytes), DynamicRegistryManager.EMPTY);
		assertEquals(payload, codec.decode(in));
		assertEquals(0, in.readableBytes());
		return bytes;
	}

	@Test
	void blockPosPayloadsKeepTheirLayout() {
		assertEquals("0000004000003002", ByteBufUtil.hexDump(roundTrip(RequestSessionPayload.CODEC, new RequestSessionPayload(TABLE))));
		assertEquals("0000004000003002", ByteBufUtil.hexDump(roundTrip(DepositMaterialPayload.CODEC, new DepositMaterialPayload(TABLE))));
	}

	@Test
	void watchAreasIsOneBoolean() {
		assertEquals("01", ByteBufUtil.hexDump(roundTrip(FarmAreasPayload.WatchAreasPayload.CODEC, new FarmAreasPayload.WatchAreasPayload(true))));
		assertEquals("00", ByteBufUtil.hexDump(roundTrip(FarmAreasPayload.WatchAreasPayload.CODEC, new FarmAreasPayload.WatchAreasPayload(false))));
	}

	@Test
	void farmAreasListsSixIntsAndAFlagPerArea() {
		byte[] bytes = roundTrip(FarmAreasPayload.CODEC, new FarmAreasPayload(List.of(new FarmAreasPayload.Area(1, 2, 3, 4, 5, 6, true))));
		assertEquals(1 + 6 * 4 + 1, bytes.length);
	}

	@Test
	void trackedFarmRoundTripsWithAndWithoutAFarm() {
		roundTrip(TrackedFarmPayload.CODEC, new TrackedFarmPayload("the_dream_table:sugar_cane", "My cane", "minecraft:overworld", new BlockPos(-5, 64, 9)));
		roundTrip(TrackedFarmPayload.CODEC, TrackedFarmPayload.NONE);
	}

	@Test
	void schematicFarmReplyCarriesTheFileVersion() {
		NbtCompound data = new NbtCompound();
		data.putString("Name", "x");
		roundTrip(ServerSchematicsPayload.Farm.CODEC, new ServerSchematicsPayload.Farm("nbt:a", 1_700_000_000_123L, data));
		roundTrip(ServerSchematicsPayload.Farm.CODEC, new ServerSchematicsPayload.Farm("saved:a#1", 0L, data));
	}

	@Test
	void placementPayloadsRoundTrip() {
		roundTrip(PlaceFarmPayload.CODEC, new PlaceFarmPayload(TABLE, new BlockPos(4, 5, 6), Direction.EAST, true));
		roundTrip(CheckPlacementPayload.CODEC, new CheckPlacementPayload(TABLE, new BlockPos(4, 5, 6), Direction.NORTH, true, false));
		roundTrip(PlacementResultPayload.CODEC, new PlacementResultPayload(TABLE, new BlockPos(4, 5, 6), Direction.SOUTH, true, "problem", 3));
	}

	@Test
	void farmActionPayloadsRoundTrip() {
		UUID id = UUID.fromString("20000000-0000-0000-0000-000000000002");
		roundTrip(FarmCarePayload.CODEC, new FarmCarePayload(TABLE, KEY, FarmCarePayload.Action.TAKE_DOWN));
		roundTrip(FarmRenamePayload.CODEC, new FarmRenamePayload(TABLE, KEY, "Iron farm"));
		roundTrip(FarmCollaboratorPayload.CODEC, new FarmCollaboratorPayload(TABLE, KEY, id));
		roundTrip(FarmInviteResponsePayload.CODEC, new FarmInviteResponsePayload(TABLE, KEY, id, true));
		roundTrip(FarmActionResultPayload.CODEC, new FarmActionResultPayload(KEY, FarmActionResultPayload.Action.KEEP_LOADED,
				FarmActionResultPayload.Result.LIMIT_REACHED));
	}

	private static FarmLoader.Entry entry(String layoutId) {
		return new FarmLoader.Entry(KEY, "nbt:big", layoutId, "", "minecraft:overworld", TABLE, FarmRole.OWNER, false, false, false, false, 0, 0, true, 0);
	}

	@Test
	void layoutIdsUpToTheOldLimitKeepTheirBytesAndLongerOnesNowFit() {
		String old = "a".repeat(256);
		RegistryByteBuf expected = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
		expected.writeString(old, 256);
		RegistryByteBuf actual = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
		actual.writeString(old, LoadedFarmsPayload.MAX_ID);
		assertEquals(ByteBufUtil.hexDump(expected), ByteBufUtil.hexDump(actual));

		roundTrip(LoadedFarmsPayload.CODEC, new LoadedFarmsPayload(List.of(entry(old)), List.of(), 3));
		roundTrip(LoadedFarmsPayload.CODEC, new LoadedFarmsPayload(List.of(entry("saved:nbt:" + "b".repeat(300) + "@" + UUID.randomUUID())), List.of(), 3));
	}

	@Test
	void sessionSyncCarriesTheSessionCompound() {
		NbtCompound session = new NbtCompound();
		session.putInt("Page", 2);
		roundTrip(SessionSyncPayload.CODEC, new SessionSyncPayload(TABLE, session));
	}
}
