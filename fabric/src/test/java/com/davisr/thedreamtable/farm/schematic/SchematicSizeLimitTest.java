package com.davisr.thedreamtable.farm.schematic;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.ImportNote;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class SchematicSizeLimitTest {
	@TempDir
	Path folder;

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	@Test
	void aFileOverTheByteLimitIsRejectedWithoutReadingIt() throws IOException {
		Path file = folder.resolve("huge.nbt");
		try (RandomAccessFile sparse = new RandomAccessFile(file.toFile(), "rw")) {
			sparse.setLength(SchematicReader.MAX_BYTES + 1);
		}

		SchematicReader.Result result = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> SchematicReader.read("nbt:huge", file));

		assertNull(result.template());
		assertEquals(1, result.notes().size());
		assertEquals(ImportNote.Kind.TOO_LARGE, result.notes().get(0).kind());
	}
}
