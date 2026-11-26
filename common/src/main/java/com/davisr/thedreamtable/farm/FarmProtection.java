package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.network.TableNoticePayload;
import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.platform.Platform;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.AbstractPlantPartBlock;
import net.minecraft.block.AttachedStemBlock;
import net.minecraft.block.BambooBlock;
import net.minecraft.block.BambooShootBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CactusBlock;
import net.minecraft.block.ChorusFlowerBlock;
import net.minecraft.block.ChorusPlantBlock;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.item.Item;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.UserCache;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * "Protect My Farms": other players, explosions and endermen can't break the structure of a finished farm whose owner
 * turned the option on. The structure is every block of the farm's saved design at its saved site (turned and mirrored the way it was built)
 * that is still there as designed, except what grows or is harvested there; produce stays breakable by anyone, and so
 * does a block someone put in a hole of a damaged farm. The owner and server operators are never blocked by hand, and an
 * explosion the owner caused (their own TNT) is not filtered. Explosions drop the structural blocks from their list before
 * the blocks are destroyed; an enderman near a protected farm doesn't pick up blocks at all. Pistons, other mobs, fluids and
 * fire don't go through these hooks and are not covered.
 */
public final class FarmProtection {
	/** Permission level from which a player bypasses the protection, like the vanilla command operators. */
	static final int OPERATOR_LEVEL = 2;
	/** Nesting depth of the mod's own permission probes (server thread only), during which a denial tells nobody. */
	private static int probes;
	/** The design's blocks per site, built on the first break that asks inside a farm. Shared by identity, like {@link FarmGrowth}'s. */
	private static final IdentityCache<FarmTemplate, SiteIndex> INDEXES = new IdentityCache<>();

	private FarmProtection() {
	}

	/** Runs one of the mod's own permission probes: protection still answers, but no message is shown for a break nobody tried. */
	static boolean quietly(BooleanSupplier probe) {
		probes++;
		try {
			return probe.getAsBoolean();
		} finally {
			probes--;
		}
	}

	public static boolean probing() {
		return probes > 0;
	}

	/** Blocks around an enderman, horizontally and above, that its pickup can reach (x and z 2, up to 3 above), plus a margin. */
	private static final double ENDERMAN_REACH_XZ = 3;
	private static final double ENDERMAN_REACH_Y = 4;

	public static void register() {
		Platform.serverEvents().onBlockBreak((world, player, pos, state) -> {
			UUID owner = blockedOwner(world, player, pos, state);
			if (owner == null) {
				return true;
			}
			if (probing()) {
				return false;
			}
			TableNoticePayload.problem(player, Text.translatable("gui.the_dream_table.farm_protected", ownerName(world.getServer(), owner)));
			return false;
		});
		Platform.serverEvents().onExplosion(FarmProtection::filterExplosion);
		Platform.serverEvents().onEndermanGrief(FarmProtection::endermanAllowed);
	}

