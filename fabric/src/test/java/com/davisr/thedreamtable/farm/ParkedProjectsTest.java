package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.block.entity.AimedSpot;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Projects kept from broken tables come back to their own player, oldest first, bounded, and saved as they were. */
class ParkedProjectsTest {
	private static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID OTHER = UUID.fromString("20000000-0000-0000-0000-000000000002");
	private static final Identifier OVERWORLD = new Identifier("minecraft", "overworld");
	private static final Identifier NETHER = new Identifier("minecraft", "the_nether");
	private static final BlockPos ANCHOR = new BlockPos(640, 70, -20);

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	/** A started build as a broken table keeps it: its farm, the world it stands in, and its spot under the keys {@code TableSession.write} saves. */
	private static NbtCompound build(Identifier world, BlockPos anchor, Direction facing) {
		NbtCompound nbt = project(FarmOptions.SUGAR_CANE.id());
		nbt.putString("MovedBuildDimension", world.toString());
		nbt.putString("FarmFacing", facing.asString());
		nbt.putInt("AnchorX", anchor.getX());
		nbt.putInt("AnchorY", anchor.getY());
		nbt.putInt("AnchorZ", anchor.getZ());
		return nbt;
	}

	/** The box the build's farm occupies at its spot, as the table claims it. The table position is not saved with a kept build, so any is used. */
	private static Box footprint(BlockPos anchor, Direction facing) {
		return new FarmSite(new BlockPos(0, 0, 0), anchor, facing).footprint(FarmTemplates.get(FarmOptions.SUGAR_CANE.id()));
	}

	private static NbtCompound project(String farm) {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("SelectedFarm", farm);
		return nbt;
	}

	private static String take(ParkedProjects parked, UUID player) {
		List<String> taken = new ArrayList<>();
		parked.restore(player, saved -> taken.add(saved.getString("SelectedFarm")));
		return taken.isEmpty() ? null : taken.get(0);
	}

	@Test
	void projectsComeBackOldestFirstAndOnlyOnce() {
		ParkedProjects parked = new ParkedProjects();
		assertTrue(parked.park(PLAYER, project("first")));
		assertTrue(parked.park(PLAYER, project("second")));
		assertEquals("first", take(parked, PLAYER));
		assertEquals("second", take(parked, PLAYER));
		assertEquals(null, take(parked, PLAYER));
		assertEquals(0, parked.count(PLAYER));
	}

	@Test
	void eachPlayerGetsOnlyTheirOwn() {
		ParkedProjects parked = new ParkedProjects();
		parked.park(OTHER, project("theirs"));
		assertFalse(parked.restore(PLAYER, saved -> true));
		assertEquals(1, parked.count(OTHER));
	}

	@Test
	void aRefusedProjectStaysAsSavedAndTheNextIsOffered() {
		ParkedProjects parked = new ParkedProjects();
		parked.park(PLAYER, project("unreadable"));
		parked.park(PLAYER, project("good"));
		List<String> offered = new ArrayList<>();
		assertTrue(parked.restore(PLAYER, saved -> {
			offered.add(saved.getString("SelectedFarm"));
			return !saved.getString("SelectedFarm").equals("unreadable");
		}));
		assertEquals(List.of("unreadable", "good"), offered);
		assertEquals(1, parked.count(PLAYER));
		assertEquals("unreadable", take(parked, PLAYER));
	}

	@Test
	void theTakerCannotChangeWhatIsKept() {
		ParkedProjects parked = new ParkedProjects();
		NbtCompound original = project("kept");
		parked.park(PLAYER, original);
		original.putString("SelectedFarm", "changed after parking");
		parked.restore(PLAYER, saved -> {
			saved.putString("SelectedFarm", "changed by a refusing taker");
			return false;
		});
		assertEquals("kept", take(parked, PLAYER));
	}

	@Test
	void theLimitRefusesInsteadOfLosingWhatIsKept() {
		ParkedProjects parked = new ParkedProjects();
		for (int index = 0; index < ParkedProjects.MAX_PER_PLAYER; index++) {
			assertTrue(parked.park(PLAYER, project("farm_" + index)));
		}
		assertFalse(parked.canPark(PLAYER));
		assertFalse(parked.park(PLAYER, project("one_more")));
		assertEquals(ParkedProjects.MAX_PER_PLAYER, parked.count(PLAYER));
		assertTrue(parked.canPark(OTHER));
	}

	@Test
	void savedProjectsReadBackUnchanged() {
		ParkedProjects parked = new ParkedProjects();
		NbtCompound full = project("farm");
		full.putInt("BuildIndex", 42);
		full.putString("MovedBuildDimension", "minecraft:the_nether");
		full.putString("SomethingALaterVersionWrites", "kept");
		parked.park(PLAYER, full);
		parked.park(OTHER, project("theirs"));
		ParkedProjects read = ParkedProjects.read(parked.writeNbt(new NbtCompound()), null);
		assertEquals(1, read.count(PLAYER));
		assertEquals(1, read.count(OTHER));
		List<NbtCompound> back = new ArrayList<>();
		read.restore(PLAYER, back::add);
		assertEquals(full, back.get(0));
	}

