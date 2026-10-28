package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockMatchTest {
	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	@Test
	void directionMatters() {
		BlockState north = Blocks.HOPPER.getDefaultState().with(Properties.HOPPER_FACING, Direction.NORTH);
		BlockState down = Blocks.HOPPER.getDefaultState().with(Properties.HOPPER_FACING, Direction.DOWN);
		assertFalse(BlockMatch.sameBlock(north, down));
		assertTrue(BlockMatch.sameBlock(north, north));
	}

	@Test
	void stairsNeedTheSameFacingAndHalf() {
		BlockState stairs = Blocks.OAK_STAIRS.getDefaultState();
		assertFalse(BlockMatch.sameBlock(stairs.with(Properties.HORIZONTAL_FACING, Direction.EAST), stairs.with(Properties.HORIZONTAL_FACING, Direction.WEST)));
		assertFalse(BlockMatch.sameBlock(stairs.with(Properties.BLOCK_HALF, BlockHalf.TOP), stairs));
	}

	@Test
	void stateThatChangesOnItsOwnIsIgnored() {
		BlockState door = Blocks.OAK_DOOR.getDefaultState();
		assertTrue(BlockMatch.sameBlock(door.with(Properties.OPEN, true), door));
		assertTrue(BlockMatch.sameBlock(door.with(Properties.POWERED, true), door));
		BlockState fence = Blocks.OAK_FENCE.getDefaultState();
		assertTrue(BlockMatch.sameBlock(fence.with(Properties.NORTH, true), fence));
	}

	@Test
	void slabTypeMatters() {
		BlockState slab = Blocks.STONE_SLAB.getDefaultState();
		assertFalse(BlockMatch.sameBlock(slab.with(Properties.SLAB_TYPE, SlabType.TOP), slab.with(Properties.SLAB_TYPE, SlabType.BOTTOM)));
		assertFalse(BlockMatch.sameBlock(slab.with(Properties.SLAB_TYPE, SlabType.DOUBLE), slab.with(Properties.SLAB_TYPE, SlabType.BOTTOM)));
	}

	@Test
	void differentBlocksNeverMatch() {
		assertFalse(BlockMatch.sameBlock(Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState()));
		assertFalse(BlockMatch.sameShape(Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState()));
	}

	@Test
	void waterloggingCountsForBuildingButNotForTakingApart() {
		BlockState dry = Blocks.OAK_SLAB.getDefaultState();
		BlockState wet = dry.with(Properties.WATERLOGGED, true);
		assertFalse(BlockMatch.sameBlock(dry, wet));
		assertTrue(BlockMatch.sameShape(wet, dry));
	}

	@Test
	void liquidLevelMatters() {
		BlockState source = Blocks.WATER.getDefaultState();
		BlockState flowing = source.with(Properties.LEVEL_15, 3);
		assertFalse(BlockMatch.sameBlock(flowing, source));
		assertTrue(BlockMatch.sameBlock(source, source));
	}
}
