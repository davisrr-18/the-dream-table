package com.davisr.thedreamtable.neoforge;

import com.davisr.thedreamtable.platform.ClientEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.function.Consumer;

/** NeoForge's client tick, connection, input and resource events. Only loaded by the client setup. */
final class NeoForgeClientEvents implements ClientEvents {
	private final IEventBus modBus;

	NeoForgeClientEvents(IEventBus modBus) {
		this.modBus = modBus;
	}

	@Override
	public void onTickEnd(Consumer<MinecraftClient> listener) {
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> listener.accept(MinecraftClient.getInstance()));
	}

	@Override
	public void onJoin(Consumer<MinecraftClient> listener) {
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> listener.accept(MinecraftClient.getInstance()));
	}

	@Override
	public void onDisconnect(Consumer<MinecraftClient> listener) {
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> listener.accept(MinecraftClient.getInstance()));
	}

	@Override
	public void onUseBlock(UseBlockListener listener) {
		NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, event -> {
			if (event.isCanceled()) {
				return;
			}
			ActionResult result = listener.use(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
			if (result != ActionResult.PASS) {
				event.setCanceled(true);
				event.setCancellationResult(result);
			}
		});
	}

	@Override
	public void onUseItem(UseItemListener listener) {
		NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem.class, event -> {
			if (event.isCanceled()) {
				return;
			}
			TypedActionResult<ItemStack> result = listener.use(event.getEntity(), event.getLevel(), event.getHand());
			if (result.getResult() != ActionResult.PASS) {
				event.setCanceled(true);
				event.setCancellationResult(result.getResult());
			}
		});
	}

	@Override
	public KeyBinding registerKeyBinding(KeyBinding key) {
		modBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(key));
		return key;
	}

	@Override
	public void onResourcesReloaded(Identifier id, Consumer<ResourceManager> listener) {
		modBus.addListener(RegisterClientReloadListenersEvent.class,
				event -> event.registerReloadListener((SynchronousResourceReloader) listener::accept));
	}
}
