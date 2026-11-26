package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.FarmOptions;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The build queue: what a session saves of it, how an old or damaged save reads, when the next entry starts and which spots it blocks. */
class SessionQueueTest {
	private static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000002");
	private static final Identifier OVERWORLD = Identifier.of("minecraft", "overworld");
	private static final Identifier NETHER = Identifier.of("minecraft", "the_nether");
	/** Read after the game's registries are loaded, which the farm options need. */
	private static String CANE;

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
		CANE = FarmOptions.SUGAR_CANE.id();
	}

	private static TableSession session() {
		return new TableSession(null, PLAYER);
	}

	private static NbtCompound entry(String farm, int x, Map<Item, Item> swaps) {
		return SessionQueue.entry(farm, new AimedSpot(new BlockPos(x, 64, 0), Direction.EAST, x % 2 == 0, OVERWORLD), swaps);
	}

	private static NbtList entries(int count) {
		NbtList list = new NbtList();
		for (int index = 0; index < count; index++) {
			list.add(entry(CANE, index * 100, Map.of()));
		}
		return list;
	}

	@Test
	void theNextEntryStartsOnlyWhenTheBuildIsRecordedAndTheOwnerIsOnline() {
		assertTrue(SessionQueue.shouldStartNext(true, true, false, true, false));
		assertFalse(SessionQueue.shouldStartNext(false, true, false, true, false), "a build still running");
		assertFalse(SessionQueue.shouldStartNext(true, false, false, true, false), "finished but not recorded yet");
		assertFalse(SessionQueue.shouldStartNext(true, true, true, true, false), "nothing queued");
		assertFalse(SessionQueue.shouldStartNext(true, true, false, false, false), "owner away");
		assertFalse(SessionQueue.shouldStartNext(true, true, false, true, true), "a finished repair is not the queue's farm");
	}

	/** A queue alone does not block restoring a kept project, and the restored project keeps its own line first, within the cap. */
	@Test
	void aQueueOnlySessionHoldsNoProjectAndWaitsBehindARestoredOne() {
		NbtCompound savedQueue = new NbtCompound();
		savedQueue.put("Queue", entries(5));
		TableSession current = session();
		current.read(savedQueue, null);
		assertFalse(current.isEmpty());
		assertFalse(current.hasProject());
		NbtCompound withFarm = new NbtCompound();
		withFarm.putString("SelectedFarm", CANE);
		TableSession farm = session();
		farm.read(withFarm, null);
		assertTrue(farm.hasProject());

		NbtCompound keptNbt = new NbtCompound();
		NbtList kept = new NbtList();
		for (int index = 0; index < 5; index++) {
			kept.add(entry(FarmOptions.CROP.id(), 1000 + index, Map.of()));
		}
		keptNbt.put("Queue", kept);
		TableSession restored = session();
		restored.read(keptNbt, null);
		restored.queueBehind(current);
		assertEquals(SessionQueue.MAX, restored.queuedFarms().size());
		NbtCompound written = new NbtCompound();
		restored.write(written, null);
		NbtList merged = written.getListOrEmpty("Queue");
		assertEquals(kept.getCompoundOrEmpty(0), merged.getCompoundOrEmpty(0), "the project's own queue stays first");
		assertEquals(kept.getCompoundOrEmpty(4), merged.getCompoundOrEmpty(4));
		assertEquals(entries(5).getCompoundOrEmpty(0), merged.getCompoundOrEmpty(5), "then the current queue, cut at the cap");
	}

	@Test
	void aSessionSavedBeforeTheQueueReadsEmptyAndWritesNoQueue() {
		NbtCompound old = new NbtCompound();
		old.putString("SelectedFarm", CANE);
		old.putInt("Page", 1);
		TableSession read = session();
		read.read(old, null);
		assertTrue(read.queuedFarms().isEmpty());
		NbtCompound written = new NbtCompound();
		read.write(written, null);
		assertFalse(written.contains("Queue"));
	}

	@Test
	void aQueueKeepsOrderAndContentThroughASaveAndLoad() {
		Map<Item, Item> swaps = Map.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS);
		NbtCompound saved = new NbtCompound();
		NbtList list = new NbtList();
		list.add(entry(FarmOptions.CROP.id(), 10, Map.of()));
		list.add(entry(CANE, 20, swaps));
		list.add(entry(FarmOptions.BAMBOO.id(), 30, Map.of()));
		saved.put("Queue", list);
		TableSession first = session();
		first.read(saved, null);
		NbtCompound written = new NbtCompound();
		first.write(written, null);
		TableSession back = session();
		back.read(written, null);
		assertEquals(List.of(FarmOptions.CROP.id(), CANE, FarmOptions.BAMBOO.id()), back.queuedFarms());
		assertEquals(list, written.getListOrEmpty("Queue"));
		NbtCompound second = written.getListOrEmpty("Queue").getCompoundOrEmpty(1);
		assertEquals(swaps, MaterialLedger.readSwaps(second));
		assertEquals(AimedSpot.fromNbt(list.getCompoundOrEmpty(1).getCompoundOrEmpty("Aim")), AimedSpot.fromNbt(second.getCompoundOrEmpty("Aim")));
	}

	@Test
	void aSavedQueueLongerThanTheCapReadsAsTheFirstEight() {
		NbtCompound saved = new NbtCompound();
		saved.put("Queue", entries(13));
		TableSession read = session();
		read.read(saved, null);
		assertEquals(SessionQueue.MAX, read.queuedFarms().size());
		NbtCompound written = new NbtCompound();
		read.write(written, null);
		assertEquals(SessionQueue.MAX, written.getListOrEmpty("Queue").size());
		assertEquals(entries(SessionQueue.MAX), written.getListOrEmpty("Queue"), "the first eight stay, in order");
	}

	@Test
	void entriesThatAreNotCompoundsAreSkippedAndExtraKeysDropped() {
		NbtCompound strings = new NbtCompound();
		NbtList names = new NbtList();
		names.add(NbtString.of("not a plan"));
		strings.put("Queue", names);
		TableSession read = session();
		read.read(strings, null);
		assertTrue(read.queuedFarms().isEmpty());

		NbtCompound extra = entry(CANE, 5, Map.of());
		extra.putString("Junk", "x".repeat(1000));
		NbtCompound saved = new NbtCompound();
		NbtList list = new NbtList();
		list.add(extra);
		saved.put("Queue", list);
		read.read(saved, null);
		NbtCompound written = new NbtCompound();
		read.write(written, null);
		assertFalse(written.getListOrEmpty("Queue").getCompoundOrEmpty(0).contains("Junk"));
		assertEquals(List.of(CANE), read.queuedFarms());
	}

	/** A queue alone is the player's plan: the session is saved, and a broken table keeps it as a project. */
	@Test
	void aQueueOnlySessionIsNotEmptyAndParksAsAProject() {
		TableSession empty = session();
		assertTrue(empty.isEmpty());
		assertEquals(TableSession.ParkKind.NOTHING, empty.parkKind());

		NbtCompound saved = new NbtCompound();
		saved.put("Queue", entries(1));
		TableSession queued = session();
		queued.read(saved, null);
		assertFalse(queued.isEmpty());
		assertEquals(TableSession.ParkKind.PROJECT, queued.parkKind());
	}

	@Test
	void theClientCopyCarriesOnlyTheFarmNames() {
		NbtCompound saved = new NbtCompound();
		saved.put("Queue", entries(3));
		TableSession read = session();
		read.read(saved, null);
		NbtList trimmed = read.writeForClient().getListOrEmpty("Queue");
		assertEquals(3, trimmed.size());
		assertEquals(Set.of("SelectedFarm"), trimmed.getCompoundOrEmpty(0).getKeys());
		assertFalse(session().writeForClient().contains("Queue"));
	}

	@Test
	void theQueueHoldsEightAndPopsInOrder() {
		SessionQueue queue = new SessionQueue();
		for (int index = 0; index < SessionQueue.MAX; index++) {
			assertFalse(queue.full());
			queue.add(entry(CANE, index, Map.of()));
		}
		assertTrue(queue.full());
		assertEquals(entry(CANE, 0, Map.of()), queue.pop());
		assertFalse(queue.full());
		assertEquals(SessionQueue.MAX - 1, queue.size());
		queue.clear();
		assertNull(queue.pop());
	}

	/** Queued spots reserve nothing, but the player's own queue refuses a spot that would overlap it, in the same world only. */
	@Test
	void aSpotOverlapsOnlyAQueuedFarmInTheSameWorld() {
		SessionQueue queue = new SessionQueue();
		NbtCompound queued = entry(CANE, 100, Map.of());
		queue.add(queued);
		Box queuedBox = SessionQueue.footprint(queued, OVERWORLD);
		assertNotNull(queuedBox);
		assertTrue(queue.overlaps(OVERWORLD, queuedBox));
		assertTrue(queue.overlaps(OVERWORLD, SessionQueue.footprint(entry(CANE, 101, Map.of()), OVERWORLD)), "shifted by one block");
		assertFalse(queue.overlaps(OVERWORLD, SessionQueue.footprint(entry(CANE, 1000, Map.of()), OVERWORLD)), "far away");
		assertFalse(queue.overlaps(NETHER, queuedBox), "another world");
		assertNull(SessionQueue.footprint(queued, NETHER));
	}

	@Test
	void anEntryWithAnUnknownFarmOrNoSpotBlocksNothing() {
		SessionQueue queue = new SessionQueue();
		queue.add(entry("the_dream_table:gone", 100, Map.of()));
		NbtCompound noSpot = new NbtCompound();
		noSpot.putString("SelectedFarm", CANE);
		queue.add(noSpot);
		assertFalse(queue.overlaps(OVERWORLD, new Box(-10_000, -64, -10_000, 10_000, 320, 10_000)));
	}
}
