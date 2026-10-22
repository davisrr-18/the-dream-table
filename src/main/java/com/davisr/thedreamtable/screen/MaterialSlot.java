package com.davisr.thedreamtable.screen;

import com.davisr.thedreamtable.farm.MaterialSpec;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

public class MaterialSlot extends Slot {
	private final DreamTableScreenHandler handler;
	private final int materialIndex;

	public MaterialSlot(Inventory inventory, int index, int x, int y, DreamTableScreenHandler handler) {
		super(inventory, index, x, y);
		this.handler = handler;
		this.materialIndex = index;
	}

	@Override
	public boolean canInsert(ItemStack stack) {
		MaterialSpec spec = handler.material(materialIndex);
		return spec != null && stack.isOf(spec.item());
	}

	@Override
	public int getMaxItemCount() {
		MaterialSpec spec = handler.material(materialIndex);
		return spec == null ? 0 : spec.count();
	}

	@Override
	public int getMaxItemCount(ItemStack stack) {
		return getMaxItemCount();
	}

	@Override
	public boolean isEnabled() {
		return handler.getPage() == DreamTableScreenHandler.PAGE_REQUIREMENTS && handler.material(materialIndex) != null;
	}
}
