package com.davisr.dreamtablepoc;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

public final class CounterMenu extends ScreenHandler {
	private final BlockPos pos;
	private int counter;

	public CounterMenu(int syncId, PlayerInventory inventory, BlockPos pos, int counter) {
		super(PocContent.COUNTER_MENU_TYPE, syncId);
		this.pos = pos;
		this.counter = counter;
	}

	public BlockPos pos() {
		return pos;
	}

	public int counter() {
		return counter;
	}

	/** Called on the client when the server pushes a new value while the menu is open. */
	public void setCounter(int counter) {
		this.counter = counter;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return player.getWorld().getBlockState(pos).isOf(PocContent.COUNTER_BLOCK.get())
				&& player.canInteractWithBlockAt(pos, 1.0);
	}
}
