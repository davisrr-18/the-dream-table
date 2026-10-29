package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.ImportNote;
import com.davisr.thedreamtable.farm.ImportedStructures;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The schematic list a table open sends must always encode: one crafted file can't keep the screen from opening. */
class ServerSchematicsPayloadTest {
	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	private static ServerSchematicsPayload.Entry entry(String name, List<ImportNote> notes) {
		return new ServerSchematicsPayload.Entry("nbt:a", "minecraft:stone", name, 3, 1L, notes);
	}

	private static ServerSchematicsPayload roundTrip(ServerSchematicsPayload payload) {
		RegistryByteBuf out = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
		ServerSchematicsPayload.CODEC.encode(out, payload);
		byte[] bytes = new byte[out.readableBytes()];
		out.readBytes(bytes);
		RegistryByteBuf in = new RegistryByteBuf(Unpooled.wrappedBuffer(bytes), DynamicRegistryManager.EMPTY);
		ServerSchematicsPayload read = ServerSchematicsPayload.CODEC.decode(in);
		assertEquals(0, in.readableBytes());
		return read;
	}

	@Test
	void notesWithHugeNamesDoNotKeepTheListFromEncoding() {
		ImportNote hostile = new ImportNote(ImportNote.Kind.UNKNOWN_BLOCKS, 4, List.of("x".repeat(70_000), "y".repeat(40_000)));
		ServerSchematicsPayload payload = new ServerSchematicsPayload(List.of(entry("Farm", List.of(hostile))),
				List.of(new ImportedStructures.Rejected("Broken", ImportNote.of(ImportNote.Kind.UNREADABLE))));

		ServerSchematicsPayload sent = ServerSchematicsPayload.encodable(payload.schematics(), payload.rejected(), DynamicRegistryManager.EMPTY);

		assertEquals(1, sent.schematics().get(0).notes().size());
		ServerSchematicsPayload read = roundTrip(sent);
		assertEquals(ImportNote.MAX_NAME, read.schematics().get(0).notes().get(0).names().get(0).length());
		assertEquals(1, read.rejected().size());
	}

	@Test
	void aListThatCannotEncodeFallsBackToAnEmptyOneInsteadOfThrowing() {
		// A name over the string limit is not a note, so no fallback to "no notes" can save it.
		ServerSchematicsPayload sent = ServerSchematicsPayload.encodable(List.of(entry("n".repeat(40_000), List.of())), List.of(), DynamicRegistryManager.EMPTY);

		assertTrue(sent.schematics().isEmpty());
		assertTrue(sent.rejected().isEmpty());
	}

	@Test
	void aListThatEncodesIsSentAsItIs() {
		List<ServerSchematicsPayload.Entry> entries = List.of(entry("Farm", List.of(ImportNote.of(ImportNote.Kind.TABLE_BLOCKS, 2))));
		ServerSchematicsPayload sent = ServerSchematicsPayload.encodable(entries, List.of(), DynamicRegistryManager.EMPTY);
		assertSame(entries, sent.schematics());
	}
}
