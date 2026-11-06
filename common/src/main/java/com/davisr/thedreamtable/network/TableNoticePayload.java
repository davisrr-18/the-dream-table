package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.network.codec.RegistryByteBuf;
import com.davisr.thedreamtable.network.codec.PacketCodec;
import com.davisr.thedreamtable.network.codec.PacketCodecs;
import com.davisr.thedreamtable.network.codec.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * A short message for the table notice shown at the top of the screen. The server sends it where it used to use the
 * action bar; a client that cannot receive it gets the action bar line as before.
 */
public record TableNoticePayload(Text text, Severity severity) implements CustomPayload {
	public static final Id<TableNoticePayload> ID = new Id<>(new Identifier(TheDreamTableMod.MOD_ID, "table_notice"));
	public static final PacketCodec<RegistryByteBuf, TableNoticePayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				PacketCodecs.TEXT.encode(buf, payload.text());
				buf.writeEnumConstant(payload.severity());
			},
			buf -> new TableNoticePayload(PacketCodecs.TEXT.decode(buf), buf.readEnumConstant(Severity.class)));

	/** INFO is plain information or success; PROBLEM is a refusal, warning or alert and is drawn in red. */
	public enum Severity {
		INFO, PROBLEM
	}

	public static void register() {
		Platform.network().registerServerToClient(ID, CODEC);
	}

	/** Shows the notice to the player, or falls back to the action bar when their client cannot receive it. */
	public static void send(ServerPlayerEntity player, Text text, Severity severity) {
		// A fake player (a mod's bot) has no connection: there is nobody to show the notice to, and the action bar fallback would fail on it.
		if (player != null && player.networkHandler == null) {
			return;
		}
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
