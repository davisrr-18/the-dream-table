package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
