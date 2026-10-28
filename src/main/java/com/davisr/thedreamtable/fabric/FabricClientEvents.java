package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.platform.ClientEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

/** Fabric API's client tick, connection, input and resource events. Only loaded by the client entrypoint. */
final class FabricClientEvents implements ClientEvents {
	@Override
	public void onTickEnd(Consumer<MinecraftClient> listener) {
		ClientTickEvents.END_CLIENT_TICK.register(listener::accept);
	}

	@Override
	public void onJoin(Consumer<MinecraftClient> listener) {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> listener.accept(client));
	}

	@Override
	public void onDisconnect(Consumer<MinecraftClient> listener) {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> listener.accept(client));
	}

	@Override
	public void onUseBlock(UseBlockListener listener) {
		UseBlockCallback.EVENT.register(listener::use);
	}

	@Override
	public void onUseItem(UseItemListener listener) {
		UseItemCallback.EVENT.register(listener::use);
	}

	@Override
	public KeyBinding registerKeyBinding(KeyBinding key) {
		return KeyBindingHelper.registerKeyBinding(key);
	}

	@Override
	public void onResourcesReloaded(Identifier id, Consumer<ResourceManager> listener) {
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return id;
			}

			@Override
			public void reload(ResourceManager manager) {
				listener.accept(manager);
			}
		});
	}
}
