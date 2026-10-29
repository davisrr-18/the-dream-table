package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.network.NetworkProtocol;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fabric's configuration-phase handshake: its wire format, ids and the disconnect message. */
class FabricHandshakeTest {
	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	private static <T extends CustomPayload> void roundTrip(PacketCodec<PacketByteBuf, T> codec, T payload) {
		PacketByteBuf out = new PacketByteBuf(Unpooled.buffer());
		codec.encode(out, payload);
		PacketByteBuf in = new PacketByteBuf(Unpooled.wrappedBuffer(ByteBufUtil.getBytes(out)));
		assertEquals(payload, codec.decode(in));
		assertEquals(0, in.readableBytes());
	}

	@Test
	void requestAndReplyReadBackWhatTheyWrote() {
		roundTrip(FabricHandshake.Request.CODEC, new FabricHandshake.Request(NetworkProtocol.VERSION, "1.1.0"));
		roundTrip(FabricHandshake.Reply.CODEC, new FabricHandshake.Reply(NetworkProtocol.VERSION, "1.1.0-beta"));
	}

	@Test
	void theWireFormatIsTwoStrings() {
		PacketByteBuf out = new PacketByteBuf(Unpooled.buffer());
		FabricHandshake.Reply.CODEC.encode(out, new FabricHandshake.Reply("2", "1.1.0"));
		// varint length plus UTF-8 bytes, twice
		assertArrayEquals(new byte[] {1, '2', 5, '1', '.', '1', '.', '0'}, ByteBufUtil.getBytes(out));
	}

	@Test
	void theChannelIdsAreFrozen() {
		assertEquals("the_dream_table:handshake", FabricHandshake.REQUEST_ID.id().toString());
		assertEquals("the_dream_table:handshake_reply", FabricHandshake.REPLY_ID.id().toString());
	}

	@Test
	void onlyTheSameProtocolIsCompatible() {
		assertTrue(NetworkProtocol.compatible(NetworkProtocol.VERSION));
		assertFalse(NetworkProtocol.compatible("1"));
		assertFalse(NetworkProtocol.compatible(""));
	}

	@Test
	void theDisconnectMessageIsTranslatableWithServerThenClientVersion() {
		TextContent content = FabricHandshake.mismatch("1.1.0", "1.0.0").getContent();
		TranslatableTextContent translatable = (TranslatableTextContent) content;
		assertEquals("gui.the_dream_table.version_mismatch", translatable.getKey());
		assertArrayEquals(new Object[] {"1.1.0", "1.0.0"}, translatable.getArgs());
	}
}
