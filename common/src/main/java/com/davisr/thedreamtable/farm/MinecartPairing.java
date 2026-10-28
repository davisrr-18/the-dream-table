package com.davisr.thedreamtable.farm;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.function.Function;

/** Decides how a minecart-passenger entity step can be completed without guessing between candidates. */
public final class MinecartPairing {
	private MinecartPairing() {
	}

	public enum Action {
		COMPLETE,
		CREATE_CART_FOR_PASSENGER,
		CREATE_PASSENGER_FOR_CART,
		CREATE_PAIR,
		BLOCKED
	}

	public record Snapshot(int passengers, int freePassengers, int carts, int emptyCarts, int intactPairs) {
	}

	public static Action decide(Snapshot snapshot) {
		if (snapshot.passengers() == 1 && snapshot.freePassengers() == 0
				&& snapshot.carts() == 1 && snapshot.emptyCarts() == 0 && snapshot.intactPairs() == 1) {
			return Action.COMPLETE;
		}
		if (snapshot.passengers() == 1 && snapshot.freePassengers() == 1 && snapshot.carts() == 0) {
			return Action.CREATE_CART_FOR_PASSENGER;
		}
		if (snapshot.passengers() == 0 && snapshot.carts() == 1 && snapshot.emptyCarts() == 1) {
			return Action.CREATE_PASSENGER_FOR_CART;
		}
		if (snapshot.passengers() == 0 && snapshot.carts() == 0) {
			return Action.CREATE_PAIR;
		}
		return Action.BLOCKED;
	}

	static <T> List<T> inCell(BlockPos cell, List<T> candidates, Function<T, Vec3d> position) {
		return candidates.stream().filter(candidate -> BlockPos.ofFloored(position.apply(candidate)).equals(cell)).toList();
	}
}
