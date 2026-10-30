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

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps a player from breaking a Dream Table that holds another player's delivered materials, running or paused build, or
 * Take Down: breaking it would drop their materials and end their work. The player who owns every such session, and server
 * operators, may break it; a table with only the breaker's own sessions, or empty ones, breaks as before. It goes through
 * the same break seam as {@code FarmProtection}, so it covers players only: explosions, pistons and other mods' block
 * removal are not covered.
 */
public final class TableBreakGuard {
	/** Permission level from which a player may always break a table, like the vanilla command operators. */
	static final int OPERATOR_LEVEL = 2;

	/** Why a session keeps the table from being broken, strongest last so {@link #blocker} can pick the clearest reason. */
	public enum Reason {
		MATERIALS("gui.the_dream_table.table_break_materials"),
		BUILD("gui.the_dream_table.table_break_build"),
		TAKE_DOWN("gui.the_dream_table.table_break_take_down");

		private final String key;

		Reason(String key) {
			this.key = key;
		}

		public String key() {
			return key;
		}
	}

	/** What one player has at stake at a table. */
	public record Claim(UUID player, Reason reason) {
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
	 * The claim that stops this player from breaking the table, or empty when they may. A claim of the breaker's own never
	 * stops them, and an operator is never stopped. When several players have something at stake, the one with the
	 * strongest reason is reported.
	 */
	public static Optional<Claim> blocker(Collection<Claim> claims, UUID breaker, boolean operator) {
		if (operator) {
			return Optional.empty();
		}
		Claim worst = null;
		for (Claim claim : claims) {
			if (!claim.player().equals(breaker) && (worst == null || claim.reason().compareTo(worst.reason()) > 0)) {
				worst = claim;
			}
		}
		return Optional.ofNullable(worst);
	}

	/** The claim that stops this player from breaking the table at {@code pos}, or empty when it is not a table or they may. */
	public static Optional<Claim> blockerAt(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
		if (!DreamTableConfig.tableBreakGuardEnabled() || !(world.getBlockEntity(pos) instanceof DreamTableBlockEntity table)) {
			return Optional.empty();
		}
		return blocker(table.breakClaims(), player.getUuid(), player.hasPermissionLevel(OPERATOR_LEVEL));
	}

	public static void tell(ServerWorld world, ServerPlayerEntity player, Claim claim) {
		TableNoticePayload.problem(player, Text.translatable(claim.reason().key(), FarmProtection.ownerName(world.getServer(), claim.player())));
	}
}
