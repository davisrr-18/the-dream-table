package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.platform.ServerNetwork;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A client that registered the notice payload gets it, with the severity the server chose. */
class TableNoticePayloadTest {
	private static final List<CustomPayload> SENT = new ArrayList<>();

	@BeforeAll
	static void install() {
		GameSetup.bootstrap();
		Platform.install(null, null, null, new ServerNetwork() {
			@Override
			public <T extends CustomPayload> void registerServerToClient(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec) {
			}

			@Override
			public <T extends CustomPayload> void registerClientToServer(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec, Handler<T> handler) {
			}

			@Override
			public void send(ServerPlayerEntity player, CustomPayload payload) {
				SENT.add(payload);
			}

			@Override
			public boolean canSend(ServerPlayerEntity player, CustomPayload.Id<?> id) {
				return id.equals(TableNoticePayload.ID);
			}
		}, null, null, null);
	}

	@AfterAll
	static void uninstall() {
		Platform.install(null, null, null, null, null, null, null);
	}

	@Test
	void problemsAndInfoTravelWithTheirSeverity() {
		Text text = Text.translatable("gui.the_dream_table.delivered", 3);
		TableNoticePayload.problem(null, text);
		TableNoticePayload.info(null, text);

		assertEquals(List.of(new TableNoticePayload(text, TableNoticePayload.Severity.PROBLEM),
				new TableNoticePayload(text, TableNoticePayload.Severity.INFO)), SENT);
	}
}
