package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.client.DreamTableHologram;
import com.davisr.thedreamtable.client.LoadedFarmsView;
import com.davisr.thedreamtable.client.OtherFarms;
import com.davisr.thedreamtable.client.OwnSessions;
import com.davisr.thedreamtable.client.ServerSchematics;
import com.davisr.thedreamtable.client.TrackedFarmHud;
import com.davisr.thedreamtable.platform.ClientEvents;
import com.davisr.thedreamtable.platform.ClientNetwork;
import com.davisr.thedreamtable.platform.ClientRendering;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.platform.ServerEvents;
import com.davisr.thedreamtable.platform.ServerNetwork;
import com.davisr.thedreamtable.screen.OpenData;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The payloads {@code TheDreamTableMod.init()} registers, by direction. A payload registered twice, in the wrong
 * direction, or not at all breaks the connection between a client and a server that disagree about it.
 */
class PayloadRegistrationTest {
	private static final List<String> SERVER_TO_CLIENT = new ArrayList<>();
	private static final List<String> CLIENT_TO_SERVER = new ArrayList<>();
	private static final List<String> CLIENT_RECEIVERS = new ArrayList<>();

	@BeforeAll
	static void registerPayloads() {
		GameSetup.bootstrap();
		Platform.install(null, null, null, new RecordingNetwork(), new IgnoringServerEvents(), null, null);
		// The payload registrations of TheDreamTableMod.init(), in its order.
		DepositMaterialPayload.register();
		PlaceFarmPayload.register();
		CheckPlacementPayload.register();
		PlacementResultPayload.register();
		SessionSyncPayload.register();
		RequestSessionPayload.register();
		FarmAreasPayload.register();
		FarmActionResultPayload.register();
		FarmAlertsPayload.register();
		FarmAlertAckPayload.register();
		FarmRenamePayload.register();
		FarmInvitePayload.register();
		FarmInviteResponsePayload.register();
		FarmCollaboratorPayload.register();
		FarmPreferencePayload.register();
		FarmCarePayload.register();
		TrackedFarmPayload.register();
		LoadedFarmsPayload.register();
		ServerSchematicsPayload.register();
		FarmDetailsPayload.register();
		// The client registrations of TheDreamTableClient.init() that add payload receivers.
		Platform.installClient(new RecordingClientNetwork(), stub(ClientEvents.class), stub(ClientRendering.class));
		DreamTableHologram.registerChecks();
		OwnSessions.register();
		OtherFarms.register();
		LoadedFarmsView.register();
		TrackedFarmHud.register();
		ServerSchematics.register();
	}

	@AfterAll
	static void uninstall() {
		Platform.install(null, null, null, null, null, null, null);
		Platform.installClient(null, null, null);
	}

	@Test
	void everyIdIsRegisteredOnceInItsDirection() {
		assertEquals(Set.copyOf(SERVER_TO_CLIENT).size(), SERVER_TO_CLIENT.size(), "S2C ids must be unique: " + SERVER_TO_CLIENT);
		assertEquals(Set.copyOf(CLIENT_TO_SERVER).size(), CLIENT_TO_SERVER.size(), "C2S ids must be unique: " + CLIENT_TO_SERVER);
		assertEquals(new TreeSet<>(Set.of(
				"the_dream_table:check_placement",
				"the_dream_table:deposit_material",
				"the_dream_table:farm_alert_ack",
				"the_dream_table:farm_alerts_request",
				"the_dream_table:farm_care",
				"the_dream_table:farm_collaborator",
				"the_dream_table:farm_details_request",
				"the_dream_table:farm_invite",
				"the_dream_table:farm_invite_response",
				"the_dream_table:farm_preference",
				"the_dream_table:farm_rename",
				"the_dream_table:loaded_farms_request",
				"the_dream_table:place_farm",
				"the_dream_table:request_session",
				"the_dream_table:schematic_request",
				"the_dream_table:watch_areas")), new TreeSet<>(CLIENT_TO_SERVER));
		assertEquals(new TreeSet<>(Set.of(
				"the_dream_table:farm_action_result",
				"the_dream_table:farm_alerts",
				"the_dream_table:farm_areas",
				"the_dream_table:farm_details",
				"the_dream_table:loaded_farms",
				"the_dream_table:placement_result",
				"the_dream_table:schematic_farm",
				"the_dream_table:server_schematics",
				"the_dream_table:session_sync",
				"the_dream_table:tracked_farm")), new TreeSet<>(SERVER_TO_CLIENT));
	}

