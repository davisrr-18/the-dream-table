package com.davisr.dreamtablepoc.client;

import com.davisr.dreamtablepoc.network.IncrementRequestPayload;
import dev.architectury.networking.NetworkManager;
import net.minecraft.util.math.BlockPos;

/** Client-only calls reached from common code; never loaded on a dedicated server. */
public final class PocClientHooks {
	private PocClientHooks() {
	}

	public static void requestIncrement(BlockPos pos) {
		NetworkManager.sendToServer(new IncrementRequestPayload(pos));
	}
}