	/** A project kept with its aimed spot comes back with the same spot, saved and read back through the file. */
	@Test
	void aKeptSpotComesBackWithTheProject() {
		AimedSpot spot = new AimedSpot(new BlockPos(640, 70, -20), Direction.EAST, true, new Identifier("minecraft", "overworld"));
		NbtCompound kept = project("farm");
		kept.put("Aim", spot.toNbt());
		ParkedProjects parked = new ParkedProjects();
		parked.park(PLAYER, kept);
		ParkedProjects read = ParkedProjects.read(parked.writeNbt(new NbtCompound()), null);
		List<NbtCompound> back = new ArrayList<>();
		read.restore(PLAYER, back::add);
		assertEquals(spot, AimedSpot.fromNbt(back.get(0).getCompound("Aim")));
		assertEquals("farm", back.get(0).getString("SelectedFarm"));
	}

	@Test
	void aCorruptFileStaysBounded() {
		NbtCompound players = new NbtCompound();
		NbtList many = new NbtList();
		for (int index = 0; index < ParkedProjects.MAX_PER_PLAYER + 5; index++) {
			many.add(project("farm_" + index));
		}
		players.put(PLAYER.toString(), many);
		players.put("not-a-uuid", new NbtList());
		players.put(OTHER.toString(), new NbtList());
		NbtCompound nbt = new NbtCompound();
		nbt.put("Players", players);
		ParkedProjects read = ParkedProjects.read(nbt, null);
		assertEquals(ParkedProjects.MAX_PER_PLAYER, read.count(PLAYER));
		assertEquals(0, read.count(OTHER));
		assertTrue(read.canPark(OTHER));
	}

	/** A kept build keeps its farm's area in the world it stands in, so nothing else may build or clear there while it waits. */
	@Test
	void aKeptBuildReservesItsAreaInItsWorldOnly() {
		Box area = footprint(ANCHOR, Direction.SOUTH);
		ParkedProjects parked = new ParkedProjects();
		parked.park(PLAYER, build(OVERWORLD, ANCHOR, Direction.SOUTH));
		assertTrue(parked.reserves(OVERWORLD, box -> box.intersects(area)));
		assertFalse(parked.reserves(NETHER, box -> box.intersects(area)));
		assertFalse(parked.reserves(OVERWORLD, box -> box.intersects(footprint(ANCHOR.add(100, 0, 0), Direction.SOUTH))));
	}

	private static final ParkedProjects.Seat SEAT = new ParkedProjects.Seat(PLAYER, OVERWORLD, new BlockPos(0, 64, 0).asLong());

	/**
	 * Restoring the build into a table holds it: the build keeps reserving its area for others, and it is lifted only when the
	 * seat releases it (resumed, discarded or the table breaks), never by another table's seat.
	 */
	@Test
	void aRestoredBuildStaysReservedForOthersUntilItsSeatReleasesIt() {
		Box area = footprint(ANCHOR, Direction.SOUTH);
		ParkedProjects parked = new ParkedProjects();
		parked.park(PLAYER, build(OVERWORLD, ANCHOR, Direction.SOUTH));
		List<NbtCompound> back = new ArrayList<>();
		assertTrue(parked.restore(PLAYER, saved -> back.add(saved)));
		parked.hold(SEAT, back.get(0));
		assertEquals(0, parked.count(PLAYER));
		assertTrue(parked.reserves(OVERWORLD, box -> box.intersects(area)));
		parked.release(new ParkedProjects.Seat(PLAYER, OVERWORLD, new BlockPos(9, 64, 9).asLong()));
		assertTrue(parked.reserves(OVERWORLD, box -> box.intersects(area)));
		parked.release(SEAT);
		assertFalse(parked.reserves(OVERWORLD, box -> box.intersects(area)));
	}

	/** The owner resumes from the table holding the build, so its own hold does not block its spot; another player's seat or table still does. */
	@Test
	void theOwnersOwnHoldDoesNotBlockTheirResume() {
		Box area = footprint(ANCHOR, Direction.SOUTH);
		ParkedProjects parked = new ParkedProjects();
		parked.hold(SEAT, build(OVERWORLD, ANCHOR, Direction.SOUTH));
		assertTrue(parked.reserves(OVERWORLD, box -> box.intersects(area)));
		assertFalse(parked.reserves(OVERWORLD, box -> box.intersects(area), SEAT));
		assertTrue(parked.reserves(OVERWORLD, box -> box.intersects(area),
				new ParkedProjects.Seat(OTHER, OVERWORLD, SEAT.table())));
		assertTrue(parked.reserves(OVERWORLD, box -> box.intersects(area),
				new ParkedProjects.Seat(PLAYER, OVERWORLD, new BlockPos(9, 64, 9).asLong())));
	}

