package com.davisr.thedreamtable.farm;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.HangingSignBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.PressurePlateBlock;
import net.minecraft.block.SignBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TransparentBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MaterialSwaps {
	private static final Map<String, List<Item>> FAMILIES = new HashMap<>();
	private static boolean built;

	private MaterialSwaps() {
	}

	/** The original comes first, followed by every compatible replacement the template can actually use. */
	public static List<Item> options(FarmTemplate template, Item original) {
		if (template == null || original == null) {
			return List.of();
		}
		Set<Block> blocks = new LinkedHashSet<>();
		for (FarmStep step : template.steps()) {
			if (step.item() != original) {
				continue;
			}
			if (step.state() == null || step.state().getBlock().asItem() != original) {
				return List.of();
			}
			blocks.add(step.state().getBlock());
		}
		if (blocks.isEmpty()) {
			return List.of();
		}
		String family = family(Block.getBlockFromItem(original));
		if (family == null) {
			return List.of();
		}
		List<Item> options = new ArrayList<>();
		options.add(original);
		for (Item candidate : family(family)) {
			if (candidate == original) {
				continue;
			}
			boolean usable = true;
			for (Block block : blocks) {
				if (target(block, original, candidate) == null) {
					usable = false;
					break;
				}
			}
			if (usable) {
				options.add(candidate);
			}
		}
		return options.size() > 1 ? options : List.of();
	}

	public static BlockState apply(BlockState state, Map<Item, Item> swaps) {
		if (state == null || swaps.isEmpty()) {
			return state;
		}
		Block block = state.getBlock();
		Item original = block.asItem();
		Item replacement = swaps.get(original);
		if (replacement == null || replacement == original) {
			return state;
		}
		Block target = target(block, original, replacement);
		if (target == null || target == block) {
			return state;
		}
		BlockState result = target.getDefaultState();
		for (Property<?> property : state.getProperties()) {
			result = copy(result, state, property);
		}
		return result;
	}

	private static <T extends Comparable<T>> BlockState copy(BlockState into, BlockState from, Property<T> property) {
		return into.contains(property) ? into.with(property, from.get(property)) : into;
	}

	private static Block target(Block block, Item original, Item replacement) {
		Block direct = Block.getBlockFromItem(replacement);
		if (direct == Blocks.AIR) {
			return null;
		}
		if (block == Block.getBlockFromItem(original)) {
			return direct;
		}
		Identifier blockId = Registries.BLOCK.getId(block);
		String from = Registries.ITEM.getId(original).getPath();
		Identifier toId = Registries.ITEM.getId(replacement);
		String to = toId.getPath();
		int shared = 0;
		while (shared < from.length() && shared < to.length() && from.charAt(from.length() - 1 - shared) == to.charAt(to.length() - 1 - shared)) {
			shared++;
		}
		String fromStem = from.substring(0, from.length() - shared);
		String toStem = to.substring(0, to.length() - shared);
		int at = blockId.getPath().indexOf(fromStem);
		if (fromStem.isEmpty() || at < 0) {
			return null;
		}
		String path = blockId.getPath().substring(0, at) + toStem + blockId.getPath().substring(at + fromStem.length());
		Identifier id = new Identifier(toId.getNamespace(), path);
		return Registries.BLOCK.containsId(id) ? Registries.BLOCK.get(id) : null;
	}

	private static synchronized List<Item> family(String family) {
		build();
		return FAMILIES.getOrDefault(family, List.of());
	}

	private static void build() {
		if (built) {
			return;
		}
		for (Block block : Registries.BLOCK) {
			Item item = block.asItem();
			if (item == Items.AIR || Block.getBlockFromItem(item) != block || block.getHardness() < 0.0F) {
				continue;
			}
			String family = family(block);
			if (family != null) {
				FAMILIES.computeIfAbsent(family, key -> new ArrayList<>()).add(item);
			}
		}
		built = true;
	}

	private static String family(Block block) {
		if (block == null || block == Blocks.AIR) {
			return null;
		}
		if (block instanceof BedBlock) {
			return "bed";
		}
		if (block instanceof HangingSignBlock) {
			return "hanging_sign";
		}
		if (block instanceof SignBlock) {
			return "sign";
		}
		if (block instanceof StairsBlock) {
			return "stairs";
		}
		if (block instanceof SlabBlock) {
			return "slab";
		}
		if (block instanceof WallBlock) {
			return "wall";
		}
		if (block instanceof FenceGateBlock) {
			return "fence_gate";
		}
		if (block instanceof FenceBlock) {
			return "fence";
		}
		if (block instanceof TrapdoorBlock) {
			return "trapdoor";
		}
		if (block instanceof DoorBlock) {
			return "door";
		}
		if (block instanceof ButtonBlock) {
			return "button";
		}
		if (block instanceof PressurePlateBlock) {
			return "pressure_plate";
		}
		if (block instanceof CarpetBlock) {
			return "carpet";
		}
		if (block instanceof PaneBlock) {
			return "pane";
		}
		if (block instanceof TransparentBlock) {
			return "glass";
		}
		if (block instanceof BlockEntityProvider) {
			return null;
		}
		if (block.getClass() == PillarBlock.class) {
			return "pillar";
		}
		if (block.getClass() == Block.class && block.getDefaultState().isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) {
			return "cube";
		}
		return null;
	}
}
