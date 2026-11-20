package com.davisr.thedreamtable.block.entity;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * The spot the player aimed the farm hologram at and has not built yet. It lives beside the placement, not in it: it reserves no
 * area, blocks no other farm and is not progress. A broken table keeps it with the project, and the next table checks it again.
 */
public record AimedSpot(BlockPos anchor, Direction facing, boolean mirrored, Identifier dimension) {
	public AimedSpot {
		facing = facing != null && facing.getAxis().isHorizontal() ? facing : Direction.SOUTH;
	}

	/** A spot comes back into another table only in its own world and within the reach of that table. */
	public static boolean usableHere(boolean sameWorld, boolean withinReach) {
		return sameWorld && withinReach;
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("X", anchor.getX());
		nbt.putInt("Y", anchor.getY());
		nbt.putInt("Z", anchor.getZ());
		nbt.putString("Facing", facing.asString());
		nbt.putBoolean("Mirror", mirrored);
		nbt.putString("Dimension", dimension.toString());
		return nbt;
	}

	/** The spot {@link #toNbt} wrote, or null when the entry lacks any part of it. */
	public static @Nullable AimedSpot fromNbt(NbtCompound nbt) {
		if (!(nbt.get("X") instanceof NbtInt) || !(nbt.get("Y") instanceof NbtInt) || !(nbt.get("Z") instanceof NbtInt)) {
			return null;
		}
		Identifier dimension = Identifier.tryParse(nbt.getString("Dimension", ""));
		if (dimension == null) {
			return null;
		}
		BlockPos anchor = new BlockPos(nbt.getInt("X", 0), nbt.getInt("Y", 0), nbt.getInt("Z", 0));
		return new AimedSpot(anchor, Direction.byId(nbt.getString("Facing", "")), nbt.getBoolean("Mirror", false), dimension);
	}
}
