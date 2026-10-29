package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

/**
 * A short message for the table notice shown at the top of the screen. The server sends it where it used to use the
 * action bar; a client that cannot receive it gets the action bar line as before.
 */
public record TableNoticePayload(Text text, Severity severity) implements CustomPayload {
	public static final Id<TableNoticePayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "table_notice"));
	public static final PacketCodec<RegistryByteBuf, TableNoticePayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, payload.text());
				buf.writeEnumConstant(payload.severity());
			},
			buf -> new TableNoticePayload(TextCodecs.REGISTRY_PACKET_CODEC.decode(buf), buf.readEnumConstant(Severity.class)));

	/** INFO is plain information or success; PROBLEM is a refusal, warning or alert and is drawn in red. */
	public enum Severity {
		INFO, PROBLEM
	}

	public static void register() {
		Platform.network().registerServerToClient(ID, CODEC);
	}

	/** Shows the notice to the player, or falls back to the action bar when their client cannot receive it. */
	public static void send(ServerPlayerEntity player, Text text, Severity severity) {
		if (Platform.network().canSend(player, ID)) {
			Platform.network().send(player, new TableNoticePayload(text, severity));
		} else {
			player.sendMessage(text, true);
		}
	}

	public static void info(ServerPlayerEntity player, Text text) {
		send(player, text, Severity.INFO);
	}

	public static void problem(ServerPlayerEntity player, Text text) {
		send(player, text, Severity.PROBLEM);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
