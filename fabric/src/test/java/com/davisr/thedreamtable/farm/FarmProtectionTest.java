package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.GameSetup;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "Protect My Farms": who is stopped, which blocks count as structure, and that the farm's own turn and mirror are followed. */
class FarmProtectionTest {
	private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID SHARED = UUID.fromString("20000000-0000-0000-0000-000000000002");
	private static final UUID STRANGER = UUID.fromString("30000000-0000-0000-0000-000000000003");
	private static final FarmKey KEY = new FarmKey(Identifier.of("minecraft", "overworld"), UUID.fromString("40000000-0000-0000-0000-000000000004"));
	private static final FarmRecord FARM = new FarmRecord(KEY, OWNER, Map.of(SHARED, new FarmGrant(SHARED, FarmRole.VIEWER, 1L)));
	private static final BlockPos TABLE = new BlockPos(0, 64, 0);
	private static final BlockPos ANCHOR = new BlockPos(30, 64, 30);
	private static final BooleanSupplier STRUCTURE = () -> true;

	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	private static boolean denies(boolean server, boolean ownerProtects, boolean operator, UUID actor, boolean sharing) {
		return FarmProtection.denies(server, ownerProtects, operator, actor, FARM, sharing, STRUCTURE);
	}

	@Test
	void aStrangerIsStoppedOnlyWhenTheOwnerTurnedItOnAndTheServerAllowsIt() {
		assertTrue(denies(true, true, false, STRANGER, true));
		assertFalse(denies(true, false, false, STRANGER, true));
		assertFalse(denies(false, true, false, STRANGER, true));
		assertFalse(denies(false, false, false, STRANGER, true));
	}

	@Test
	void theOwnerAndSharedPlayersAreNeverStopped() {
		assertFalse(denies(true, true, false, OWNER, true));
		assertFalse(denies(true, true, false, SHARED, true));
	}

	@Test
	void sharedAccessCountsOnlyWhileSharingIsOnForTheServer() {
		assertTrue(denies(true, true, false, SHARED, false));
		assertFalse(denies(true, true, false, OWNER, false));
	}

	@Test
	void serverOperatorsBypass() {
		assertFalse(denies(true, true, true, STRANGER, true));
	}

	@Test
	void theDesignIsOnlySearchedForAStrangerOfAProtectedFarm() {
		boolean[] asked = {false};
		BooleanSupplier probe = () -> {
			asked[0] = true;
			return true;
		};
		FarmProtection.denies(true, true, false, OWNER, FARM, true, probe);
		FarmProtection.denies(true, false, false, STRANGER, FARM, true, probe);
		FarmProtection.denies(false, true, false, STRANGER, FARM, true, probe);
		FarmProtection.denies(true, true, true, STRANGER, FARM, true, probe);
		assertFalse(asked[0]);
		assertTrue(FarmProtection.denies(true, true, false, STRANGER, FARM, true, probe));
		assertTrue(asked[0]);
		assertFalse(FarmProtection.denies(true, true, false, STRANGER, FARM, true, () -> false));
	}

	@Test
	void theServerSwitchDefaultsToOn() {
		assertTrue(DreamTableConfig.farmProtectionEnabled());
	}

