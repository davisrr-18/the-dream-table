package com.davisr.thedreamtable.farm;

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
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.UserCache;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * "Protect My Farms": other players can't break the structure of a finished farm whose owner turned the option on.
 * The structure is every block of the farm's saved design at its saved site (turned and mirrored the way it was built)
 * except what grows or is harvested there; produce stays breakable by anyone. The owner, players the farm is shared
 * with, and server operators are never blocked. Explosions, pistons, mobs, fluids and fire don't go through the player
 * break event and are not covered.
 */
public final class FarmProtection {
	/** Permission level from which a player bypasses the protection, like the vanilla command operators. */
	static final int OPERATOR_LEVEL = 2;

	private FarmProtection() {
	}

	public static void register() {
		Platform.serverEvents().onBlockBreak((world, player, pos, state) -> {
			UUID owner = blockedOwner(world, player, pos, state);
			if (owner == null) {
				return true;
			}
			player.sendMessage(Text.translatable("gui.the_dream_table.farm_protected", ownerName(world.getServer(), owner)), true);
			return false;
		});
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
			FarmRecord record = new FarmRecord(FarmKey.of(world, guard.instanceId()), guard.ownerUuid(), guard.grants());
			if (denies(true, preferences.protectsFarms(guard.ownerUuid()), operator, player.getUuid(), record,
					DreamTableConfig.farmSharingEnabled(), () -> structuralAt(guard.layout(), guard.site(), pos, state))) {
				return guard.ownerUuid();
			}
		}
		return null;
	}

	/**
	 * Whether a farm stops this actor from breaking one of its blocks. Cheapest questions come first, so the design is
	 * only searched for a stranger whose target farm is actually protected.
	 *
	 * @param serverEnabled   the server's {@code farm_protection_enabled}
	 * @param ownerProtects   the farm owner's "Protect My Farms" switch
	 * @param structuralHere  whether the position is a structural block of the farm
	 */
	public static boolean denies(boolean serverEnabled, boolean ownerProtects, boolean actorOperator, UUID actor, FarmRecord farm,
			boolean sharingEnabled, BooleanSupplier structuralHere) {
		if (!serverEnabled || !ownerProtects || actorOperator) {
			return false;
		}
		if (FarmAccess.role(farm, actor, sharingEnabled).isPresent()) {
			return false;
		}
		return structuralHere.getAsBoolean();
	}

	/** Whether the position holds a block of the farm's design at the site, and the block there is structure, not produce. */
	public static boolean structuralAt(FarmTemplate template, FarmSite site, BlockPos pos, BlockState current) {
		if (current.isAir() || current.getBlock() instanceof FluidBlock || harvestable(current)) {
			return false;
		}
		for (FarmStep step : template.steps()) {
			if (!step.isEntity() && step.state() != null && site.target(template, step).equals(pos)) {
				return structural(step.state());
			}
		}
		return false;
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

	private static Text ownerName(MinecraftServer server, UUID owner) {
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(owner);
		if (online != null) {
			return online.getName();
		}
		UserCache cache = server.getUserCache();
		Optional<String> cached = cache == null ? Optional.empty() : cache.getByUuid(owner).map(GameProfile::getName);
		return cached.<Text>map(Text::literal).orElseGet(() -> Text.translatable("gui.the_dream_table.farm_owner_unknown"));
	}
}