	/** NeoForge drops a payload with no client receiver, so each server-to-client id needs exactly one. */
	@Test
	void everyServerToClientPayloadHasOneClientReceiver() {
		assertEquals(Set.copyOf(CLIENT_RECEIVERS).size(), CLIENT_RECEIVERS.size(), "Client receivers must be unique: " + CLIENT_RECEIVERS);
		assertEquals(new TreeSet<>(SERVER_TO_CLIENT), new TreeSet<>(CLIENT_RECEIVERS));
	}

	@Test
	void directionsHoldTheExpectedCounts() {
		assertEquals(16, CLIENT_TO_SERVER.size());
		assertEquals(10, SERVER_TO_CLIENT.size());
	}

	@Test
	void menuOpeningDataKeepsItsLayout() {
		OpenData data = new OpenData(new BlockPos(1, 2, 3), "x", 1, 2, 3);
		RegistryByteBuf out = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
		OpenData.CODEC.encode(out, data);
		assertEquals("00000040000030020178010203", ByteBufUtil.hexDump(ByteBufUtil.getBytes(out)));
		assertEquals(data, OpenData.CODEC.decode(new RegistryByteBuf(Unpooled.wrappedBuffer(ByteBufUtil.getBytes(out)), DynamicRegistryManager.EMPTY)));
	}

	/** A seam whose methods do nothing and return defaults, for the client hooks this test does not exercise. */
	private static <T> T stub(Class<T> seam) {
		return seam.cast(Proxy.newProxyInstance(seam.getClassLoader(), new Class<?>[] {seam}, (proxy, method, args) -> null));
	}

	private static final class RecordingClientNetwork implements ClientNetwork {
		@Override
		public <T extends CustomPayload> void registerReceiver(CustomPayload.Id<T> id, Consumer<T> receiver) {
			CLIENT_RECEIVERS.add(id.id().toString());
		}

		@Override
		public boolean canSend(CustomPayload.Id<?> id) {
			return false;
		}

		@Override
		public void send(CustomPayload payload) {
		}
	}

	private static final class RecordingNetwork implements ServerNetwork {
		@Override
		public <T extends CustomPayload> void registerServerToClient(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec) {
			SERVER_TO_CLIENT.add(id.id().toString());
		}

		@Override
		public <T extends CustomPayload> void registerClientToServer(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec, Handler<T> handler) {
			CLIENT_TO_SERVER.add(id.id().toString());
		}

		@Override
		public void send(ServerPlayerEntity player, CustomPayload payload) {
		}
	}

	private static final class IgnoringServerEvents implements ServerEvents {
		@Override
		public void onServerStarting(Consumer<MinecraftServer> listener) {
		}

		@Override
		public void onServerStarted(Consumer<MinecraftServer> listener) {
		}

		@Override
		public void onServerStopped(Consumer<MinecraftServer> listener) {
		}

		@Override
		public void onTickEnd(Consumer<MinecraftServer> listener) {
		}

		@Override
		public void onPlayerJoin(BiConsumer<ServerPlayerEntity, MinecraftServer> listener) {
		}

		@Override
		public void onPlayerDisconnect(BiConsumer<ServerPlayerEntity, MinecraftServer> listener) {
		}

		@Override
		public void onBlockBreak(BlockBreakCheck check) {
		}
	}
}
