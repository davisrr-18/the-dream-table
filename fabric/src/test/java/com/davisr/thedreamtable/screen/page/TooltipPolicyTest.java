package com.davisr.thedreamtable.screen.page;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Cards, buttons and chips show their own text (scrolling when long), so pages must not draw a tooltip repeating it.
 * Only controls without visible text, such as the icon-only compact tabs and item slots, may have one.
 */
class TooltipPolicyTest {
	@Test
	void pageContextOffersNoTooltipForLabeledControls() {
		for (var method : PageContext.class.getDeclaredMethods()) {
			assertFalse(method.getName().toLowerCase().contains("tooltip"), "PageContext." + method.getName());
		}
	}

	@Test
	void noPageDrawsATooltip() throws IOException {
		Path pages = pagesFolder();
		List<String> offenders = new ArrayList<>();
		try (Stream<Path> files = Files.list(pages)) {
			for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
				if (Files.readString(file).toLowerCase().contains("tooltip")) {
					offenders.add(file.getFileName().toString());
				}
			}
		}
		assertEquals(List.of(), offenders, "pages with tooltip code");
	}

	/** The tests run in {@code fabric/build/test-run}, so look for the repository root upwards from there. */
	private static Path pagesFolder() {
		Path relative = Path.of("common", "src", "main", "java", "com", "davisr", "thedreamtable", "screen", "page");
		for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
			if (Files.isDirectory(dir.resolve(relative))) {
				return dir.resolve(relative);
			}
		}
		throw new IllegalStateException("page sources not found");
	}
}
