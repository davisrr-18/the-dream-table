package com.davisr.thedreamtable.farm.capture;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureFilesTest {
	@TempDir
	Path temp;

	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	private static NbtCompound data(String marker) {
		NbtCompound root = new NbtCompound();
		root.putString("marker", marker);
		return root;
	}

	@Test
	void theTargetIsAlwaysDirectlyInsideTheFolder() {
		Path folder = temp.resolve("structures");
		assertTrue(CaptureFiles.target(folder, "farm").isPresent());
		assertEquals(folder.toAbsolutePath().normalize().resolve("farm.nbt"), CaptureFiles.target(folder, "farm").orElseThrow());
		assertTrue(CaptureFiles.target(folder, "../x").isEmpty());
		assertTrue(CaptureFiles.target(folder, "a/b").isEmpty());
		assertTrue(CaptureFiles.target(folder, "../../etc/passwd").isEmpty());
	}

	@Test
	void aTraversingNameWritesNothingOutsideTheFolder() throws IOException {
		Path folder = temp.resolve("structures");
		CaptureFiles.Saved saved = CaptureFiles.save(folder, List.of(), "../escaped", data("x"));
		assertEquals(CaptureProblem.Kind.BAD_NAME, saved.problem().kind());
		assertFalse(Files.exists(temp.resolve("escaped.nbt")));
		assertFalse(Files.exists(folder.resolve("escaped.nbt")));
	}

	@Test
	void savesAReadableCompressedFileAndCreatesTheFolder() throws IOException {
		Path folder = temp.resolve("config").resolve("structures");
		CaptureFiles.Saved saved = CaptureFiles.save(folder, List.of(), "farm", data("first"));
		assertNull(saved.problem());
		assertEquals("farm.nbt", saved.fileName());
		assertEquals("first", NbtIo.readCompressed(folder.resolve("farm.nbt").toFile()).getString("marker"));
		try (Stream<Path> files = Files.list(folder)) {
			assertEquals(List.of("farm.nbt"), files.map(path -> path.getFileName().toString()).toList(), "no temporary file is left");
		}
	}

	@Test
	void anExistingFileIsNeverReplacedWhateverItsLetterCase() throws IOException {
		Path folder = temp.resolve("structures");
		assertNull(CaptureFiles.save(folder, List.of(), "farm", data("first")).problem());
		assertEquals(CaptureProblem.Kind.EXISTS, CaptureFiles.save(folder, List.of(), "farm", data("second")).problem().kind());
		Files.writeString(folder.resolve("Big.NBT"), "mine");
		assertEquals(CaptureProblem.Kind.EXISTS, CaptureFiles.save(folder, List.of(), "big", data("third")).problem().kind());
		assertEquals("mine", Files.readString(folder.resolve("Big.NBT")));
		assertEquals("first", NbtIo.readCompressed(folder.resolve("farm.nbt").toFile()).getString("marker"));
		try (Stream<Path> files = Files.list(folder)) {
			assertEquals(2, files.count(), "a refused save leaves no temporary file");
		}
	}

	@Test
	void aNameTakenInAnotherImporterFolderIsRefusedInAnyReadExtension() throws IOException {
		Path own = temp.resolve("structures");
		Path schematics = Files.createDirectories(temp.resolve("schematics"));
		Files.writeString(schematics.resolve("Tower.SCHEM"), "theirs");
		Files.writeString(schematics.resolve("pit.litematic"), "theirs");
		Files.writeString(schematics.resolve("notes.txt"), "not a structure");
		List<Path> others = List.of(schematics, temp.resolve("missing"), own);
		assertEquals(CaptureProblem.Kind.EXISTS, CaptureFiles.save(own, others, "tower", data("x")).problem().kind());
		assertEquals(CaptureProblem.Kind.EXISTS, CaptureFiles.save(own, others, "pit", data("x")).problem().kind());
		assertNull(CaptureFiles.save(own, others, "notes", data("x")).problem(), "a file the importer does not read hides nothing");
		assertFalse(Files.exists(own.resolve("tower.nbt")));
	}

	@Test
	void aRegularFileInPlaceOfTheFolderIsAWriteFailureNotAnExistingStructure() throws IOException {
		Path notAFolder = temp.resolve("structures");
		Files.writeString(notAFolder, "i am a file");
		assertEquals(CaptureProblem.Kind.WRITE_FAILED, CaptureFiles.save(notAFolder, List.of(), "farm", data("x")).problem().kind());
		assertEquals("i am a file", Files.readString(notAFolder));
	}
}
