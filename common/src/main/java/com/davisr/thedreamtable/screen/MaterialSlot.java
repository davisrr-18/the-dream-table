package com.davisr.thedreamtable.screen;

import com.davisr.thedreamtable.block.entity.MaterialLedger;
import com.davisr.thedreamtable.farm.MaterialSpec;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
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
		Item item = handler.materialItem(materialIndex);
		return accepts(item != null && stack.isOf(item) && MaterialLedger.plain(stack), getMaxItemCount());
	}

	/** No room means no insert: vanilla insertItem counts a 0-item split as a move, and shift-click then loops forever. */
	static boolean accepts(boolean matches, int room) {
		return matches && room > 0;
	}

	@Override
	public int getMaxItemCount() {
		return room(handler.material(materialIndex), handler.supplied(materialIndex));
	}

	/** Only what the farm still needs: the ledger absorbs the slot right away, so the full count would let a surplus sit in it. */
	static int room(MaterialSpec spec, int supplied) {
		return spec == null ? 0 : Math.max(0, spec.count() - supplied);
	}

	@Override
	public int getMaxItemCount(ItemStack stack) {
		return getMaxItemCount();
	}

	@Override
	public boolean isEnabled() {
		return handler.getPage() == DreamTableScreenHandler.PAGE_REQUIREMENTS && handler.material(materialIndex) != null && handler.shown(this);
	}
}
