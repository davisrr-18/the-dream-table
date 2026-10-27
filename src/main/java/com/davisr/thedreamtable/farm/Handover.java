package com.davisr.thedreamtable.farm;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Hands items to a player. What doesn't fit in their inventory drops at their feet, never into the void, and the
 * caller is told so it can let the player know once.
 */
public final class Handover {
	private Handover() {
	}

	/** Returns true when part of the stack didn't fit and was dropped. */
	public static boolean give(PlayerEntity player, ItemStack stack) {
		player.getInventory().insertStack(stack);
		if (stack.isEmpty()) {
			return false;
		}
		player.dropItem(stack, false);
		return true;
	}

	/** Gives any amount, in stacks no bigger than the item allows. Returns true when anything was dropped. */
	public static boolean giveAll(PlayerEntity player, Item item, int count) {
		int max = Math.max(1, item.getMaxCount());
		boolean dropped = false;
		while (count > 0) {
			int amount = Math.min(count, max);
			dropped |= give(player, new ItemStack(item, amount));
			count -= amount;
		}
		return dropped;
	}

	public static void tellDropped(PlayerEntity player) {
		player.sendMessage(Text.translatable("gui.the_dream_table.inventory_full"), false);
	}
}