	/** Takes the structural blocks of protected farms out of an explosion's list, unless their owner caused it. */
	static void filterExplosion(ServerWorld world, @Nullable Entity cause, List<BlockPos> affected) {
		if (affected.isEmpty() || !DreamTableConfig.farmProtectionEnabled()) {
			return;
		}
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos pos : affected) {
			minX = Math.min(minX, pos.getX());
			minY = Math.min(minY, pos.getY());
			minZ = Math.min(minZ, pos.getZ());
			maxX = Math.max(maxX, pos.getX());
			maxY = Math.max(maxY, pos.getY());
			maxZ = Math.max(maxZ, pos.getZ());
		}
		List<FarmAreas.Guard> guards = FarmAreas.of(world).guardsIn(new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1));
		if (!guards.isEmpty()) {
			filterExplosion(true, guards, FarmPreferences.of(world.getServer())::protectsFarms, cause == null ? null : cause.getUuid(), affected, world::getBlockState);
		}
	}

	/**
	 * The decision behind {@code filterExplosion(ServerWorld, Entity, List)}: removes the positions that are structure of a
	 * guard whose owner protects and did not cause the explosion. Nothing is looked up for the other guards.
	 */
	static void filterExplosion(boolean serverEnabled, List<FarmAreas.Guard> guards, Predicate<UUID> protects, @Nullable UUID cause,
			List<BlockPos> affected, Function<BlockPos, BlockState> stateAt) {
		if (!serverEnabled) {
			return;
		}
		List<FarmAreas.Guard> applicable = guards.stream()
				.filter(guard -> !guard.ownerUuid().equals(cause) && protects.test(guard.ownerUuid()))
				.toList();
		if (!applicable.isEmpty()) {
			affected.removeIf(pos -> {
				BlockState state = stateAt.apply(pos);
				for (FarmAreas.Guard guard : applicable) {
					if (structuralAt(guard.layout(), guard.site(), guard.swaps(), pos, state)) {
						return true;
					}
				}
				return false;
			});
		}
	}

	/** Whether an enderman may pick up a block now: not while a farm whose owner protects it is within its reach. */
	static boolean endermanAllowed(ServerWorld world, EndermanEntity enderman) {
		if (!DreamTableConfig.farmProtectionEnabled()) {
			return true;
		}
		List<FarmAreas.Guard> nearby = FarmAreas.of(world).guardsIn(new Box(enderman.getBlockPos()).expand(ENDERMAN_REACH_XZ, ENDERMAN_REACH_Y, ENDERMAN_REACH_XZ));
		return nearby.isEmpty() || !endermanBlocked(true, nearby, FarmPreferences.of(world.getServer())::protectsFarms);
	}

	/** The decision behind {@link #endermanAllowed}: blocked when any nearby guard's owner protects. Endermen have no owner to exempt. */
	static boolean endermanBlocked(boolean serverEnabled, List<FarmAreas.Guard> nearby, Predicate<UUID> protects) {
		return serverEnabled && nearby.stream().anyMatch(guard -> protects.test(guard.ownerUuid()));
	}

	/** The owner whose protected farm stops this break, or null when the break is allowed. */
	static UUID blockedOwner(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
		if (!DreamTableConfig.farmProtectionEnabled()) {
			return null;
		}
		List<FarmAreas.Guard> guards = FarmAreas.of(world).guardsAt(pos);
		if (guards.isEmpty()) {
			return null;
		}
		FarmPreferences preferences = FarmPreferences.of(world.getServer());
		boolean operator = player.hasPermissionLevel(OPERATOR_LEVEL);
		for (FarmAreas.Guard guard : guards) {
			FarmRecord record = new FarmRecord(FarmKey.of(world, guard.instanceId()), guard.ownerUuid());
			if (denies(true, preferences.protectsFarms(guard.ownerUuid()), operator,
					player.getUuid(), record, () -> structuralAt(guard.layout(), guard.site(), guard.swaps(), pos, state))) {
				return guard.ownerUuid();
			}
		}
		return null;
	}

	/**
	 * Whether a farm stops this actor from breaking one of its blocks. Cheapest questions come first, so the design is
	 * only searched for a stranger whose target farm is actually protected.
	 *
	 * @param serverEnabled  the server's {@code farm_protection_enabled}
	 * @param ownerProtects  the farm owner's "Protect My Farms" switch
	 * @param structuralHere whether the position is a structural block of the farm
	 */
	public static boolean denies(boolean serverEnabled, boolean ownerProtects, boolean actorOperator,
			UUID actor, FarmRecord farm, BooleanSupplier structuralHere) {
		if (!serverEnabled || !ownerProtects || actorOperator || FarmAccess.isOwner(farm, actor)) {
			return false;
		}
		return structuralHere.getAsBoolean();
	}

	/**
	 * Whether the position holds a block of the farm's design at the site, the block there is still the design's (as
	 * Repair and Take Down see it, with the farm's material swaps), and it is structure, not produce. A different block
	 * someone put in a hole of a damaged farm is not the farm's, so it stays breakable.
	 */
	public static boolean structuralAt(FarmTemplate template, FarmSite site, Map<Item, Item> swaps, BlockPos pos, BlockState current) {
		if (current.isAir() || current.getBlock() instanceof FluidBlock || harvestable(current)) {
			return false;
		}
		BlockState designed = designedAt(template, site, pos);
		return designed != null && structural(designed) && BlockMatch.stillFarm(current, site.orient(MaterialSwaps.apply(designed, swaps)));
	}

	/** The design's block at a position of the site (the first step there, as a search finds it), or null when there is none. */
	private static @Nullable BlockState designedAt(FarmTemplate template, FarmSite site, BlockPos pos) {
		SiteIndex index = INDEXES.get(template, indexed -> SiteIndex.of(indexed, site));
		if (!index.site().equals(site)) {
			return searchDesign(template, site, pos);
		}
		return index.states().get(pos);
	}

	private static @Nullable BlockState searchDesign(FarmTemplate template, FarmSite site, BlockPos pos) {
		for (FarmStep step : template.steps()) {
			if (!step.isEntity() && step.state() != null && site.target(template, step).equals(pos)) {
				return step.state();
			}
		}
		return null;
	}

	/**
	 * A design's blocks at one site, by position. Keyed by the design's identity: a template is a record whose own hash covers every
	 * step, and it is immutable, so an index never goes stale. Holds no reference back to the template.
	 */
	private record SiteIndex(FarmSite site, Map<BlockPos, BlockState> states) {
		static SiteIndex of(FarmTemplate template, FarmSite site) {
			Map<BlockPos, BlockState> states = new HashMap<>();
			for (FarmStep step : template.steps()) {
				if (!step.isEntity() && step.state() != null) {
					states.putIfAbsent(site.target(template, step), step.state());
				}
			}
			return new SiteIndex(site, states);
		}
	}

	/** Whether a block of the design is structure: not air, not a fluid, and not something that grows or is harvested. */
	public static boolean structural(BlockState designed) {
		return !designed.isAir() && !(designed.getBlock() instanceof FluidBlock) && !harvestable(designed);
	}

	/**
	 * Crops, stems, saplings and the rest of the plants, sugar cane, cactus, bamboo, kelp and other vines, cocoa,
	 * chorus, and pumpkin and melon blocks. These stay breakable by anyone, since farms are meant to be harvested.
	 */
	public static boolean harvestable(BlockState state) {
		Block block = state.getBlock();
		return block instanceof CropBlock || block instanceof StemBlock || block instanceof AttachedStemBlock
				|| block instanceof PlantBlock || block instanceof SugarCaneBlock || block instanceof CactusBlock
				|| block instanceof BambooBlock || block instanceof BambooShootBlock || block instanceof AbstractPlantPartBlock
				|| block instanceof CocoaBlock || block instanceof ChorusFlowerBlock || block instanceof ChorusPlantBlock
				|| state.isOf(Blocks.PUMPKIN) || state.isOf(Blocks.MELON)
				|| state.isIn(BlockTags.CROPS) || state.isIn(BlockTags.SAPLINGS);
	}

	/** A player's name for messages: online, else from the server's cache, else "its owner". */
	public static Text ownerName(MinecraftServer server, UUID owner) {
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(owner);
		if (online != null) {
			return online.getName();
		}
		UserCache cache = server.getUserCache();
		Optional<String> cached = cache == null ? Optional.empty() : cache.getByUuid(owner).map(GameProfile::getName);
		return cached.<Text>map(Text::literal).orElseGet(() -> Text.translatable("gui.the_dream_table.farm_owner_unknown"));
	}
}
