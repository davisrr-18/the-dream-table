package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.GameSetup;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
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
	private static final UUID STRANGER = UUID.fromString("30000000-0000-0000-0000-000000000003");
	private static final FarmKey KEY = new FarmKey(new Identifier("minecraft", "overworld"), UUID.fromString("40000000-0000-0000-0000-000000000004"));
	private static final FarmRecord FARM = new FarmRecord(KEY, OWNER);
	private static final BlockPos TABLE = new BlockPos(0, 64, 0);
	private static final BlockPos ANCHOR = new BlockPos(30, 64, 30);
	private static final BooleanSupplier STRUCTURE = () -> true;
	private static final Map<Item, Item> NO_SWAPS = Map.of();

	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	private static boolean denies(boolean server, boolean ownerProtects, boolean operator, UUID actor) {
		return FarmProtection.denies(server, ownerProtects, operator, actor, FARM, STRUCTURE);
	}

	@Test
	void aStrangerIsStoppedOnlyWhenTheOwnerTurnedItOnAndTheServerAllowsIt() {
		assertTrue(denies(true, true, false, STRANGER));
		assertFalse(denies(true, false, false, STRANGER));
		assertFalse(denies(false, true, false, STRANGER));
		assertFalse(denies(false, false, false, STRANGER));
	}

	@Test
	void theOwnerIsNeverStopped() {
		assertFalse(denies(true, true, false, OWNER));
	}

	@Test
	void serverOperatorsBypass() {
		assertFalse(denies(true, true, true, STRANGER));
	}

	@Test
	void theDesignIsOnlySearchedForAStrangerOfAProtectedFarm() {
		boolean[] asked = {false};
		BooleanSupplier probe = () -> {
			asked[0] = true;
			return true;
		};
		FarmProtection.denies(true, true, false, OWNER, FARM, probe);
		FarmProtection.denies(true, true, true, STRANGER, FARM, probe);
		FarmProtection.denies(true, false, false, STRANGER, FARM, probe);
		FarmProtection.denies(false, true, false, STRANGER, FARM, probe);
		assertFalse(asked[0]);
		assertTrue(FarmProtection.denies(true, true, false, STRANGER, FARM, probe));
		assertTrue(asked[0]);
		assertFalse(FarmProtection.denies(true, true, false, STRANGER, FARM, () -> false));
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
						assertEquals(expected, FarmProtection.structuralAt(farm, site, NO_SWAPS, pos, worldAt(farm, site, index)), label + " step " + index);
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
		assertTrue(FarmProtection.structuralAt(farm, site, NO_SWAPS, inside, Blocks.STONE.getDefaultState()));
		for (Direction side : Direction.values()) {
			BlockPos next = inside.offset(side);
			boolean designed = farm.steps().stream().anyMatch(step -> site.target(farm, step).equals(next));
			assertFalse(designed);
			assertFalse(FarmProtection.structuralAt(farm, site, NO_SWAPS, next, Blocks.STONE.getDefaultState()), side.toString());
		}
		assertFalse(FarmProtection.structuralAt(farm, site, NO_SWAPS, new BlockPos(500, 70, 500), Blocks.STONE.getDefaultState()));
	}

	@Test
	void whateverStandsAtAStructuralSpotNowMustStillBeTheDesignsBlock() {
		FarmTemplate farm = farm();
		FarmSite site = new FarmSite(TABLE, ANCHOR, Direction.SOUTH, false);
		BlockPos farmland = site.target(farm, farm.steps().get(1));
		assertTrue(FarmProtection.structuralAt(farm, site, NO_SWAPS, farmland, Blocks.FARMLAND.getDefaultState()));
		assertTrue(FarmProtection.structuralAt(farm, site, NO_SWAPS, farmland, Blocks.DIRT.getDefaultState()));
		assertFalse(FarmProtection.structuralAt(farm, site, NO_SWAPS, farmland, Blocks.AIR.getDefaultState()));
		assertFalse(FarmProtection.structuralAt(farm, site, NO_SWAPS, farmland, Blocks.WHEAT.getDefaultState()));
		assertFalse(FarmProtection.structuralAt(farm, site, NO_SWAPS, farmland, Blocks.WATER.getDefaultState()));
		// Somebody's block in a hole of a damaged farm is not the farm's, so its placer can still break it.
		assertFalse(FarmProtection.structuralAt(farm, site, NO_SWAPS, farmland, Blocks.COBBLESTONE.getDefaultState()));
		assertFalse(FarmProtection.structuralAt(farm, site, NO_SWAPS, farmland, Blocks.OAK_PLANKS.getDefaultState()));
	}

	@Test
	void theFarmsMaterialSwapsDecideWhichBlockIsTheDesigns() {
		FarmTemplate farm = farm();
		FarmSite site = new FarmSite(TABLE, ANCHOR, Direction.NORTH, false);
		BlockPos planks = site.target(farm, farm.steps().get(3));
		Map<Item, Item> swaps = Map.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS);
		assertTrue(FarmProtection.structuralAt(farm, site, NO_SWAPS, planks, Blocks.OAK_PLANKS.getDefaultState()));
		assertFalse(FarmProtection.structuralAt(farm, site, NO_SWAPS, planks, Blocks.SPRUCE_PLANKS.getDefaultState()));
		assertTrue(FarmProtection.structuralAt(farm, site, swaps, planks, Blocks.SPRUCE_PLANKS.getDefaultState()));
		assertFalse(FarmProtection.structuralAt(farm, site, swaps, planks, Blocks.OAK_PLANKS.getDefaultState()));
	}

	@Test
	void mirroringMovesTheProtectedSpotsToTheFlippedSide() {
		FarmTemplate farm = farm();
		FarmSite plainSite = new FarmSite(TABLE, ANCHOR, Direction.SOUTH, false);
		FarmSite mirroredSite = new FarmSite(TABLE, ANCHOR, Direction.SOUTH, true);
		Set<BlockPos> plain = structure(farm, plainSite);
		Set<BlockPos> mirrored = structure(farm, mirroredSite);
		assertEquals(3, plain.size());
		assertEquals(3, mirrored.size());
		assertNotEquals(plain, mirrored);
		for (BlockPos pos : plain) {
			if (!mirrored.contains(pos)) {
				assertFalse(FarmProtection.structuralAt(farm, mirroredSite, NO_SWAPS, pos, Blocks.STONE.getDefaultState()), pos.toString());
			}
		}
	}

	private static Set<BlockPos> structure(FarmTemplate farm, FarmSite site) {
		Set<BlockPos> found = new HashSet<>();
		for (int index = 0; index < farm.steps().size(); index++) {
			BlockPos pos = site.target(farm, farm.steps().get(index));
			if (FarmProtection.structuralAt(farm, site, NO_SWAPS, pos, worldAt(farm, site, index))) {
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

		List<FarmAreas.Guard> guards = FarmAreas.read(areas.writeNbt(new NbtCompound()), null).guardsAt(inside);

		assertEquals(1, guards.size());
		assertEquals(OWNER, guards.get(0).ownerUuid());
		assertEquals(site, guards.get(0).site());
		assertTrue(FarmProtection.structuralAt(guards.get(0).layout(), guards.get(0).site(), guards.get(0).swaps(), inside,
				Blocks.STONE.getDefaultState()));
		assertTrue(areas.guardsAt(new BlockPos(500, 70, 500)).isEmpty());
		assertTrue(new FarmAreas().guardsAt(inside).isEmpty());
	}

	private static FarmDismantler takeDown(FarmTemplate farm, FarmSite site) {
		BitSet all = new BitSet();
		all.set(0, farm.steps().size());
		return new FarmDismantler(OWNER, farm, site, NO_SWAPS, all, all, TABLE, new FarmDismantler.Identity(KEY.instanceId(), OWNER, null));
	}

	@Test
	void aFarmBeingTakenDownStaysGuardedUntilItsAreaIsReleased() {
		FarmTemplate farm = farm();
		FarmSite site = new FarmSite(TABLE, ANCHOR, Direction.WEST, true);
		FarmDismantler job = takeDown(farm, site);
		FarmAreas areas = new FarmAreas();
		BlockPos inside = site.target(farm, farm.steps().get(0));

		areas.claim(job.areaKey(), site.footprint(farm), job.guard());

		List<FarmAreas.Guard> guards = areas.guardsAt(inside);
		assertEquals(1, guards.size());
		assertEquals(OWNER, guards.get(0).ownerUuid());
		assertEquals(KEY.instanceId(), guards.get(0).instanceId());
		assertTrue(FarmProtection.structuralAt(guards.get(0).layout(), guards.get(0).site(), guards.get(0).swaps(), inside,
				Blocks.STONE.getDefaultState()));
		assertTrue(areas.guardsAt(new BlockPos(500, 70, 500)).isEmpty());

		areas.release(job.areaKey());
		assertTrue(areas.guardsAt(inside).isEmpty());
	}

	@Test
	void aDiscardClaimHasNoFarmToGuard() {
		FarmTemplate farm = farm();
		FarmSite site = new FarmSite(TABLE, ANCHOR, Direction.WEST, true);
		FarmDismantler discard = new FarmDismantler(OWNER, farm.id(), farm.signature(), site, NO_SWAPS, new BitSet(), new BitSet(), 0);
		FarmAreas areas = new FarmAreas();

		areas.claim(discard.areaKey(), site.footprint(farm), discard.guard());

		assertTrue(areas.guardsAt(site.target(farm, farm.steps().get(0))).isEmpty());
	}

	@Test
	void aRestartedTakeDownGetsItsGuardBackFromItsJob() {
		FarmTemplate farm = farm();
		FarmSite site = new FarmSite(TABLE, ANCHOR, Direction.WEST, true);
		FarmDismantler job = takeDown(farm, site);
		FarmAreas areas = new FarmAreas();
		areas.claim(job.areaKey(), site.footprint(farm), job.guard());
		BlockPos inside = site.target(farm, farm.steps().get(0));

		FarmAreas restarted = FarmAreas.read(areas.writeNbt(new NbtCompound()), null);
		assertTrue(restarted.guardsAt(inside).isEmpty());
		restarted.guard(job.areaKey(), job.guard());

		assertEquals(1, restarted.guardsAt(inside).size());
		assertEquals(OWNER, restarted.guardsAt(inside).get(0).ownerUuid());
		restarted.release(job.areaKey());
		restarted.guard(job.areaKey(), job.guard());
		assertTrue(restarted.guardsAt(inside).isEmpty());
	}

	/** The search {@link FarmProtection#structuralAt} did before its index: the first step at the position decides. */
	private static boolean searched(FarmTemplate template, FarmSite site, Map<Item, Item> swaps, BlockPos pos, BlockState current) {
		if (current.isAir() || current.getBlock() instanceof FluidBlock || FarmProtection.harvestable(current)) {
			return false;
		}
		for (FarmStep step : template.steps()) {
			if (!step.isEntity() && step.state() != null && site.target(template, step).equals(pos)) {
				return FarmProtection.structural(step.state())
						&& BlockMatch.stillFarm(current, site.orient(MaterialSwaps.apply(step.state(), swaps)));
			}
		}
		return false;
	}

	@Test
	void theIndexedLookupAnswersLikeTheSearchForEverySiteSwapAndBlock() {
		FarmTemplate farm = farm();
		FarmTemplate overlapping = new FarmTemplate("overlapping", List.of(
				FarmStep.block(0, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE),
				FarmStep.block(0, 0, 0, Blocks.OAK_PLANKS.getDefaultState(), Items.OAK_PLANKS),
				FarmStep.block(1, 0, 0, Blocks.WHEAT.getDefaultState(), Items.WHEAT_SEEDS)), List.of(), TABLE);
		List<BlockState> blocks = List.of(Blocks.AIR.getDefaultState(), Blocks.STONE.getDefaultState(), Blocks.FARMLAND.getDefaultState(),
				Blocks.DIRT.getDefaultState(), Blocks.WHEAT.getDefaultState(), Blocks.OAK_PLANKS.getDefaultState(),
				Blocks.SPRUCE_PLANKS.getDefaultState(), Blocks.WATER.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
		Map<Item, Item> swaps = Map.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS);
		// The same template object is asked for several sites, so the index is also checked where it does not apply.
		for (FarmTemplate template : List.of(farm, overlapping)) {
			for (Direction facing : Direction.Type.HORIZONTAL) {
				for (boolean mirrored : new boolean[]{false, true}) {
					for (BlockPos anchor : new BlockPos[]{ANCHOR, null}) {
						FarmSite site = new FarmSite(TABLE, anchor, facing, mirrored);
						Set<BlockPos> positions = new HashSet<>(Set.of(new BlockPos(500, 70, 500)));
						for (FarmStep step : template.steps()) {
							BlockPos target = site.target(template, step);
							positions.add(target);
							for (Direction side : Direction.values()) {
								positions.add(target.offset(side));
							}
						}
						for (BlockPos pos : positions) {
							for (BlockState block : blocks) {
								for (Map<Item, Item> map : List.of(NO_SWAPS, swaps)) {
									String label = template.id() + " " + facing + " mirrored=" + mirrored + " anchor=" + (anchor != null) + " at " + pos + " " + block;
									assertEquals(searched(template, site, map, pos, block),
											FarmProtection.structuralAt(template, site, map, pos, block), label);
								}
							}
						}
					}
				}
			}
		}
	}

	private static final UUID OTHER_OWNER = UUID.fromString("50000000-0000-0000-0000-000000000005");
	private static final FarmSite BLAST_SITE = new FarmSite(TABLE, ANCHOR, Direction.EAST, false);

	private static FarmAreas.Guard guardOf(UUID owner) {
		return new FarmAreas.Guard(UUID.randomUUID(), owner, farm(), BLAST_SITE, NO_SWAPS);
	}

	/** The farm's step positions (stone, farmland, wheat, planks, water) as an explosion's list holds them, plus one far away. */
	private static List<BlockPos> blastList() {
		FarmTemplate farm = farm();
		List<BlockPos> list = new ArrayList<>();
		farm.steps().forEach(step -> list.add(BLAST_SITE.target(farm, step)));
		list.add(new BlockPos(500, 70, 500));
		return list;
	}

	private static BlockState blastState(BlockPos pos) {
		FarmTemplate farm = farm();
		for (int index = 0; index < farm.steps().size(); index++) {
			if (BLAST_SITE.target(farm, farm.steps().get(index)).equals(pos)) {
				return worldAt(farm, BLAST_SITE, index);
			}
		}
		return Blocks.STONE.getDefaultState();
	}

	private static List<BlockPos> blast(boolean server, boolean ownerProtects, UUID cause, FarmAreas.Guard... guards) {
		List<BlockPos> list = blastList();
		FarmProtection.filterExplosion(server, List.of(guards), owner -> ownerProtects, cause, list, FarmProtectionTest::blastState);
		return list;
	}

	@Test
	void anExplosionLosesTheStructureOfAProtectedFarmButNotItsProduceOrOtherBlocks() {
		FarmTemplate farm = farm();
		List<BlockPos> left = blast(true, true, STRANGER, guardOf(OWNER));
		// Stone, farmland and planks are structure; wheat, water and the block far away are not the farm's to protect.
		assertEquals(List.of(BLAST_SITE.target(farm, farm.steps().get(2)), BLAST_SITE.target(farm, farm.steps().get(4)), new BlockPos(500, 70, 500)), left);
	}

	@Test
	void anExplosionWithNoCauseIsFilteredToo() {
		assertEquals(3, blast(true, true, null, guardOf(OWNER)).size());
	}

	@Test
	void anExplosionTheOwnerCausedIsNotFilteredForTheirFarm() {
		assertEquals(blastList(), blast(true, true, OWNER, guardOf(OWNER)));
	}

	@Test
	void theOwnersExemptionDoesNotCoverAnotherPlayersFarm() {
		List<BlockPos> left = blast(true, true, OWNER, guardOf(OWNER), guardOf(OTHER_OWNER));
		assertEquals(3, left.size());
	}

	@Test
	void anExplosionIsLeftAloneWhenTheOwnerOrTheServerTurnedProtectionOff() {
		assertEquals(blastList(), blast(true, false, STRANGER, guardOf(OWNER)));
		assertEquals(blastList(), blast(false, true, STRANGER, guardOf(OWNER)));
		assertEquals(blastList(), blast(true, true, STRANGER));
	}

	@Test
	void anExplosionOnlyLooksUpBlocksForGuardsThatApply() {
		int[] lookups = {0};
		List<BlockPos> list = blastList();
		FarmProtection.filterExplosion(true, List.of(guardOf(OWNER)), owner -> false, STRANGER, list, pos -> {
			lookups[0]++;
			return Blocks.STONE.getDefaultState();
		});
		FarmProtection.filterExplosion(true, List.of(guardOf(OWNER)), owner -> true, OWNER, list, pos -> {
			lookups[0]++;
			return Blocks.STONE.getDefaultState();
		});
		assertEquals(0, lookups[0]);
	}

	@Test
	void anEndermanIsStoppedOnlyNearAFarmWhoseOwnerProtectsIt() {
		assertTrue(FarmProtection.endermanBlocked(true, List.of(guardOf(OWNER)), owner -> true));
		assertTrue(FarmProtection.endermanBlocked(true, List.of(guardOf(OWNER), guardOf(OTHER_OWNER)), owner -> owner.equals(OTHER_OWNER)));
		assertFalse(FarmProtection.endermanBlocked(true, List.of(guardOf(OWNER)), owner -> false));
		assertFalse(FarmProtection.endermanBlocked(false, List.of(guardOf(OWNER)), owner -> true));
		assertFalse(FarmProtection.endermanBlocked(true, List.of(), owner -> true));
	}

	@Test
	void guardsInFindsTheGuardedAreasThatTouchTheBox() {
		FarmTemplate farm = farm();
		FarmAreas areas = new FarmAreas();
		areas.recordFinished(OWNER, farm, BLAST_SITE, Map.of());
		BlockPos inside = BLAST_SITE.target(farm, farm.steps().get(0));
		Box around = new Box(inside).expand(1);

		assertEquals(1, areas.guardsIn(around).size());
		assertEquals(OWNER, areas.guardsIn(around).get(0).ownerUuid());
		assertEquals(areas.guardsAt(inside), areas.guardsIn(new Box(inside)));
		// A box that only overlaps the farm's edge still counts; one beside it does not.
		assertEquals(1, areas.guardsIn(new Box(inside).offset(-0.5, 0, 0)).size());
		assertTrue(areas.guardsIn(new Box(new BlockPos(500, 70, 500)).expand(3)).isEmpty());
		assertTrue(new FarmAreas().guardsIn(around).isEmpty());

		FarmAreas taking = new FarmAreas();
		FarmDismantler job = takeDown(farm, BLAST_SITE);
		taking.claim(job.areaKey(), BLAST_SITE.footprint(farm), job.guard());
		assertEquals(1, taking.guardsIn(around).size());
		FarmDismantler discard = new FarmDismantler(OWNER, farm.id(), farm.signature(), BLAST_SITE, NO_SWAPS, new BitSet(), new BitSet(), 0);
		FarmAreas discarding = new FarmAreas();
		discarding.claim(discard.areaKey(), BLAST_SITE.footprint(farm), discard.guard());
		assertTrue(discarding.guardsIn(around).isEmpty());
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
