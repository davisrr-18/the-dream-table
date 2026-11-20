package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.platform.ModInfo;
import net.minecraftforge.fml.ModList;

final class ForgeModInfo implements ModInfo {
	@Override
	public String loader() {
		return "forge";
	}

	@Override
	public String version() {
		return ModList.get().getModContainerById(TheDreamTableMod.MOD_ID)
				.map(container -> container.getModInfo().getVersion().toString())
				.orElse("unknown");
	}
}
