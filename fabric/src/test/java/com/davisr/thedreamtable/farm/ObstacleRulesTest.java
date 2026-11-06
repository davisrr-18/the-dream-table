package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.fluid.FluidState;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.EmptyBlockView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the builder may clear from a farm spot: plain blocks only, everything else stops the build. */
class ObstacleRulesTest {
	private static final BlockPos TABLE = new BlockPos(0, 64, 0);
	private static final BlockPos SPOT = new BlockPos(3, 64, 3);

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	private static boolean clearable(Block block) {
		return ObstacleRules.clearable(EmptyBlockView.INSTANCE, TABLE, SPOT, block.getDefaultState());
	}

	@Test
	void plainBlocksAreCleared() {
		for (Block block : new Block[]{Blocks.STONE, Blocks.DIRT, Blocks.SAND, Blocks.OAK_LEAVES, Blocks.AIR}) {
			assertTrue(clearable(block), block.toString());
		}
	}

	@Test
	void theTablesOwnSpotIsNeverCleared() {
		assertFalse(ObstacleRules.clearable(EmptyBlockView.INSTANCE, TABLE, TABLE, Blocks.STONE.getDefaultState()));
	}

	@Test
	void keepListBlocksAreNeverCleared() {
		for (Block block : new Block[]{Blocks.BUDDING_AMETHYST, Blocks.REINFORCED_DEEPSLATE, Blocks.DRAGON_EGG, Blocks.TURTLE_EGG,
				Blocks.SNIFFER_EGG, Blocks.FROGSPAWN, Blocks.RESPAWN_ANCHOR, Blocks.LODESTONE, Blocks.NOTE_BLOCK, Blocks.REDSTONE_LAMP}) {
			assertFalse(clearable(block), block.toString());
		}
	}

	@Test
	void blockEntitiesAreNeverCleared() {
		for (Block block : new Block[]{Blocks.CHEST, Blocks.HOPPER, Blocks.FURNACE, Blocks.SPAWNER}) {
			assertFalse(clearable(block), block.toString());
		}
	}

	@Test
	void unbreakableBlocksAreNeverCleared() {
		for (Block block : new Block[]{Blocks.BEDROCK, Blocks.BARRIER, Blocks.END_PORTAL_FRAME}) {
			assertFalse(clearable(block), block.toString());
		}
	}

	@Test
	void redstoneEmittersAreNeverCleared() {
		for (Block block : new Block[]{Blocks.REDSTONE_BLOCK, Blocks.REDSTONE_TORCH, Blocks.LEVER}) {
			assertFalse(clearable(block), block.toString());
		}
	}

	@Test
	void pistonsTntRailsAndInfestedBlocksAreNeverCleared() {
		for (Block block : new Block[]{Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.PISTON_HEAD, Blocks.TNT, Blocks.RAIL,
				Blocks.POWERED_RAIL, Blocks.DETECTOR_RAIL, Blocks.ACTIVATOR_RAIL, Blocks.INFESTED_STONE, Blocks.INFESTED_DEEPSLATE}) {
			assertFalse(clearable(block), block.toString());
		}
	}

	@Test
	void clearingKeepsNoMiningRewardButEveryOrdinaryBlockStillDrops() {
		assertTrue(ObstacleRules.minedResource(Blocks.OBSIDIAN.getDefaultState()));
		assertTrue(ObstacleRules.minedResource(Blocks.ANCIENT_DEBRIS.getDefaultState()));
		assertTrue(ObstacleRules.minedResource(Blocks.NETHER_QUARTZ_ORE.getDefaultState()));
		assertFalse(ObstacleRules.minedResource(Blocks.STONE.getDefaultState()));
		assertFalse(ObstacleRules.minedResource(Blocks.COBBLESTONE.getDefaultState()));
		assertFalse(ObstacleRules.minedResource(Blocks.DIRT.getDefaultState()));
	}

	private static boolean clearableInLayer(Block block, boolean stepCell) {
		return ObstacleRules.clearableInLayer(EmptyBlockView.INSTANCE, TABLE, SPOT, block.getDefaultState(), stepCell);
	}

	@Test
	void aLayerClearTakesFluidsAndPlantsButNotAirOrAStepCell() {
		for (Block block : new Block[]{Blocks.WATER, Blocks.LAVA, Blocks.GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.DANDELION,
				Blocks.SNOW, Blocks.FIRE, Blocks.STONE, Blocks.OAK_DOOR}) {
			assertTrue(clearableInLayer(block, false), block.toString());
		}
		assertFalse(clearableInLayer(Blocks.AIR, false));
		assertFalse(clearableInLayer(Blocks.STONE, true));
		assertFalse(clearableInLayer(Blocks.WATER, true));
	}

	@Test
	void aLayerClearKeepsTheBlocksTheSingleBlockRulesKeep() {
		for (Block block : new Block[]{Blocks.CHEST, Blocks.BEDROCK, Blocks.REDSTONE_BLOCK, Blocks.RAIL, Blocks.TNT, Blocks.PISTON}) {
			assertFalse(clearableInLayer(block, false), block.toString());
		}
	}

	/** A world that holds only the given states, air everywhere else. */
	private static BlockView viewOf(Map<BlockPos, BlockState> states) {
		return new BlockView() {
			@Override
			public BlockEntity getBlockEntity(BlockPos pos) {
				return null;
			}

			@Override
			public BlockState getBlockState(BlockPos pos) {
				return states.getOrDefault(pos, Blocks.AIR.getDefaultState());
			}

			@Override
			public FluidState getFluidState(BlockPos pos) {
				return getBlockState(pos).getFluidState();
			}

			@Override
			public int getHeight() {
				return 384;
			}

			@Override
			public int getBottomY() {
				return -64;
			}
		};
	}

	@Test
	void aDoubleBlockIsPairedOnlyWithItsOwnOtherHalf() {
		BlockPos base = new BlockPos(3, 64, 3);
		BlockState lower = Blocks.TALL_GRASS.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
		BlockState upper = Blocks.TALL_GRASS.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
		BlockView pair = viewOf(Map.of(base, lower, base.up(), upper));

		assertEquals(base.up(), ObstacleRules.partnerOf(pair, base, lower));
		assertEquals(base, ObstacleRules.partnerOf(pair, base.up(), upper));
		assertNull(ObstacleRules.partnerOf(EmptyBlockView.INSTANCE, base, lower));
		assertNull(ObstacleRules.partnerOf(viewOf(Map.of(base, lower)), base, lower));
		assertNull(ObstacleRules.partnerOf(pair, base, Blocks.STONE.getDefaultState()));
	}
}
