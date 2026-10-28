package com.davisr.dreamtablepoc.network;

import com.davisr.dreamtablepoc.CounterMenu;
import com.davisr.dreamtablepoc.CounterState;
import com.davisr.dreamtablepoc.PocContent;
import com.davisr.dreamtablepoc.PocRules;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class PocNetworking {
	private PocNetworking() {
	}

	public static void registerCommon() {
		NetworkManager.registerReceiver(NetworkManager.Side.C2S, IncrementRequestPayload.ID, IncrementRequestPayload.CODEC,
				(payload, context) -> context.queue(() -> {
					if (context.getPlayer() instanceof ServerPlayerEntity player) {
						handleIncrement(player, payload.pos());
					}
				}));

		// Dedicated servers have no client receiver, so the S2C type must be declared explicitly.
		if (Platform.getEnvironment() == Env.SERVER) {
			NetworkManager.registerS2CPayloadType(CounterSyncPayload.ID, CounterSyncPayload.CODEC);
		}
	}

	private static void handleIncrement(ServerPlayerEntity player, BlockPos pos) {
		ServerWorld world = player.getServerWorld();
		boolean chunkLoaded = world.isChunkLoaded(pos);
		boolean isCounterBlock = chunkLoaded && world.getBlockState(pos).isOf(PocContent.COUNTER_BLOCK.get());
		boolean menuOpen = player.currentScreenHandler instanceof CounterMenu menu && menu.pos().equals(pos);
		boolean withinReach = player.canInteractWithBlockAt(pos, 1.0);
		if (!PocRules.canIncrement(chunkLoaded, isCounterBlock, menuOpen, player.isSpectator(), withinReach)) {
			return;
		}

		int value = CounterState.get(world.getServer()).increment();
		NetworkManager.sendToPlayer(player, new CounterSyncPayload(value));
	}
}
