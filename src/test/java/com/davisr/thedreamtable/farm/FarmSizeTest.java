package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.farm.schematic.SchematicReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarmSizeTest {
	@Test
	void limitsComeFromTheSchematicLimit() {
		assertEquals(512, FarmSize.TINY_MAX);
		assertEquals(4096, FarmSize.MEDIUM_MAX);
	}

	@Test
	void sizesSplitAtTheLimits() {
		assertEquals(FarmSize.TINY, FarmSize.of(1));
		assertEquals(FarmSize.TINY, FarmSize.of(512));
		assertEquals(FarmSize.MEDIUM, FarmSize.of(513));
		assertEquals(FarmSize.MEDIUM, FarmSize.of(2505));
		assertEquals(FarmSize.MEDIUM, FarmSize.of(4096));
		assertEquals(FarmSize.BIG, FarmSize.of(4097));
		assertEquals(FarmSize.BIG, FarmSize.of(SchematicReader.MAX_BLOCKS));
	}
}
