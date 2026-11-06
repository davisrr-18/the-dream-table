package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.platform.ClientEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.function.Consumer;

import static com.davisr.thedreamtable.forge.ForgePlatform.listen;

/** Forge's client tick, connection, input and resource events. Only loaded by the client setup. */
final class ForgeClientEvents implements ClientEvents {
	private static final IEventBus BUS = MinecraftForge.EVENT_BUS;

	private final IEventBus modBus;

	ForgeClientEvents(IEventBus modBus) {
		this.modBus = modBus;
	}

	@Override
	public void onTickEnd(Consumer<MinecraftClient> listener) {
		listen(BUS, TickEvent.ClientTickEvent.class, event -> {
			if (event.phase == TickEvent.Phase.END) {
				listener.accept(MinecraftClient.getInstance());
			}
		});
	}

	@Override
	public void onJoin(Consumer<MinecraftClient> listener) {
		listen(BUS, ClientPlayerNetworkEvent.LoggingIn.class, event -> listener.accept(MinecraftClient.getInstance()));
	}

	@Override
	public void onDisconnect(Consumer<MinecraftClient> listener) {
		listen(BUS, ClientPlayerNetworkEvent.LoggingOut.class, event -> listener.accept(MinecraftClient.getInstance()));
	}

	@Override
	public void onUseBlock(UseBlockListener listener) {
		listen(BUS, PlayerInteractEvent.RightClickBlock.class, event -> {
			ActionResult result = listener.use(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
			if (result != ActionResult.PASS) {
				event.setCanceled(true);
				event.setCancellationResult(result);
			}
		});
	}

	@Override
	public void onUseItem(UseItemListener listener) {
		listen(BUS, PlayerInteractEvent.RightClickItem.class, event -> {
			TypedActionResult<ItemStack> result = listener.use(event.getEntity(), event.getLevel(), event.getHand());
			if (result.getResult() != ActionResult.PASS) {
				event.setCanceled(true);
				event.setCancellationResult(result.getResult());
			}
		});
	}

	@Override
	public KeyBinding registerKeyBinding(KeyBinding key) {
		listen(modBus, RegisterKeyMappingsEvent.class, event -> event.register(key));
		return key;
	}

	@Override
	public void onResourcesReloaded(Identifier id, Consumer<ResourceManager> listener) {
		listen(modBus, RegisterClientReloadListenersEvent.class,
				event -> event.registerReloadListener((SynchronousResourceReloader) listener::accept));
	}
}
