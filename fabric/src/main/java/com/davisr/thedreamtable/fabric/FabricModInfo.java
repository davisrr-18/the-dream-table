package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.platform.ModInfo;

final class FabricModInfo implements ModInfo {
	@Override
	public String loader() {
		return "fabric";
	}

	@Override
	public String version() {
		return FabricHandshake.modVersion();
	}
}
