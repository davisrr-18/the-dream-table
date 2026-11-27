package com.davisr.thedreamtable.farm.capture;

import com.davisr.thedreamtable.farm.ImportNote;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Why a capture was refused, with the number found and the limit it broke, when it has them. {@code note} is set for
 * {@link Kind#REJECTED}: the importer's own reason. Shared by the server and the selection screen.
 */
public record CaptureProblem(Kind kind, long found, long limit, ImportNote note) {
	public enum Kind {
		NOT_ALLOWED,
		TOO_FAR,
		TOO_WIDE,
		TOO_TALL,
		OUT_OF_WORLD,
		TOO_BIG,
		TOO_MANY_BLOCKS,
		EMPTY,
		UNLOADED,
		BAD_NAME,
		EXISTS,
		WRITE_FAILED,
		/** The importer would not list the file, for the reason in {@code note}. */
		REJECTED
	}

	public CaptureProblem(Kind kind, long found, long limit) {
		this(kind, found, limit, null);
	}

	public static CaptureProblem of(Kind kind) {
		return new CaptureProblem(kind, 0, 0, null);
	}

	public static CaptureProblem rejected(ImportNote note) {
		return new CaptureProblem(Kind.REJECTED, 0, 0, note);
	}

	public Text text() {
		if (note != null) {
			return Text.translatable("gui.the_dream_table.capture_problem.rejected", note.text());
		}
		return Text.translatable("gui.the_dream_table.capture_problem." + kind.name().toLowerCase(Locale.ROOT), found, limit);
	}
}
