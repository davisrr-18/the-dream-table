package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.farm.FarmProtection;
import com.davisr.thedreamtable.network.TableNoticePayload;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps a player from breaking a Dream Table that holds work or materials they must not lose: another player's delivered
 * materials, running or paused build, or Take Down, breaking it would drop their materials and end their work. A player's
 * own running build stops them too, since breaking the table would orphan its placed blocks, and saved data this version
 * cannot read stops everyone, the owner included. Server operators may always break it, and a table with only the breaker's
 * own delivered materials, Take Down or empty sessions breaks as before. It goes through the same break seam as
 * {@code FarmProtection}, so it covers players only: explosions, pistons and other mods' block removal are not covered.
 */
public final class TableBreakGuard {
	/** Permission level from which a player may always break a table, like the vanilla command operators. */
	static final int OPERATOR_LEVEL = 2;

	/** The notice to the player whose own build is running at the table. */
	static final String OWN_BUILD_KEY = "gui.the_dream_table.table_break_own_build";

	/** Why a session or saved entry keeps the table from being broken, strongest last so {@link #blocker} can pick the clearest reason. */
	public enum Reason {
		MATERIALS("gui.the_dream_table.table_break_materials"),
		BUILD("gui.the_dream_table.table_break_build"),
		TAKE_DOWN("gui.the_dream_table.table_break_take_down"),
		UNREADABLE("gui.the_dream_table.table_break_unreadable");

		private final String key;

		Reason(String key) {
			this.key = key;
		}

		public String key() {
			return key;
		}
	}

	/** What one player has at stake at a table. {@code player} is null only for a saved entry that names no player. */
	public record Claim(@Nullable UUID player, Reason reason) {
	}

	private TableBreakGuard() {
	}

	public static void register() {
		Platform.serverEvents().onBlockBreak((world, player, pos, state) -> {
			if (!(state.getBlock() instanceof DreamTableBlock)) {
				return true;
			}
			Optional<Claim> blocker = blockerAt(world, player, pos);
			if (blocker.isEmpty()) {
				return true;
			}
			if (!FarmProtection.probing()) {
				tell(world, player, blocker.get());
			}
			return false;
		});
	}

	/**
	 * The claim that stops this player from breaking the table, or empty when they may. An operator is never stopped. Unreadable
	 * saved data and a running build stop every player, the owner included; other claims stop only players other than their
	 * owner. When several claims stop the breaker, the one with the strongest reason is reported.
	 */
	public static Optional<Claim> blocker(Collection<Claim> claims, UUID breaker, boolean operator) {
		if (operator) {
			return Optional.empty();
		}
		Claim worst = null;
		for (Claim claim : claims) {
			if (stops(claim, breaker) && (worst == null || claim.reason().compareTo(worst.reason()) > 0)) {
				worst = claim;
			}
		}
		return Optional.ofNullable(worst);
	}

	/** Whether this claim keeps the breaker from breaking the table, before the operator rule. */
	static boolean stops(Claim claim, UUID breaker) {
		return claim.reason() == Reason.UNREADABLE || claim.reason() == Reason.BUILD || !breaker.equals(claim.player());
	}

	/** The claim that stops this player from breaking the table at {@code pos}, or empty when it is not a table or they may. */
	public static Optional<Claim> blockerAt(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
		if (!DreamTableConfig.tableBreakGuardEnabled() || !(world.getBlockEntity(pos) instanceof DreamTableBlockEntity table)) {
			return Optional.empty();
		}
		return blocker(table.breakClaims(), player.getUuid(), player.hasPermissionLevel(OPERATOR_LEVEL));
	}

	public static void tell(ServerWorld world, ServerPlayerEntity player, Claim claim) {
		Text text;
		if (claim.reason() == Reason.UNREADABLE) {
			text = Text.translatable(Reason.UNREADABLE.key());
		} else if (claim.reason() == Reason.BUILD && player.getUuid().equals(claim.player())) {
			text = Text.translatable(OWN_BUILD_KEY);
		} else {
			text = Text.translatable(claim.reason().key(), FarmProtection.ownerName(world.getServer(), claim.player()));
		}
		TableNoticePayload.problem(player, text);
	}
}
