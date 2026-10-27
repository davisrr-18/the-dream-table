package com.davisr.thedreamtable.block.entity;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Why a build stopped or waits: what happened, at which spot, to which block or item, and what the player can do.
 * Temporary causes may clear on their own, so Try Again just tries; the others need the player first, and resuming
 * checks them before it starts. {@code attempts} counts failures in a row at the same step.
 */
public record BuildIssue(Kind kind, BlockPos where, String subject, int attempts) {
	/** A failure that repeats this often at the same step is no longer treated as temporary. */
	public static final int REPEATED = 2;

	public enum Kind {
		/** A block the table never clears: it holds items, data or redstone, or can't be broken. */
		OBSTACLE(false),
		/** A block of another player's or table's farm. */
		OTHER_FARM(false),
		/** Spawn protection, the world border or a land claim. */
		PROTECTED(false),
		/** The next block wasn't delivered. */
		MATERIAL(false),
		/** The spot's chunk takes long to load. The build keeps waiting there. */
		UNLOADED(true),
		/** The game refused the block. */
		PLACE_FAILED(true),
		/** An unexpected error, logged with its details. {@code subject} is the step number. */
		ERROR(true);

		private final boolean temporary;

		Kind(boolean temporary) {
			this.temporary = temporary;
		}

		private String key() {
			return "gui.the_dream_table.issue." + name().toLowerCase(Locale.ROOT);
		}
	}

	public static BuildIssue of(Kind kind, BlockPos where, Block block) {
		return new BuildIssue(kind, where.toImmutable(), Registries.BLOCK.getId(block).toString(), 1);
	}

	public static BuildIssue of(Kind kind, BlockPos where, Item item) {
		return new BuildIssue(kind, where.toImmutable(), Registries.ITEM.getId(item).toString(), 1);
	}

	public static BuildIssue at(Kind kind, BlockPos where) {
		return new BuildIssue(kind, where.toImmutable(), "", 1);
	}

	public BuildIssue again(int count) {
		return new BuildIssue(kind, where, subject, count);
	}

	/** True when trying again may work by itself, so it isn't checked before resuming. */
	public boolean temporary() {
		return kind.temporary && attempts < REPEATED;
	}

	/** "Chest at 10, 64, -3 is in the way", "Needs Hopper". */
	public Text what() {
		return switch (kind) {
			case OBSTACLE, OTHER_FARM, PLACE_FAILED -> Text.translatable(kind.key(), subjectName(), where.getX(), where.getY(), where.getZ());
			case PROTECTED -> Text.translatable(kind.key(), where.getX(), where.getY(), where.getZ());
			case MATERIAL -> Text.translatable(kind.key(), subjectName());
			case UNLOADED -> Text.translatable(kind.key(), where.getX(), where.getZ());
			case ERROR -> Text.translatable(kind.key(), subject);
		};
	}

	/** What the player can do about it. */
	public Text fix() {
		boolean repeated = kind.temporary && attempts >= REPEATED;
		return Text.translatable(kind.key() + (repeated ? ".repeated" : ".fix"));
	}

	private Text subjectName() {
		Identifier id = Identifier.tryParse(subject);
		if (id == null) {
			return Text.literal(subject);
		}
		if (kind == Kind.MATERIAL) {
			return Registries.ITEM.containsId(id) ? Registries.ITEM.get(id).getName() : Text.literal(subject);
		}
		return Registries.BLOCK.containsId(id) ? Registries.BLOCK.get(id).getName() : Text.literal(subject);
	}

	public void write(NbtCompound nbt) {
		NbtCompound issue = new NbtCompound();
		issue.putString("Kind", kind.name());
		issue.putLong("Where", where.asLong());
		issue.putString("Subject", subject);
		issue.putInt("Attempts", attempts);
		nbt.put("Issue", issue);
	}

	public static @Nullable BuildIssue read(NbtCompound nbt) {
		if (!nbt.contains("Issue")) {
			return null;
		}
		NbtCompound issue = nbt.getCompound("Issue");
		Kind kind;
		try {
			kind = Kind.valueOf(issue.getString("Kind"));
		} catch (IllegalArgumentException unknown) {
			return null;
		}
		return new BuildIssue(kind, BlockPos.fromLong(issue.getLong("Where")), issue.getString("Subject"), Math.max(1, issue.getInt("Attempts")));
	}
}
