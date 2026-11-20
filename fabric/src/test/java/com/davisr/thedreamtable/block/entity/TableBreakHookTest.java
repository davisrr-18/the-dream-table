package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.block.DreamTableBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Where the break hooks live. On 1.21.11 {@code WorldChunk} removes the block entity before {@code Block.onStateReplaced}
 * runs, so a hook only there finds no table: nothing is parked or returned and the farm areas stay claimed. The hook that sees
 * the table is {@code BlockEntity.onBlockReplaced} (server only, before the removal). A change carrying flag 256
 * ({@code /setblock}, {@code /fill}, structures) skips it, so {@code markRemoved} sets the table aside and the server tick
 * settles it. A real chunk cannot be built without a running game, so this pins the declarations; the call order itself is
 * vanilla's and is on the in-game checklist (break by hand, explosion, {@code /setblock air}, {@code /fill}, {@code strict}).
 */
class TableBreakHookTest {
	@Test
	void theBlockEntityOwnsTheBreakHookAndSetsTheTableAside() {
		assertDoesNotThrow(() -> DreamTableBlockEntity.class.getDeclaredMethod("onBlockReplaced", BlockPos.class, BlockState.class));
		assertDoesNotThrow(() -> DreamTableBlockEntity.class.getDeclaredMethod("markRemoved"));
		assertDoesNotThrow(() -> DreamTableBlockEntity.class.getDeclaredMethod("settleReplaced"));
	}

	@Test
	void theBlockDoesNotHookStateReplaced() {
		assertFalse(Arrays.stream(DreamTableBlock.class.getDeclaredMethods()).anyMatch(method -> method.getName().equals("onStateReplaced")),
				"Block.onStateReplaced runs after the block entity is gone, or not at all with /setblock strict");
	}
}
