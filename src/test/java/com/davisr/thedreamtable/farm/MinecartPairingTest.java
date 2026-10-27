package com.davisr.thedreamtable.farm;

import org.junit.jupiter.api.Test;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

import static com.davisr.thedreamtable.farm.MinecartPairing.Action.BLOCKED;
import static com.davisr.thedreamtable.farm.MinecartPairing.Action.COMPLETE;
import static com.davisr.thedreamtable.farm.MinecartPairing.Action.CREATE_CART_FOR_PASSENGER;
import static com.davisr.thedreamtable.farm.MinecartPairing.Action.CREATE_PAIR;
import static com.davisr.thedreamtable.farm.MinecartPairing.Action.CREATE_PASSENGER_FOR_CART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MinecartPairingTest {
	@Test
	void intactPairIsComplete() {
		assertEquals(COMPLETE, MinecartPairing.decide(new MinecartPairing.Snapshot(1, 0, 1, 0, 1)));
	}

	@Test
	void loneFreePassengerNeedsOnlyACart() {
		assertEquals(CREATE_CART_FOR_PASSENGER, MinecartPairing.decide(new MinecartPairing.Snapshot(1, 1, 0, 0, 0)));
	}

	@Test
	void loneEmptyVanillaCartNeedsOnlyAPassenger() {
		assertEquals(CREATE_PASSENGER_FOR_CART, MinecartPairing.decide(new MinecartPairing.Snapshot(0, 0, 1, 1, 0)));
	}

	@Test
	void emptyCellNeedsTheWholePair() {
		assertEquals(CREATE_PAIR, MinecartPairing.decide(new MinecartPairing.Snapshot(0, 0, 0, 0, 0)));
	}

	@Test
	void occupiedCartBlocksRepair() {
		assertEquals(BLOCKED, MinecartPairing.decide(new MinecartPairing.Snapshot(0, 0, 1, 0, 0)));
	}

	@Test
	void passengerInAnotherVehicleBlocksRepair() {
		assertEquals(BLOCKED, MinecartPairing.decide(new MinecartPairing.Snapshot(1, 0, 0, 0, 0)));
	}

	@Test
	void loosePassengerAndLooseCartBlockRepair() {
		assertEquals(BLOCKED, MinecartPairing.decide(new MinecartPairing.Snapshot(1, 1, 1, 1, 0)));
	}

	@Test
	void multipleCandidatesBlockRepair() {
		assertEquals(BLOCKED, MinecartPairing.decide(new MinecartPairing.Snapshot(2, 2, 0, 0, 0)));
		assertEquals(BLOCKED, MinecartPairing.decide(new MinecartPairing.Snapshot(0, 0, 2, 2, 0)));
	}

	@Test
	void candidatesAreFilteredByTheirOwnBlockPosition() {
		Positioned local = new Positioned(new Vec3d(4.5, 8.0625, -1.5));
		Positioned east = new Positioned(new Vec3d(5.5, 8.0625, -1.5));
		Positioned above = new Positioned(new Vec3d(4.5, 9.0625, -1.5));

		assertEquals(List.of(local), MinecartPairing.inCell(new BlockPos(4, 8, -2), List.of(local, east, above), Positioned::position));
	}

	@Test
	void failedMountDiscardsOnlyEntitiesCreatedByThatAttempt() {
		FakeEntity unrelated = new FakeEntity("unrelated");

		FakeEntity existingPassenger = new FakeEntity("existing passenger");
		FakeOperations cartAttempt = new FakeOperations(unrelated);
		assertEquals(FarmSpawns.Result.FAILED,
				FarmSpawns.spawnMinecart(CREATE_CART_FOR_PASSENGER, existingPassenger, null, cartAttempt));
		assertEquals(List.of(cartAttempt.createdCart), cartAttempt.discarded);
		assertFalse(cartAttempt.discarded.contains(existingPassenger));

		FakeEntity existingCart = new FakeEntity("existing cart");
		FakeOperations passengerAttempt = new FakeOperations(unrelated);
		assertEquals(FarmSpawns.Result.FAILED,
				FarmSpawns.spawnMinecart(CREATE_PASSENGER_FOR_CART, null, existingCart, passengerAttempt));
		assertEquals(List.of(passengerAttempt.createdPassenger), passengerAttempt.discarded);
		assertFalse(passengerAttempt.discarded.contains(existingCart));

		FakeOperations pairAttempt = new FakeOperations(unrelated);
		assertEquals(FarmSpawns.Result.FAILED, FarmSpawns.spawnMinecart(CREATE_PAIR, null, null, pairAttempt));
		assertEquals(List.of(pairAttempt.createdCart, pairAttempt.createdPassenger), pairAttempt.discarded);
		assertFalse(pairAttempt.discarded.contains(unrelated));
	}

	private record Positioned(Vec3d position) {
	}

	private record FakeEntity(String name) {
	}

	private static final class FakeOperations implements FarmSpawns.PairingOperations<FakeEntity> {
		private final FakeEntity unrelated;
		private final List<FakeEntity> discarded = new ArrayList<>();
		private FakeEntity createdPassenger;
		private FakeEntity createdCart;

		private FakeOperations(FakeEntity unrelated) {
			this.unrelated = unrelated;
		}

		@Override
		public FakeEntity createPassenger() {
			createdPassenger = new FakeEntity("created passenger");
			return createdPassenger;
		}

		@Override
		public FakeEntity createCart() {
			createdCart = new FakeEntity("created cart");
			return createdCart;
		}

		@Override
		public boolean spawn(FakeEntity entity) {
			return entity != unrelated;
		}

		@Override
		public boolean mount(FakeEntity passenger, FakeEntity cart) {
			return false;
		}

		@Override
		public void discard(FakeEntity entity) {
			discarded.add(entity);
		}
	}
}
