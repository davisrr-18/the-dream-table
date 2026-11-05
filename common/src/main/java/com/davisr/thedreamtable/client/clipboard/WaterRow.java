package com.davisr.thedreamtable.client.clipboard;

import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.NearbyWater;
import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.farm.FarmBuilder;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.client.clipboard.ClipboardLines.Line;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The water line of a farm with water steps: whether infinite water is near the table, read from the client world with the
 * server's rule ({@link NearbyWater}). The server decides for real; this only shows what it will find. Farm areas the client
 * doesn't know (other farms, finished ones) are not excluded, so the line can say "nearby" where the server would refuse.
 */
public final class WaterRow {
	private static long checkedTick = Long.MIN_VALUE;
	private static @Nullable BlockPos checkedTable;
	private static @Nullable FarmTemplate checkedTemplate;
	private static boolean checkedNearby;

	private WaterRow() {
	}

	/** True when the farm has a water step, so the clipboard and the requirements page show the line. */
	public static boolean needed(FarmTemplate template) {
		for (FarmStep step : template.steps()) {
			if (FarmBuilder.isWater(step)) {
				return true;
			}
		}
		return false;
	}

	/** The clipboard row: a water bottle, so it never looks like a bucket, with one item when a source is near. */
	public static Line line(boolean nearby) {
		ItemStack stack = new ItemStack(Items.POTION);
		stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Potions.WATER));
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.translatable(key(nearby)));
		return new Line(stack, nearby ? 1 : 0, 1);
	}

	public static String key(boolean nearby) {
		return nearby ? "gui.the_dream_table.water.nearby" : "gui.the_dream_table.water.none";
	}

	/** The table's session in the client world, or null while the table isn't there. */
	public static @Nullable TableSession sessionAt(BlockPos table) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) {
			return null;
		}
		return client.world.getBlockEntity(table) instanceof DreamTableBlockEntity entity ? entity.session(client.player.getUuid()) : null;
	}

	/** Cached for the tick: the clipboard and the page ask every frame, and the scan reads up to 2197 cells. */
	public static boolean nearby(TableSession table, FarmTemplate template) {
		ClientWorld world = MinecraftClient.getInstance().world;
		if (world == null) {
			return false;
		}
		BlockPos pos = table.getPos();
		long now = world.getTime();
		if (now == checkedTick && pos.equals(checkedTable) && template == checkedTemplate) {
			return checkedNearby;
		}
		Box ownFarm = FarmBuilder.site(table).footprint(template);
		boolean nearby = NearbyWater.nearby(new NearbyWater.Cells() {
			@Override
			public boolean loaded(BlockPos cell) {
				return world.getChunkManager().isChunkLoaded(cell.getX() >> 4, cell.getZ() >> 4);
			}

			@Override
			public FluidState fluid(BlockPos cell) {
				return world.getFluidState(cell);
			}

			@Override
			public boolean farmOwned(BlockPos cell) {
				return ownFarm.contains(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5);
			}
		}, pos);
		checkedTick = now;
		checkedTable = pos;
		checkedTemplate = template;
		checkedNearby = nearby;
		return nearby;
	}
}
