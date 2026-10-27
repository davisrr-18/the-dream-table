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
import static org.junit.jupiter.api.Assertions.assertSame;

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
		FakeEntity existingPassenger = new FakeEntity("existing passenger");
		FakeOperations cartAttempt = new FakeOperations().failMount();
		assertEquals(FarmSpawns.Result.FAILED,
				FarmSpawns.spawnMinecart(CREATE_CART_FOR_PASSENGER, existingPassenger, null, cartAttempt));
		assertEquals(List.of(cartAttempt.createdCart), cartAttempt.discarded);
		assertFalse(cartAttempt.discarded.contains(existingPassenger));

		FakeEntity existingCart = new FakeEntity("existing cart");
		FakeOperations passengerAttempt = new FakeOperations().failMount();
		assertEquals(FarmSpawns.Result.FAILED,
				FarmSpawns.spawnMinecart(CREATE_PASSENGER_FOR_CART, null, existingCart, passengerAttempt));
		assertEquals(List.of(passengerAttempt.createdPassenger), passengerAttempt.discarded);
		assertFalse(passengerAttempt.discarded.contains(existingCart));

		FakeOperations pairAttempt = new FakeOperations().failMount();
		assertEquals(FarmSpawns.Result.FAILED, FarmSpawns.spawnMinecart(CREATE_PAIR, null, null, pairAttempt));
		assertEquals(List.of(pairAttempt.createdCart, pairAttempt.createdPassenger), pairAttempt.discarded);
	}

	@Test
	void failedCartSpawnStopsBeforePassengerSpawnAndDiscardsTheCreatedPair() {
		FakeOperations operations = new FakeOperations().failCartSpawn();

		assertEquals(FarmSpawns.Result.FAILED, FarmSpawns.spawnMinecart(CREATE_PAIR, null, null, operations));

		assertEquals(List.of("create passenger", "create cart", "spawn created cart", "discard created cart", "discard created passenger"),
				operations.calls);
		assertEquals(List.of(operations.createdCart, operations.createdPassenger), operations.discarded);
	}

	@Test
	void failedPassengerSpawnRollsBackTheCreatedCartAndPassenger() {
		FakeOperations operations = new FakeOperations().failPassengerSpawn();

		assertEquals(FarmSpawns.Result.FAILED, FarmSpawns.spawnMinecart(CREATE_PAIR, null, null, operations));

		assertEquals(List.of("create passenger", "create cart", "spawn created cart", "spawn created passenger",
				"discard created cart", "discard created passenger"), operations.calls);
		assertEquals(List.of(operations.createdCart, operations.createdPassenger), operations.discarded);
	}

	@Test
	void failedCartSpawnPreservesTheExistingPassenger() {
		FakeEntity passenger = new FakeEntity("existing passenger");
		FakeOperations operations = new FakeOperations().failCartSpawn();

		assertEquals(FarmSpawns.Result.FAILED,
				FarmSpawns.spawnMinecart(CREATE_CART_FOR_PASSENGER, passenger, null, operations));

		assertEquals(List.of("create cart", "spawn created cart", "discard created cart"), operations.calls);
		assertEquals(List.of(operations.createdCart), operations.discarded);
		assertFalse(operations.discarded.contains(passenger));
	}

	@Test
	void failedPassengerSpawnPreservesTheExistingCart() {
		FakeEntity cart = new FakeEntity("existing cart");
		FakeOperations operations = new FakeOperations().failPassengerSpawn();

		assertEquals(FarmSpawns.Result.FAILED,
				FarmSpawns.spawnMinecart(CREATE_PASSENGER_FOR_CART, null, cart, operations));

		assertEquals(List.of("create passenger", "spawn created passenger", "discard created passenger"), operations.calls);
		assertEquals(List.of(operations.createdPassenger), operations.discarded);
		assertFalse(operations.discarded.contains(cart));
	}

	@Test
	void successfulCartCreationMountsExistingPassengerWithoutDiscardingIt() {
		FakeEntity passenger = new FakeEntity("existing passenger");
		FakeOperations operations = new FakeOperations();
		assertEquals(FarmSpawns.Result.PLACED,
				FarmSpawns.spawnMinecart(CREATE_CART_FOR_PASSENGER, passenger, null, operations));
		assertSame(passenger, operations.mountedPassenger);
		assertSame(operations.createdCart, operations.mountedCart);
		assertEquals(List.of("create cart", "spawn created cart", "mount existing passenger -> created cart"), operations.calls);
		assertEquals(List.of(), operations.discarded);
	}

	@Test
	void successfulPassengerCreationMountsItInExistingCartWithoutDiscardingIt() {
		FakeEntity cart = new FakeEntity("existing cart");
		FakeOperations operations = new FakeOperations();
		assertEquals(FarmSpawns.Result.PLACED,
				FarmSpawns.spawnMinecart(CREATE_PASSENGER_FOR_CART, null, cart, operations));
		assertSame(operations.createdPassenger, operations.mountedPassenger);
		assertSame(cart, operations.mountedCart);
		assertEquals(List.of("create passenger", "spawn created passenger", "mount created passenger -> existing cart"), operations.calls);
		assertEquals(List.of(), operations.discarded);
	}

	@Test
	void successfulPairCreationSpawnsBothAndMountsTheCreatedReferences() {
		FakeOperations operations = new FakeOperations();
		assertEquals(FarmSpawns.Result.PLACED, FarmSpawns.spawnMinecart(CREATE_PAIR, null, null, operations));
		assertSame(operations.createdPassenger, operations.mountedPassenger);
		assertSame(operations.createdCart, operations.mountedCart);
		assertEquals(List.of("create passenger", "create cart", "spawn created cart", "spawn created passenger",
				"mount created passenger -> created cart"), operations.calls);
		assertEquals(List.of(), operations.discarded);
	}

	private record Positioned(Vec3d position) {
	}

	private record FakeEntity(String name) {
	}

	private static final class FakeOperations implements FarmSpawns.PairingOperations<FakeEntity> {
		private final List<String> calls = new ArrayList<>();
		private final List<FakeEntity> discarded = new ArrayList<>();
		private boolean cartSpawnSucceeds = true;
		private boolean passengerSpawnSucceeds = true;
		private boolean mountSucceeds = true;
		private FakeEntity createdPassenger;
		private FakeEntity createdCart;
		private FakeEntity mountedPassenger;
		private FakeEntity mountedCart;

		private FakeOperations failCartSpawn() {
			cartSpawnSucceeds = false;
			return this;
		}

		private FakeOperations failPassengerSpawn() {
			passengerSpawnSucceeds = false;
			return this;
		}

		private FakeOperations failMount() {
			mountSucceeds = false;
			return this;
		}

		@Override
		public FakeEntity createPassenger() {
			createdPassenger = new FakeEntity("created passenger");
			calls.add("create passenger");
			return createdPassenger;
		}

		@Override
		public FakeEntity createCart() {
			createdCart = new FakeEntity("created cart");
			calls.add("create cart");
			return createdCart;
		}

		@Override
		public boolean spawn(FakeEntity entity) {
			calls.add("spawn " + entity.name());
			if (entity == createdCart) {
				return cartSpawnSucceeds;
			}
			if (entity == createdPassenger) {
				return passengerSpawnSucceeds;
			}
			throw new AssertionError("Unexpected spawn: " + entity);
		}

		@Override
		public boolean mount(FakeEntity passenger, FakeEntity cart) {
			mountedPassenger = passenger;
			mountedCart = cart;
			calls.add("mount " + passenger.name() + " -> " + cart.name());
			return mountSucceeds;
		}

		@Override
		public void discard(FakeEntity entity) {
			calls.add("discard " + entity.name());
			discarded.add(entity);
		}
	}
}
