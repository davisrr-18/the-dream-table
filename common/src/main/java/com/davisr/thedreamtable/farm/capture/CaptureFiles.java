package com.davisr.thedreamtable.farm.capture;

import com.davisr.thedreamtable.TheDreamTableMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Writes a captured structure into the structures folder: only inside it, never over an existing file, and never half written. */
public final class CaptureFiles {
	public static final String EXTENSION = ".nbt";
	/** The extensions the importer reads: a capture whose name is taken in any of them would be hidden or confused with it. */
	private static final Set<String> IMPORTED = Set.of(".nbt", ".schem", ".litematic");

	private CaptureFiles() {
	}

	/** The file for a name, or empty when the path would not be directly inside the folder. The caller passes a name {@link CaptureName#sanitize} made, but the path is checked anyway. */
	public static Optional<Path> target(Path folder, String name) {
		Path root = folder.toAbsolutePath().normalize();
		Path file = root.resolve(name + EXTENSION).normalize();
		return root.equals(file.getParent()) ? Optional.of(file) : Optional.empty();
	}

	/** The file name written, or the problem. */
	public record Saved(CaptureProblem problem, String fileName) {
	}

	/**
	 * Writes the data as a compressed {@code .nbt} named {@code name} to a temporary file in the folder, then links it into place,
	 * which is atomic and fails when the name appeared meanwhile, so a reader never sees a partial file and a file is never replaced
	 * (not by this server, not by another program). {@code otherFolders} are the other folders the importer reads: a name taken
	 * in any of them, in any extension it reads and any letter case, is refused, because the importer lists the first file of a name
	 * and would hide the capture. The folder is created when missing.
	 */
	public static Saved save(Path folder, List<Path> otherFolders, String name, NbtCompound data) {
		Path target = target(folder, name).orElse(null);
		if (target == null) {
			return failed(CaptureProblem.Kind.BAD_NAME);
		}
		Path parent = target.getParent();
		try {
			Files.createDirectories(parent);
		} catch (IOException | RuntimeException failure) {
			// A regular file named structures is a failure of the folder, not a structure that exists.
			TheDreamTableMod.LOGGER.warn("Could not create the structures folder {}", parent, failure);
			return failed(CaptureProblem.Kind.WRITE_FAILED);
		}
		Path temp = null;
		try {
			if (taken(parent, name)) {
				return failed(CaptureProblem.Kind.EXISTS);
			}
			for (Path other : otherFolders) {
				if (!other.toAbsolutePath().normalize().equals(parent) && taken(other, name)) {
					return failed(CaptureProblem.Kind.EXISTS);
				}
			}
			temp = Files.createTempFile(parent, "capture-", ".tmp");
			NbtIo.writeCompressed(data, temp);
			place(temp, target);
			return new Saved(null, target.getFileName().toString());
		} catch (FileAlreadyExistsException appeared) {
			return failed(CaptureProblem.Kind.EXISTS);
		} catch (IOException | RuntimeException failure) {
			TheDreamTableMod.LOGGER.warn("Could not write capture {}", target, failure);
			return failed(CaptureProblem.Kind.WRITE_FAILED);
		} finally {
			if (temp != null) {
				try {
					Files.deleteIfExists(temp);
				} catch (IOException ignored) {
					// A leftover capture-*.tmp is not a structure file, so nothing lists it.
				}
			}
		}
	}

	/** A hard link fails when the target exists, whoever made it. A file system without links gets a plain move, which refuses an existing target too, but with a small gap. */
	private static void place(Path temp, Path target) throws IOException {
		try {
			Files.createLink(target, temp);
		} catch (FileAlreadyExistsException exists) {
			throw exists;
		} catch (UnsupportedOperationException | FileSystemException noLinks) {
			Files.move(temp, target);
		}
	}

	private static Saved failed(CaptureProblem.Kind kind) {
		return new Saved(CaptureProblem.of(kind), "");
	}

	/** Whether the folder holds a file of that name in an extension the importer reads, ignoring letter case (a case-insensitive file system treats them as one). A missing folder holds nothing. */
	private static boolean taken(Path folder, String name) {
		if (!Files.isDirectory(folder)) {
			return false;
		}
		String wanted = name.toLowerCase(Locale.ROOT);
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
			for (Path entry : entries) {
				String file = entry.getFileName().toString().toLowerCase(Locale.ROOT);
				for (String extension : IMPORTED) {
					if (file.equals(wanted + extension)) {
						return true;
					}
				}
			}
		} catch (IOException unreadable) {
			// An unlistable folder holds nothing the importer could read either.
			return false;
		}
		return false;
	}
}
