package com.davisr.dreamtablepoc.fabric;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Blocks;
import org.junit.jupiter.api.Test;

class BootstrapSmokeTest {
	@Test
	void bootstrapsMinecraft() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		assertNotNull(Blocks.STONE);
	}
}
