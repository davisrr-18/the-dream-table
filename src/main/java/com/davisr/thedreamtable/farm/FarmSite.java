package com.davisr.thedreamtable.farm;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Where a farm sits in the world: beside the table when there is no anchor, or at the anchor the player picked.
 * It turns template steps into world positions without touching a session, so a spot can be checked before it is used.
 */
public record FarmSite(BlockPos table, @Nullable BlockPos anchor, Direction facing) {
	public BlockPos target(FarmTemplate template, FarmStep step) {
		return anchor != null
				? FarmGeometry.toAimed(anchor, facing, template, step)
				: FarmGeometry.toWorld(table, facing, step);
	}

	/** The box of blocks the farm occupies. */
	public Box footprint(FarmTemplate template) {
		int[] low = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
		int[] high = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (FarmStep step : template.steps()) {
			BlockPos pos = target(template, step);
			int[] at = {pos.getX(), pos.getY(), pos.getZ()};
			for (int axis = 0; axis < 3; axis++) {
				low[axis] = Math.min(low[axis], at[axis]);
				high[axis] = Math.max(high[axis], at[axis]);
			}
		}
		return new Box(low[0], low[1], low[2], high[0] + 1, high[1] + 1, high[2] + 1);
	}

	/** True when every step lies between the world floor and ceiling and inside the border. Works on both sides. */
	public boolean fits(World world, FarmTemplate template) {
		for (FarmStep step : template.steps()) {
			BlockPos pos = target(template, step);
			if (!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)) {
				return false;
			}
		}
		return true;
	}

	public void write(NbtCompound nbt) {
		nbt.putLong("Table", table.asLong());
		if (anchor != null) {
			nbt.putLong("Anchor", anchor.asLong());
		}
		nbt.putString("Facing", facing.asString());
	}

	public static FarmSite read(NbtCompound nbt) {
		Direction facing = Direction.byName(nbt.getString("Facing"));
		return new FarmSite(
				BlockPos.fromLong(nbt.getLong("Table")),
				nbt.contains("Anchor") ? BlockPos.fromLong(nbt.getLong("Anchor")) : null,
				facing != null && facing.getAxis().isHorizontal() ? facing : Direction.SOUTH);
	}
}
