package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.block.entity.TableSession;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

/** Mobs and minecarts the builder places, and the empty bucket it hands back after pouring water. */
final class FarmSpawns {
	enum Result {
		PLACED, PRESENT, BLOCKED, FAILED
	}

	interface PairingOperations<T> {
		T createPassenger();

		T createCart();

		boolean spawn(T entity);

		boolean mount(T passenger, T cart);

		void discard(T entity);
	}

	private FarmSpawns() {
	}

	static Result spawn(ServerWorld world, BlockPos pos, FarmStep step) {
		if (!step.minecart()) {
			Entity entity = createPassenger(world, pos, step);
			return entity != null && world.spawnEntity(entity) ? Result.PLACED : Result.FAILED;
		}
		Pairing pairing = pairing(world, pos, step);
		return spawnMinecart(pairing.action(), pairing.passenger(), pairing.cart(), new PairingOperations<>() {
			@Override
			public Entity createPassenger() {
				return FarmSpawns.createPassenger(world, pos, step);
			}

			@Override
			public Entity createCart() {
				Entity cart = EntityType.MINECART.create(world);
				if (cart != null) {
					cart.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
				}
				return cart;
			}

			@Override
			public boolean spawn(Entity entity) {
				return world.spawnEntity(entity);
			}

			@Override
			public boolean mount(Entity passenger, Entity cart) {
				return passenger.startRiding(cart, true);
			}

			@Override
			public void discard(Entity entity) {
				entity.discard();
			}
		});
	}

	/**
	 * Reports whether an entity step is complete, repairable, or blocked. Only each entity's own position counts, not
	 * its width, so one in the next cell doesn't satisfy or block this step.
	 */
	static Result status(ServerWorld world, BlockPos pos, FarmStep step) {
		if (step.minecart()) {
			return switch (pairing(world, pos, step).action()) {
				case COMPLETE -> Result.PRESENT;
				case BLOCKED -> Result.BLOCKED;
				default -> Result.FAILED;
			};
		}
		return passengers(world, pos, step).isEmpty() ? Result.FAILED : Result.PRESENT;
	}

	static <T> Result spawnMinecart(MinecartPairing.Action action, T existingPassenger, T existingCart, PairingOperations<T> operations) {
		if (action == MinecartPairing.Action.COMPLETE) {
			return Result.PRESENT;
		}
		if (action == MinecartPairing.Action.BLOCKED) {
			return Result.BLOCKED;
		}

		T createdPassenger = null;
		T createdCart = null;
		T passenger = existingPassenger;
		T cart = existingCart;
		if (action == MinecartPairing.Action.CREATE_PASSENGER_FOR_CART || action == MinecartPairing.Action.CREATE_PAIR) {
			createdPassenger = operations.createPassenger();
			passenger = createdPassenger;
		}
		if (action == MinecartPairing.Action.CREATE_CART_FOR_PASSENGER || action == MinecartPairing.Action.CREATE_PAIR) {
			createdCart = operations.createCart();
			cart = createdCart;
		}
		if (passenger == null || cart == null
				|| (createdCart != null && !operations.spawn(createdCart))
				|| (createdPassenger != null && !operations.spawn(createdPassenger))
				|| !operations.mount(passenger, cart)) {
			if (createdCart != null) {
				operations.discard(createdCart);
			}
			if (createdPassenger != null) {
				operations.discard(createdPassenger);
			}
			return Result.FAILED;
		}
		return Result.PLACED;
	}

	private static Pairing pairing(ServerWorld world, BlockPos pos, FarmStep step) {
		List<Entity> passengers = passengers(world, pos, step);
		Box search = new Box(pos).expand(0.5);
		List<MinecartEntity> broadCarts = new ArrayList<>(world.getEntitiesByType(EntityType.MINECART, search, Entity::isAlive));
		List<MinecartEntity> carts = MinecartPairing.inCell(pos, broadCarts, Entity::getPos);
		int freePassengers = (int) passengers.stream().filter(passenger -> !passenger.hasVehicle()).count();
		int emptyCarts = (int) carts.stream().filter(cart -> !cart.hasPassengers()).count();
		int intactPairs = (int) passengers.stream().filter(passenger -> carts.stream().anyMatch(cart ->
				passenger.getVehicle() == cart && cart.getPassengerList().size() == 1)).count();
		MinecartPairing.Action action = MinecartPairing.decide(new MinecartPairing.Snapshot(
				passengers.size(), freePassengers, carts.size(), emptyCarts, intactPairs));
		Entity passenger = action == MinecartPairing.Action.CREATE_CART_FOR_PASSENGER ? passengers.get(0) : null;
		Entity cart = action == MinecartPairing.Action.CREATE_PASSENGER_FOR_CART ? carts.get(0) : null;
		return new Pairing(action, passenger, cart);
	}

	private static List<Entity> passengers(ServerWorld world, BlockPos pos, FarmStep step) {
		Box search = new Box(pos).expand(0.5);
		List<Entity> broad = new ArrayList<>(world.getEntitiesByType(step.entity(), search, Entity::isAlive));
		return MinecartPairing.inCell(pos, broad, Entity::getPos);
	}

	private static Entity createPassenger(ServerWorld world, BlockPos pos, FarmStep step) {
		Entity passenger = step.entity().create(world);
		if (passenger == null) {
			return null;
		}
		double y = pos.getY() + (step.entity() == EntityType.HOPPER_MINECART || step.minecart() ? 0.0625 : 0.0);
		passenger.setPosition(pos.getX() + 0.5, y, pos.getZ() + 0.5);
		if (passenger instanceof MobEntity mob) {
			mob.setPersistent();
			mob.setAiDisabled(step.noAi());
		}
		return passenger;
	}

	private record Pairing(MinecartPairing.Action action, Entity passenger, Entity cart) {
	}

	/** Buckets that pour out their fluid when placed and leave an empty bucket behind. */
	public static boolean leavesEmptyBucket(Item item) {
		return item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET || item == Items.POWDER_SNOW_BUCKET;
	}

	/**
	 * Whether a placed step is recorded as paid. A bucket that was handed back empty cost nothing lasting, so it is recorded
	 * as unpaid and Take Down refunds nothing for it. Before the empty bucket was returned for lava and powder snow, those
	 * steps were recorded as paid because the bucket was consumed, and they keep their refund. The flag is per step, so a
	 * record that mixes old and new steps never refunds a bucket that already came back.
	 */
	public static boolean recordsAsPaid(Item item, boolean paid) {
		return paid && !(item != null && leavesEmptyBucket(item));
	}

	/** Whether Take Down hands back this recorded step's item. Water is skipped because it always returned its bucket, even in records that mark it paid. */
	public static boolean refundsOnTakeDown(Item item, boolean paid) {
		return paid && item != null && item != Items.WATER_BUCKET;
	}

	static void returnBucket(ServerWorld world, TableSession table) {
		ItemStack bucket = new ItemStack(Items.BUCKET);
		ServerPlayerEntity player = table.getOwner() == null ? null : world.getServer().getPlayerManager().getPlayer(table.getOwner());
		if (player != null && player.isAlive()) {
			if (table.build().shouldNotifyDropped(Handover.give(player, bucket))) {
				Handover.tellDropped(player);
			}
			return;
		}
		BlockPos pos = table.getPos();
		ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, bucket);
	}
}
