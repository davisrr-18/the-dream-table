package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.network.TableNoticePayload;
import com.davisr.thedreamtable.network.RequestThrottle;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.function.IntPredicate;

/**
 * Moves what a farm still needs out of inventories into the ledger, one stack at a time. It only ever takes the exact
 * accepted amount, so repeating it cannot over-deliver, and it never touches a stack with data components
 * ({@link MaterialLedger#plain}); those are counted as refused so the player can be told.
 */
public final class MaterialDelivery {
	/** The player's main inventory (hotbar included) and offhand; armor never counts. */
	static final IntPredicate PLAYER_SLOTS = slot -> slot < PlayerInventory.MAIN_SIZE || slot == PlayerInventory.OFF_HAND_SLOT;
	static final IntPredicate ALL_SLOTS = slot -> true;

	/** A scan covers a few thousand blocks, so a modified client may not repeat it every tick. */
	static final RequestThrottle NEARBY_REQUESTS = new RequestThrottle(4, RequestThrottle.DEFAULT_WINDOW_TICKS);

	private MaterialDelivery() {
	}

	public static void register() {
		NEARBY_REQUESTS.forgetOnDisconnect();
	}

	/** What the farm takes: {@code accept} returns how many of the stack it took, {@code wants} whether it still needs the item at all. */
	interface Receiver {
		int accept(ItemStack stack);

		boolean wants(ItemStack stack);
	}

	/** Items moved, and stacks the farm needs but that were refused for their data. */
	record Tally(int delivered, int refused) {
		static final Tally NONE = new Tally(0, 0);

		Tally plus(Tally other) {
			return new Tally(delivered + other.delivered, refused + other.refused);
		}
	}

	static Tally pull(Inventory source, IntPredicate slots, Receiver receiver) {
		int delivered = 0;
		int refused = 0;
		for (int slot = 0; slot < source.size(); slot++) {
			if (!slots.test(slot)) {
				continue;
			}
			ItemStack stack = source.getStack(slot);
			if (stack.isEmpty()) {
				continue;
			}
			if (!MaterialLedger.plain(stack)) {
				if (receiver.wants(stack)) {
					refused++;
				}
				continue;
			}
			int moved = Math.min(receiver.accept(stack), stack.getCount());
			if (moved > 0) {
				source.removeStack(slot, moved);
				delivered += moved;
			}
		}
		return new Tally(delivered, refused);
	}

	/** Plain information only when everything wanted was taken; skipped stacks and empty-handed tries are problems. */
	static TableNoticePayload.Severity severity(Tally tally) {
		return tally.delivered() > 0 && tally.refused() == 0 ? TableNoticePayload.Severity.INFO : TableNoticePayload.Severity.PROBLEM;
	}

	/** The notice line for a bulk delivery; skipped containers are only mentioned when nothing could be taken. */
	static Text summary(Tally tally, boolean nearby, int skippedContainers) {
		if (tally.delivered() > 0) {
			return tally.refused() > 0
					? Text.translatable("gui.the_dream_table.delivered_refused", tally.delivered(), tally.refused())
					: Text.translatable("gui.the_dream_table.delivered", tally.delivered());
		}
		if (tally.refused() > 0) {
			return Text.translatable("gui.the_dream_table.delivery_refused", tally.refused());
		}
		if (!nearby) {
			return Text.translatable("gui.the_dream_table.deliver_none");
		}
		return skippedContainers > 0
				? Text.translatable("gui.the_dream_table.deliver_nearby_none_skipped", skippedContainers)
				: Text.translatable("gui.the_dream_table.deliver_nearby_none");
	}
}
