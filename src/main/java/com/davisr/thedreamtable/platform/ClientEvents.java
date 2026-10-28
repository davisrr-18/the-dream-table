package com.davisr.thedreamtable.platform;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

import java.util.function.Consumer;

/** Client tick, connection, input and resource hooks. Installed only on a client; listeners run in registration order. */
public interface ClientEvents {
	/** At the end of every client tick. */
	void onTickEnd(Consumer<MinecraftClient> listener);

	/** When the client joins a world or server. */
	void onJoin(Consumer<MinecraftClient> listener);

	/** When the client leaves a world or server. */
	void onDisconnect(Consumer<MinecraftClient> listener);

	/** Before a block is used; {@link ActionResult#PASS} lets vanilla continue, any other result ends the interaction. */
	void onUseBlock(UseBlockListener listener);

	/** Before an item is used; a pass result lets vanilla continue, any other result ends the interaction. */
	void onUseItem(UseItemListener listener);

	/** Adds the key to the controls screen and returns it. */
	KeyBinding registerKeyBinding(KeyBinding key);

	/** Calls the listener after each reload of the client's resources. The id is stable across versions. */
	void onResourcesReloaded(Identifier id, Consumer<ResourceManager> listener);

	@FunctionalInterface
	interface UseBlockListener {
		ActionResult use(PlayerEntity player, World world, Hand hand, BlockHitResult hit);
	}

	@FunctionalInterface
	interface UseItemListener {
		TypedActionResult<ItemStack> use(PlayerEntity player, World world, Hand hand);
	}
}
