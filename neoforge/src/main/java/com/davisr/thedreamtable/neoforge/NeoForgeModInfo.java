package com.davisr.thedreamtable.neoforge;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.platform.ModInfo;
import net.neoforged.fml.ModList;

final class NeoForgeModInfo implements ModInfo {
	@Override
	public String loader() {
		return "neoforge";
	}

	@Override
	public String version() {
		return ModList.get().getModContainerById(TheDreamTableMod.MOD_ID)
				.map(container -> container.getModInfo().getVersion().toString())
				.orElse("unknown");
	}
}
