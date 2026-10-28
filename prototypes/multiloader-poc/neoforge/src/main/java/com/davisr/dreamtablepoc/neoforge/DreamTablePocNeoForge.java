package com.davisr.dreamtablepoc.neoforge;

import com.davisr.dreamtablepoc.DreamTablePoc;
import com.davisr.dreamtablepoc.client.DreamTablePocClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(DreamTablePoc.MOD_ID)
public final class DreamTablePocNeoForge {
	public DreamTablePocNeoForge(Dist dist) {
		DreamTablePoc.init();
		if (dist.isClient()) {
			DreamTablePocClient.init();
		}
	}
}