	@Test
	void produceIsHarvestableAndBuildingBlocksAreNot() {
		List.of(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS, Blocks.MELON_STEM, Blocks.PUMPKIN_STEM,
				Blocks.ATTACHED_MELON_STEM, Blocks.ATTACHED_PUMPKIN_STEM, Blocks.SUGAR_CANE, Blocks.CACTUS, Blocks.BAMBOO,
				Blocks.BAMBOO_SAPLING, Blocks.KELP, Blocks.KELP_PLANT, Blocks.COCOA, Blocks.SWEET_BERRY_BUSH, Blocks.NETHER_WART,
				Blocks.PUMPKIN, Blocks.MELON, Blocks.OAK_SAPLING, Blocks.TORCHFLOWER_CROP, Blocks.PITCHER_CROP, Blocks.CAVE_VINES,
				Blocks.TWISTING_VINES, Blocks.CHORUS_FLOWER, Blocks.CHORUS_PLANT, Blocks.SEA_PICKLE)
				.forEach(block -> assertTrue(FarmProtection.harvestable(block.getDefaultState()), block.toString()));
		List.of(Blocks.FARMLAND, Blocks.DIRT, Blocks.SAND, Blocks.STONE, Blocks.HOPPER, Blocks.CHEST, Blocks.OBSERVER,
				Blocks.PISTON, Blocks.GLASS, Blocks.WATER, Blocks.OAK_PLANKS, Blocks.SOUL_SAND, Blocks.CARVED_PUMPKIN,
				Blocks.HAY_BLOCK, Blocks.DISPENSER, Blocks.OAK_LOG)
				.forEach(block -> assertFalse(FarmProtection.harvestable(block.getDefaultState()), block.toString()));
	}

	@Test
	void airAndFluidsAreNeverStructure() {
		assertFalse(FarmProtection.structural(Blocks.AIR.getDefaultState()));
		assertFalse(FarmProtection.structural(Blocks.CAVE_AIR.getDefaultState()));
		assertFalse(FarmProtection.structural(Blocks.WATER.getDefaultState()));
		assertFalse(FarmProtection.structural(Blocks.LAVA.getDefaultState()));
		assertTrue(FarmProtection.structural(Blocks.STONE.getDefaultState()));
		assertTrue(FarmProtection.structural(Blocks.FARMLAND.getDefaultState()));
	}

	private static FarmTemplate farm() {
		return FarmTemplate.of("protected", List.of(
				FarmStep.block(0, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE),
				FarmStep.block(4, 0, 0, Blocks.FARMLAND.getDefaultState(), Items.DIRT),
				FarmStep.block(4, 1, 0, Blocks.WHEAT.getDefaultState(), Items.WHEAT_SEEDS),
				FarmStep.block(4, 0, 2, Blocks.OAK_PLANKS.getDefaultState(), Items.OAK_PLANKS),
				FarmStep.block(0, 0, 2, Blocks.WATER.getDefaultState(), Items.WATER_BUCKET)));
	}

	private static BlockState worldAt(FarmTemplate template, FarmSite site, int index) {
		return site.orient(template.steps().get(index).state());
	}

	@Test
	void structureIsProtectedProduceAndFluidAreNotInEveryTurnAndMirror() {
		FarmTemplate farm = farm();
		for (Direction facing : Direction.Type.HORIZONTAL) {
			for (boolean mirrored : new boolean[]{false, true}) {
				for (BlockPos anchor : new BlockPos[]{ANCHOR, null}) {
					FarmSite site = new FarmSite(TABLE, anchor, facing, mirrored);
					String label = facing + " mirrored=" + mirrored + " anchor=" + (anchor != null);
					for (int index = 0; index < farm.steps().size(); index++) {
						BlockPos pos = site.target(farm, farm.steps().get(index));
						boolean expected = index == 0 || index == 1 || index == 3;
						assertEquals(expected, FarmProtection.structuralAt(farm, site, pos, worldAt(farm, site, index)), label + " step " + index);
					}
				}
			}
		}
	}

	@Test
	void aPositionOutsideTheDesignIsNotProtected() {
		FarmTemplate farm = farm();
		FarmSite site = new FarmSite(TABLE, ANCHOR, Direction.EAST, false);
		BlockPos inside = site.target(farm, farm.steps().get(0));
		assertTrue(FarmProtection.structuralAt(farm, site, inside, Blocks.STONE.getDefaultState()));
		for (Direction side : Direction.values()) {
			BlockPos next = inside.offset(side);
			boolean designed = farm.steps().stream().anyMatch(step -> site.target(farm, step).equals(next));
			assertEquals(designed, FarmProtection.structuralAt(farm, site, next, Blocks.STONE.getDefaultState()), side.toString());
		}
		assertFalse(FarmProtection.structuralAt(farm, site, new BlockPos(500, 70, 500), Blocks.STONE.getDefaultState()));
	}

