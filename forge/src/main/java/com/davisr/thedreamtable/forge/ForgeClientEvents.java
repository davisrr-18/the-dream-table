package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.platform.ClientEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.function.Consumer;

/**
 * Forge's client tick, connection, input and resource events. Only loaded by the client setup. Forge 61 events carry
 * their own {@code BUS}; the registration events are no longer on a mod bus.
 */
final class ForgeClientEvents implements ClientEvents {
	@Override
	public void onTickEnd(Consumer<MinecraftClient> listener) {
		TickEvent.ClientTickEvent.Post.BUS.addListener(event -> listener.accept(MinecraftClient.getInstance()));
	}

	@Override
	public void onJoin(Consumer<MinecraftClient> listener) {
		ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(event -> listener.accept(MinecraftClient.getInstance()));
	}

	@Override
	public void onDisconnect(Consumer<MinecraftClient> listener) {
		ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> listener.accept(MinecraftClient.getInstance()));
	}

	@Override
	public void onUseBlock(UseBlockListener listener) {
		// A listener that returns true cancels the event; a canceled event never reaches the later listeners.
		PlayerInteractEvent.RightClickBlock.BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
			ActionResult result = listener.use(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
			if (result != ActionResult.PASS) {
				event.setCancellationResult(result);
				return true;
			}
			return false;
		});
	}

	@Override
	public void onUseItem(UseItemListener listener) {
		PlayerInteractEvent.RightClickItem.BUS.addListener((PlayerInteractEvent.RightClickItem event) -> {
			ActionResult result = listener.use(event.getEntity(), event.getLevel(), event.getHand());
			if (result != ActionResult.PASS) {
				event.setCancellationResult(result);
				return true;
			}
			return false;
		});
	}

	@Override
	public KeyBinding registerKeyBinding(KeyBinding key) {
		RegisterKeyMappingsEvent.BUS.addListener(event -> event.register(key));
		return key;
	}

	@Override
	public void onResourcesReloaded(Identifier id, Consumer<ResourceManager> listener) {
		RegisterClientReloadListenersEvent.BUS.addListener(
				event -> event.registerReloadListener((SynchronousResourceReloader) listener::accept));
	}
}
