package com.davisr.thedreamtable.farm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;

class FarmLoaderSelectionTest {
	@Test
	void aStableKeyStillTargetsTheSameFarmAfterTheListChanges() {
		FarmLoader.Located selected = new FarmLoader.Located("overworld|selected", null, null);
		FarmLoader.Located addedLater = new FarmLoader.Located("overworld|newer", null, null);

		assertSame(selected, FarmLoader.findByKey(List.of(addedLater, selected), selected.key()).orElseThrow());
	}
}