	@Test
	void whateverStandsAtAStructuralSpotNowDecidesToo() {
		FarmTemplate farm = farm();
		FarmSite site = new FarmSite(TABLE, ANCHOR, Direction.SOUTH, false);
		BlockPos farmland = site.target(farm, farm.steps().get(1));
		assertTrue(FarmProtection.structuralAt(farm, site, farmland, Blocks.FARMLAND.getDefaultState()));
		assertTrue(FarmProtection.structuralAt(farm, site, farmland, Blocks.DIRT.getDefaultState()));
		assertFalse(FarmProtection.structuralAt(farm, site, farmland, Blocks.AIR.getDefaultState()));
		assertFalse(FarmProtection.structuralAt(farm, site, farmland, Blocks.WHEAT.getDefaultState()));
		assertFalse(FarmProtection.structuralAt(farm, site, farmland, Blocks.WATER.getDefaultState()));
	}

	@Test
	void mirroringMovesTheProtectedSpotsToTheFlippedSide() {
		FarmTemplate farm = farm();
		Set<BlockPos> plain = structure(farm, new FarmSite(TABLE, ANCHOR, Direction.SOUTH, false));
		Set<BlockPos> mirrored = structure(farm, new FarmSite(TABLE, ANCHOR, Direction.SOUTH, true));
		assertEquals(3, plain.size());
		assertEquals(3, mirrored.size());
		assertNotEquals(plain, mirrored);
		FarmSite mirroredSite = new FarmSite(TABLE, ANCHOR, Direction.SOUTH, true);
		for (BlockPos pos : plain) {
			if (!mirrored.contains(pos)) {
				assertFalse(FarmProtection.structuralAt(farm, mirroredSite, pos, Blocks.STONE.getDefaultState()), pos.toString());
			}
		}
	}

	private static Set<BlockPos> structure(FarmTemplate farm, FarmSite site) {
		Set<BlockPos> found = new HashSet<>();
		for (FarmStep step : farm.steps()) {
			BlockPos pos = site.target(farm, step);
			if (FarmProtection.structuralAt(farm, site, pos, Blocks.STONE.getDefaultState())) {
				found.add(pos);
			}
		}
		return found;
	}

	@Test
	void finishedFarmsAreFoundByPositionThroughTheirSavedDesign() {
		FarmTemplate farm = farm();
		FarmSite site = new FarmSite(TABLE, ANCHOR, Direction.WEST, true);
		FarmAreas areas = new FarmAreas();
		areas.recordFinished(OWNER, farm, site, Map.of());
		BlockPos inside = site.target(farm, farm.steps().get(0));

		List<FarmAreas.Guard> guards = FarmAreas.read(areas.writeNbt(new NbtCompound(), null), null).guardsAt(inside);

		assertEquals(1, guards.size());
		assertEquals(OWNER, guards.get(0).ownerUuid());
		assertEquals(site, guards.get(0).site());
		assertTrue(FarmProtection.structuralAt(guards.get(0).layout(), guards.get(0).site(), inside, Blocks.STONE.getDefaultState()));
		assertTrue(areas.guardsAt(new BlockPos(500, 70, 500)).isEmpty());
		assertTrue(new FarmAreas().guardsAt(inside).isEmpty());
	}

	@Test
	void theModsOwnProbesAnswerQuietlyAndEndEvenWhenTheyThrow() {
		assertFalse(FarmProtection.probing());
		assertTrue(FarmProtection.quietly(() -> FarmProtection.quietly(FarmProtection::probing)));
		assertFalse(FarmProtection.quietly(() -> false));
		try {
			FarmProtection.quietly(() -> {
				throw new IllegalStateException();
			});
		} catch (IllegalStateException expected) {
			// The probe's own failure is not the point here.
		}
		assertFalse(FarmProtection.probing());
	}
}