	/** Held builds are saved with their seat and come back held in the same seat; a file from before holds reads as holding nothing. */
	@Test
	void aHeldBuildIsSavedWithItsSeat() {
		Box area = footprint(ANCHOR, Direction.SOUTH);
		ParkedProjects parked = new ParkedProjects();
		parked.hold(SEAT, build(OVERWORLD, ANCHOR, Direction.SOUTH));
		ParkedProjects read = ParkedProjects.read(parked.writeNbt(new NbtCompound()), null);
		assertTrue(read.reserves(OVERWORLD, box -> box.intersects(area)));
		assertFalse(read.reserves(OVERWORLD, box -> box.intersects(area), SEAT));
		read.release(SEAT);
		assertFalse(read.reserves(OVERWORLD, box -> box.intersects(area)));
		NbtCompound old = new NbtCompound();
		NbtCompound players = new NbtCompound();
		players.put(PLAYER.toString(), new NbtList());
		old.put("Players", players);
		assertFalse(ParkedProjects.read(old, null).reserves(OVERWORLD, box -> box.intersects(area)));
	}

	/** Builds kept under the NO_PLAYER key belong to no one and reserve nothing, so their area is never held forever. */
	@Test
	void aBuildOfNoPlayerReservesNothing() {
		ParkedProjects parked = new ParkedProjects();
		parked.park(ParkedProjects.NO_PLAYER, build(OVERWORLD, ANCHOR, Direction.SOUTH));
		assertFalse(parked.reserves(OVERWORLD, box -> true));
	}

	/**
	 * A build whose farm is not known yet reserves nothing, and must not stay that way: once the farm is known the reservation
	 * appears, without any park or restore in between.
	 */
	@Test
	void aBuildWhoseFarmIsNotKnownYetIsLookedUpAgain() {
		String id = "saved:reservation_probe#1";
		FarmTemplate sugar = FarmTemplates.get(FarmOptions.SUGAR_CANE.id());
		Box area = footprint(ANCHOR, Direction.SOUTH);
		NbtCompound project = build(OVERWORLD, ANCHOR, Direction.SOUTH);
		project.putString("SelectedFarm", id);
		ParkedProjects parked = new ParkedProjects();
		parked.park(PLAYER, project);
		assertFalse(parked.reserves(OVERWORLD, box -> box.intersects(area)));
		FarmTemplates.keepSaved(new FarmTemplate(id, sugar.steps(), sugar.materials(), sugar.center(), sugar.mirrorSum()));
		try {
			assertTrue(parked.reserves(OVERWORLD, box -> box.intersects(area)));
		} finally {
			FarmTemplates.clearSaved();
		}
	}

	/** A build the owner could not take yet stays on record with its reservation, and the reservation follows the file. */
	@Test
	void aRefusedBuildKeepsItsReservationThroughASave() {
		Box area = footprint(ANCHOR, Direction.SOUTH);
		ParkedProjects parked = new ParkedProjects();
		parked.park(PLAYER, build(OVERWORLD, ANCHOR, Direction.SOUTH));
		assertFalse(parked.restore(PLAYER, saved -> false));
		assertTrue(parked.reserves(OVERWORLD, box -> box.intersects(area)));
		ParkedProjects read = ParkedProjects.read(parked.writeNbt(new NbtCompound()), null);
		assertTrue(read.reserves(OVERWORLD, box -> box.intersects(area)));
	}

	/** Projects without a spot (a farm, its materials) reserve nothing, and a parked build is seen as soon as it is parked. */
	@Test
	void onlyBuildsWithASpotReserveAndNewOnesAreSeenAtOnce() {
		ParkedProjects parked = new ParkedProjects();
		parked.park(PLAYER, project(FarmOptions.SUGAR_CANE.id()));
		assertFalse(parked.reserves(OVERWORLD, box -> true));
		parked.park(OTHER, build(OVERWORLD, ANCHOR, Direction.SOUTH));
		assertTrue(parked.reserves(OVERWORLD, box -> box.intersects(footprint(ANCHOR, Direction.SOUTH))));
	}

	/** The area follows the build's facing, not a fixed one: the east-facing build reserves what its east footprint covers, and nothing more. */
	@Test
	void theReservationFollowsTheFacing() {
		Box east = footprint(ANCHOR, Direction.EAST);
		Box south = footprint(ANCHOR, Direction.SOUTH);
		assertNotEquals(east, south);
		ParkedProjects parked = new ParkedProjects();
		parked.park(PLAYER, build(OVERWORLD, ANCHOR, Direction.EAST));
		assertTrue(parked.reserves(OVERWORLD, box -> box.intersects(east)));
		assertEquals(east.intersects(south), parked.reserves(OVERWORLD, box -> box.intersects(south)));
	}
}
