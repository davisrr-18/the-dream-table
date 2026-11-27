package com.davisr.thedreamtable.farm.capture;

import com.davisr.thedreamtable.farm.ImportNote;
import com.davisr.thedreamtable.farm.schematic.SchematicReader;
import net.minecraft.nbt.NbtCompound;

import java.nio.file.Path;
import java.util.List;

/**
 * Captures a box into a structure file, in the cheap-to-costly order: the name, the box's size, the blocks, the importer's
 * verdict, the file. It runs on the server thread, because the files are small (at most {@link CaptureBox#MAX_BLOCKS} blocks)
 * and the world is read there anyway; the structure list is then refreshed on the importer's worker.
 */
public final class AreaCapture {
	private AreaCapture() {
	}

	public static CaptureFiles.Saved run(CaptureBox box, String typedName, int bottomY, int topY, int dataVersion,
			CaptureScan.Chunks chunks, Path folder, List<Path> otherFolders) {
		String name = CaptureName.sanitize(typedName);
		if (name == null) {
			return failed(CaptureProblem.of(CaptureProblem.Kind.BAD_NAME));
		}
		CaptureProblem size = box.sizeProblem(bottomY, topY);
		if (size != null) {
			return failed(size);
		}
		CaptureScan.Result scan = CaptureScan.read(box, chunks);
		if (scan.problem() != null) {
			return failed(scan.problem());
		}
		NbtCompound data = CaptureStructure.toNbt(box, scan.blocks(), dataVersion);
		CaptureProblem rejected = importProblem(data);
		if (rejected != null) {
			return failed(rejected);
		}
		return CaptureFiles.save(folder, otherFolders, name, data);
	}

	/**
	 * What the importer would say to this data, read once in memory by its real path ({@link SchematicReader#fromRoot}, as a player's
	 * file): null when it would list the file, otherwise its own reason, such as needing more materials than a table holds.
	 */
	public static CaptureProblem importProblem(NbtCompound data) {
		SchematicReader.Result result = SchematicReader.fromRoot("nbt:capture", data, true);
		if (result.template() != null) {
			return null;
		}
		return CaptureProblem.rejected(result.notes().isEmpty() ? ImportNote.of(ImportNote.Kind.UNREADABLE) : result.notes().get(0));
	}

	private static CaptureFiles.Saved failed(CaptureProblem problem) {
		return new CaptureFiles.Saved(problem, "");
	}
}
