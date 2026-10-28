package com.davisr.dreamtablepoc.fabric;

import com.davisr.dreamtablepoc.DreamTablePoc;
import net.fabricmc.api.ModInitializer;

public final class DreamTablePocFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		DreamTablePoc.init();
	}
}
