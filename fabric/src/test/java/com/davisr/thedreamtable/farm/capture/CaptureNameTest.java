package com.davisr.thedreamtable.farm.capture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CaptureNameTest {
	@Test
	void plainNamesStayAndSpacesBecomeUnderscores() {
		assertEquals("my_farm-2", CaptureName.sanitize("My Farm-2"));
		assertEquals("a", CaptureName.sanitize("  A  ".trim()));
	}

	@Test
	void pathCharactersAreDroppedSoNoNameCanLeaveTheFolder() {
		assertEquals("x", CaptureName.sanitize("../x"));
		assertEquals("ab", CaptureName.sanitize("a/b"));
		assertEquals("x", CaptureName.sanitize("..\\..\\x"));
		assertEquals("cwindows", CaptureName.sanitize("C:\\windows"));
		assertEquals("farmnbt", CaptureName.sanitize("farm.nbt"));
		assertNull(CaptureName.sanitize(".."));
		assertNull(CaptureName.sanitize("/"));
	}

	@Test
	void accentsFoldAndOtherScriptsLeaveNothing() {
		assertEquals("acao_arvore", CaptureName.sanitize("Ação Árvore"));
		assertNull(CaptureName.sanitize("日本語"));
		assertNull(CaptureName.sanitize("\u202e\u200b"));
	}

	@Test
	void emptyBlankAndReservedNamesAreRefused() {
		assertNull(CaptureName.sanitize(null));
		assertNull(CaptureName.sanitize(""));
		assertNull(CaptureName.sanitize("   "));
		assertNull(CaptureName.sanitize("CON"));
		assertNull(CaptureName.sanitize("con"));
		assertNull(CaptureName.sanitize("Lpt1"));
		assertEquals("console", CaptureName.sanitize("console"));
	}

	@Test
	void theLengthLimitIsSixtyFourCharacters() {
		assertEquals("a".repeat(64), CaptureName.sanitize("a".repeat(64)));
		assertNull(CaptureName.sanitize("a".repeat(65)));
	}
}
