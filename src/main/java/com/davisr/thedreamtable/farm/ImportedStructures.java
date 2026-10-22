package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ImportedStructures {
	private static final int MAX_BLOCKS = 4096;

	private ImportedStructures() {
	}

	public static void reload(Map<String, FarmTemplate> templates, List<FarmOption> options, Path worldRoot) {
		templates.clear();
		options.clear();
		List<Path> files = new ArrayList<>();
		Set<String> names = new HashSet<>();
		for (Path folder : folders(worldRoot)) {
			if (!Files.isDirectory(folder)) {
				continue;
			}
			try (var listed = Files.list(folder)) {
				listed.filter(ImportedStructures::isStructureFile).forEach(path -> {
					String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
					if (names.add(name)) {
						files.add(path);
					}
				});
			} catch (IOException exception) {
				TheDreamTableMod.LOGGER.warn("Could not list schematics in {}", folder, exception);
			}
		}
		files.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));
		for (Path path : files) {
			load(path, templates, options);
		}
	}

	private static List<Path> folders(Path worldRoot) {
		Path game = FabricLoader.getInstance().getGameDir();
		List<Path> folders = new ArrayList<>();
		folders.add(game.resolve("schematics"));
		folders.add(game.resolve("config").resolve("worldedit").resolve("schematics"));
		folders.add(game.resolve("config").resolve("litematica"));
		if (worldRoot != null) {
			folders.add(worldRoot.resolve("schematics"));
		}
		folders.add(game.resolve("config").resolve("the_dream_table").resolve("structures"));
		return folders;
	}

	private static void load(Path path, Map<String, FarmTemplate> templates, List<FarmOption> options) {
		String fileName = path.getFileName().toString();
		int dot = fileName.lastIndexOf('.');
		String display = dot > 0 ? fileName.substring(0, dot) : fileName;
		String id = uniqueId(display, templates);
		try {
			FarmTemplate template = read(id, path);
			if (template == null) {
				return;
			}
			templates.put(id, template);
			options.add(new FarmOption(id, icon(template), "farm.the_dream_table.imported", display));
			TheDreamTableMod.LOGGER.info("Imported structure {} as {}", display, id);
		} catch (RuntimeException | IOException exception) {
			TheDreamTableMod.LOGGER.warn("Skipped structure {}", fileName, exception);
		}
	}

	private static boolean isStructureFile(Path path) {
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(".nbt") || name.endsWith(".schem") || name.endsWith(".litematic");
	}

	private static FarmTemplate read(String id, Path path) throws IOException {
		NbtCompound root = readNbt(path);
		if (root.contains("Schematic", NbtElement.COMPOUND_TYPE)) {
			root = root.getCompound("Schematic");
		}
		List<FarmStep> steps = new ArrayList<>();
		if (root.contains("Regions", NbtElement.COMPOUND_TYPE)) {
			readLitematica(root, steps);
		} else if (root.contains("BlockData", NbtElement.BYTE_ARRAY_TYPE) || nestedSponge(root)) {
			readSponge(root, steps);
		} else if (root.contains("blocks", NbtElement.LIST_TYPE)) {
			readVanilla(root, steps);
		} else {
			TheDreamTableMod.LOGGER.warn("{} has no block NBT the table can build", path.getFileName());
			return null;
		}
		if (steps.isEmpty()) {
			TheDreamTableMod.LOGGER.warn("{} has no blocks to build", path.getFileName());
			return null;
		}
		if (steps.size() > MAX_BLOCKS) {
			TheDreamTableMod.LOGGER.warn("{} has {} blocks; the limit is {}", path.getFileName(), steps.size(), MAX_BLOCKS);
			return null;
		}
		steps.sort(Comparator
				.comparingInt((FarmStep step) -> step.isEntity() ? 1 : 0)
				.thenComparingInt(ImportedStructures::priority)
				.thenComparingInt(FarmStep::y)
				.thenComparingInt(FarmStep::x)
				.thenComparingInt(FarmStep::z));
		return FarmTemplate.of(id, steps);
	}

	private static boolean nestedSponge(NbtCompound root) {
		return root.contains("Blocks", NbtElement.COMPOUND_TYPE) && root.getCompound("Blocks").contains("Data", NbtElement.BYTE_ARRAY_TYPE);
	}

	private static void readVanilla(NbtCompound root, List<FarmStep> steps) {
		NbtList palette = palette(root);
		NbtList blocks = root.getList("blocks", NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < blocks.size(); index++) {
			NbtCompound entry = blocks.getCompound(index);
			NbtList pos = entry.getList("pos", NbtElement.INT_TYPE);
			if (pos.size() < 3) {
				continue;
			}
			BlockState state = state(palette.getCompound(entry.getInt("state")));
			addBlock(steps, pos.getInt(0), pos.getInt(1), pos.getInt(2), state, entry.contains("nbt", NbtElement.COMPOUND_TYPE) ? entry.getCompound("nbt") : null);
		}
		addEntities(root.getList("entities", NbtElement.COMPOUND_TYPE), steps);
	}

	private static void readSponge(NbtCompound root, List<FarmStep> steps) {
		NbtCompound blocks = nestedSponge(root) ? root.getCompound("Blocks") : root;
		int width = number(root, "Width");
		int height = number(root, "Height");
		int length = number(root, "Length");
		if (width <= 0 || height <= 0 || length <= 0) {
			return;
		}
		BlockState[] palette = spongePalette(blocks.contains("Palette", NbtElement.COMPOUND_TYPE) ? blocks.getCompound("Palette") : root.getCompound("Palette"));
		byte[] data = blocks.contains("Data", NbtElement.BYTE_ARRAY_TYPE) ? blocks.getByteArray("Data") : root.getByteArray("BlockData");
		int[] cursor = {0};
		for (int y = 0; y < height; y++) {
			for (int z = 0; z < length; z++) {
				for (int x = 0; x < width; x++) {
					if (cursor[0] >= data.length) {
						attachBlockEntities(steps, blockEntitiesOf(root, blocks));
						addEntities(root.getList("Entities", NbtElement.COMPOUND_TYPE), steps);
						return;
					}
					int id = readVarInt(data, cursor);
					if (id >= 0 && id < palette.length) {
						addBlock(steps, x, y, z, palette[id], null);
					}
				}
			}
		}
		attachBlockEntities(steps, blockEntitiesOf(root, blocks));
		addEntities(root.getList("Entities", NbtElement.COMPOUND_TYPE), steps);
	}

	private static NbtList blockEntitiesOf(NbtCompound root, NbtCompound blocks) {
		if (blocks.contains("BlockEntities", NbtElement.LIST_TYPE)) {
			return blocks.getList("BlockEntities", NbtElement.COMPOUND_TYPE);
		}
		if (root.contains("BlockEntities", NbtElement.LIST_TYPE)) {
			return root.getList("BlockEntities", NbtElement.COMPOUND_TYPE);
		}
		return root.getList("TileEntities", NbtElement.COMPOUND_TYPE);
	}

	private static int readVarInt(byte[] data, int[] cursor) {
		int value = 0;
		int length = 0;
		int current;
		do {
			if (cursor[0] >= data.length) {
				return 0;
			}
			current = data[cursor[0]++] & 0xFF;
			value |= (current & 127) << (length++ * 7);
		} while ((current & 128) != 0 && length < 5);
		return value;
	}

	private static BlockState[] spongePalette(NbtCompound palette) {
		int size = 0;
		for (String key : palette.getKeys()) {
			size = Math.max(size, number(palette, key) + 1);
		}
		BlockState[] states = new BlockState[Math.max(1, size)];
		java.util.Arrays.fill(states, Blocks.AIR.getDefaultState());
		for (String key : palette.getKeys()) {
			int id = number(palette, key);
			if (id >= 0 && id < states.length) {
				states[id] = fromString(key);
			}
		}
		return states;
	}

	private static void readLitematica(NbtCompound root, List<FarmStep> steps) {
		NbtCompound regions = root.getCompound("Regions");
		List<int[]> placed = new ArrayList<>();
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		List<NbtCompound> regionList = new ArrayList<>();
		for (String name : regions.getKeys()) {
			NbtCompound region = regions.getCompound(name);
			int[] min = minimumCorner(region);
			minX = Math.min(minX, min[0]);
			minY = Math.min(minY, min[1]);
			minZ = Math.min(minZ, min[2]);
			regionList.add(region);
		}
		for (NbtCompound region : regionList) {
			int[] min = minimumCorner(region);
			int sizeX = Math.abs(numberArray(region, "Size", 0));
			int sizeY = Math.abs(numberArray(region, "Size", 1));
			int sizeZ = Math.abs(numberArray(region, "Size", 2));
			NbtList palette = region.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
			long[] packed = region.getLongArray("BlockStates");
			int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.size()) - 1));
			long mask = (1L << bits) - 1L;
			for (int y = 0; y < sizeY; y++) {
				for (int z = 0; z < sizeZ; z++) {
					for (int x = 0; x < sizeX; x++) {
						int index = (y * sizeZ + z) * sizeX + x;
						int id = packedId(packed, index, bits, mask);
						if (id < 0 || id >= palette.size()) {
							continue;
						}
						int stepX = min[0] + x - minX;
						int stepY = min[1] + y - minY;
						int stepZ = min[2] + z - minZ;
						addBlock(steps, stepX, stepY, stepZ, state(palette.getCompound(id)), null);
						placed.add(new int[] {stepX, stepY, stepZ, min[0] + x, min[1] + y, min[2] + z});
					}
				}
			}
			if (region.contains("TileEntities", NbtElement.LIST_TYPE)) {
				attachLitematicaTiles(steps, placed, region.getList("TileEntities", NbtElement.COMPOUND_TYPE));
			}
			if (region.contains("Entities", NbtElement.LIST_TYPE)) {
				addEntities(region.getList("Entities", NbtElement.COMPOUND_TYPE), steps);
			}
		}
	}

	private static int packedId(long[] packed, int index, int bits, long mask) {
		if (packed.length == 0) {
			return -1;
		}
		long start = (long) index * bits;
		int startIndex = (int) (start >>> 6);
		int endIndex = (int) ((start + bits - 1) >>> 6);
		if (startIndex >= packed.length) {
			return -1;
		}
		int bit = (int) (start & 63);
		if (startIndex == endIndex) {
			return (int) ((packed[startIndex] >>> bit) & mask);
		}
		if (endIndex >= packed.length) {
			return -1;
		}
		int endOffset = 64 - bit;
		return (int) (((packed[startIndex] >>> bit) | (packed[endIndex] << endOffset)) & mask);
	}

	private static int[] minimumCorner(NbtCompound region) {
		int posX = numberArray(region, "Position", 0);
		int posY = numberArray(region, "Position", 1);
		int posZ = numberArray(region, "Position", 2);
		int sizeX = numberArray(region, "Size", 0);
		int sizeY = numberArray(region, "Size", 1);
		int sizeZ = numberArray(region, "Size", 2);
		return new int[] {
				Math.min(posX, posX + sizeX + (sizeX < 0 ? 1 : 0)),
				Math.min(posY, posY + sizeY + (sizeY < 0 ? 1 : 0)),
				Math.min(posZ, posZ + sizeZ + (sizeZ < 0 ? 1 : 0))
		};
	}

	private static int numberArray(NbtCompound compound, String key, int index) {
		if (compound.contains(key, NbtElement.INT_ARRAY_TYPE)) {
			int[] values = compound.getIntArray(key);
			return index < values.length ? values[index] : 0;
		}
		NbtList list = compound.getList(key, NbtElement.INT_TYPE);
		return index < list.size() ? list.getInt(index) : 0;
	}

	private static void attachBlockEntities(List<FarmStep> steps, NbtList blockEntities) {
		for (int index = 0; index < blockEntities.size(); index++) {
			NbtCompound data = blockEntities.getCompound(index);
			int[] pos = data.contains("Pos", NbtElement.INT_ARRAY_TYPE) ? data.getIntArray("Pos") : new int[] {data.getInt("x"), data.getInt("y"), data.getInt("z")};
			if (pos.length < 3) {
				continue;
			}
			replaceNbt(steps, pos[0], pos[1], pos[2], data);
		}
	}

	private static void attachLitematicaTiles(List<FarmStep> steps, List<int[]> placed, NbtList tiles) {
		for (int index = 0; index < tiles.size(); index++) {
			NbtCompound data = tiles.getCompound(index);
			int x = data.getInt("x");
			int y = data.getInt("y");
			int z = data.getInt("z");
			for (int[] spot : placed) {
				boolean local = spot[0] == x && spot[1] == y && spot[2] == z;
				boolean world = spot[3] == x && spot[4] == y && spot[5] == z;
				if (local || world) {
					replaceNbt(steps, spot[0], spot[1], spot[2], data);
					break;
				}
			}
		}
	}

	private static void replaceNbt(List<FarmStep> steps, int x, int y, int z, NbtCompound data) {
		for (int index = 0; index < steps.size(); index++) {
			FarmStep step = steps.get(index);
			if (!step.isEntity() && step.x() == x && step.y() == y && step.z() == z) {
				steps.set(index, FarmStep.block(x, y, z, step.state(), step.item(), data.copy()));
				return;
			}
		}
	}

	private static void addEntities(NbtList entities, List<FarmStep> steps) {
		for (int index = 0; index < entities.size(); index++) {
			FarmStep step = entity(entities.getCompound(index));
			if (step != null) {
				steps.add(step);
			}
		}
	}

	private static void addBlock(List<FarmStep> steps, int x, int y, int z, BlockState state, NbtCompound blockNbt) {
		if (state.isAir() || state.isOf(Blocks.STRUCTURE_VOID) || state.isOf(Blocks.JIGSAW) || state.isOf(Blocks.STRUCTURE_BLOCK)) {
			return;
		}
		steps.add(FarmStep.block(x, y, z, state, itemFor(state), blockNbt == null ? null : blockNbt.copy()));
	}

	private static NbtCompound readNbt(Path path) throws IOException {
		try {
			return NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
		} catch (IOException compressed) {
			return NbtIo.read(path);
		}
	}

	private static NbtList palette(NbtCompound root) {
		if (root.contains("palette", NbtElement.LIST_TYPE)) {
			return root.getList("palette", NbtElement.COMPOUND_TYPE);
		}
		NbtList palettes = root.getList("palettes", NbtElement.LIST_TYPE);
		if (!palettes.isEmpty()) {
			return palettes.getList(0);
		}
		return new NbtList();
	}

	private static BlockState state(NbtCompound paletteEntry) {
		Identifier name = Identifier.of(paletteEntry.getString("Name"));
		Block block = Registries.BLOCK.get(name);
		BlockState state = block.getDefaultState();
		if (!paletteEntry.contains("Properties", NbtElement.COMPOUND_TYPE)) {
			return state;
		}
		NbtCompound properties = paletteEntry.getCompound("Properties");
		for (String key : properties.getKeys()) {
			Property<?> property = block.getStateManager().getProperty(key);
			if (property != null) {
				state = apply(state, property, properties.getString(key));
			}
		}
		return state;
	}

	private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<T> property, String value) {
		return property.parse(value).map(parsed -> state.with(property, parsed)).orElse(state);
	}

	private static Item itemFor(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof DoorBlock && state.contains(DoorBlock.HALF) && state.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
			return null;
		}
		if (state.contains(net.minecraft.state.property.Properties.BED_PART) && state.get(net.minecraft.state.property.Properties.BED_PART) == BedPart.HEAD) {
			return null;
		}
		if (block == Blocks.WATER) {
			return Items.WATER_BUCKET;
		}
		if (block == Blocks.LAVA) {
			return Items.LAVA_BUCKET;
		}
		if (block == Blocks.FARMLAND || block == Blocks.DIRT_PATH) {
			return Items.DIRT;
		}
		if (block == Blocks.REDSTONE_WIRE) {
			return Items.REDSTONE;
		}
		if (block == Blocks.WHEAT) {
			return Items.WHEAT_SEEDS;
		}
		if (block == Blocks.CARROTS) {
			return Items.CARROT;
		}
		if (block == Blocks.POTATOES) {
			return Items.POTATO;
		}
		if (block == Blocks.BEETROOTS) {
			return Items.BEETROOT_SEEDS;
		}
		if (block == Blocks.REDSTONE_WALL_TORCH) {
			return Items.REDSTONE_TORCH;
		}
		if (block == Blocks.WALL_TORCH) {
			return Items.TORCH;
		}
		Item item = block.asItem();
		return item == Items.AIR ? null : item;
	}

	private static FarmStep entity(NbtCompound entry) {
		NbtCompound data = entry.contains("nbt", NbtElement.COMPOUND_TYPE) ? entry.getCompound("nbt") : entry;
		String rawId = data.contains("id") ? data.getString("id") : entry.getString("Id");
		var type = EntityType.get(rawId);
		if (type.isEmpty()) {
			return null;
		}
		int x;
		int y;
		int z;
		if (entry.contains("blockPos", NbtElement.LIST_TYPE)) {
			NbtList pos = entry.getList("blockPos", NbtElement.INT_TYPE);
			if (pos.size() < 3) {
				return null;
			}
			x = pos.getInt(0);
			y = pos.getInt(1);
			z = pos.getInt(2);
		} else if (entry.contains("Pos", NbtElement.LIST_TYPE)) {
			NbtList pos = entry.getList("Pos", NbtElement.DOUBLE_TYPE);
			if (pos.size() < 3) {
				return null;
			}
			x = (int) Math.floor(pos.getDouble(0));
			y = (int) Math.floor(pos.getDouble(1));
			z = (int) Math.floor(pos.getDouble(2));
		} else {
			return null;
		}
		return FarmStep.entity(x, y, z, type.get(), false).withItem(cost(type.get()));
	}

	private static int number(NbtCompound compound, String key) {
		NbtElement element = compound.get(key);
		if (element instanceof net.minecraft.nbt.AbstractNbtNumber number) {
			return number.intValue();
		}
		return 0;
	}

	private static BlockState fromString(String raw) {
		String name = raw;
		String properties = null;
		int bracket = raw.indexOf('[');
		if (bracket >= 0) {
			name = raw.substring(0, bracket);
			properties = raw.substring(bracket + 1, raw.endsWith("]") ? raw.length() - 1 : raw.length());
		}
		Block block = Registries.BLOCK.get(Identifier.of(name));
		BlockState state = block.getDefaultState();
		if (properties == null || properties.isEmpty()) {
			return state;
		}
		for (String part : properties.split(",")) {
			String[] pair = part.split("=", 2);
			if (pair.length != 2) {
				continue;
			}
			Property<?> property = block.getStateManager().getProperty(pair[0]);
			if (property != null) {
				state = apply(state, property, pair[1]);
			}
		}
		return state;
	}

	private static Item cost(EntityType<?> type) {
		SpawnEggItem egg = SpawnEggItem.forEntity(type);
		if (egg != null) {
			return egg;
		}
		if (type == EntityType.HOPPER_MINECART) {
			return Items.HOPPER_MINECART;
		}
		if (type == EntityType.CHEST_MINECART) {
			return Items.CHEST_MINECART;
		}
		if (type == EntityType.FURNACE_MINECART) {
			return Items.FURNACE_MINECART;
		}
		if (type == EntityType.TNT_MINECART) {
			return Items.TNT_MINECART;
		}
		if (type == EntityType.MINECART) {
			return Items.MINECART;
		}
		return null;
	}

	private static int priority(FarmStep step) {
		if (step.state() == null) {
			return 0;
		}
		Block block = step.state().getBlock();
		if (block == Blocks.WATER || block == Blocks.LAVA) {
			return 2;
		}
		if (block instanceof PlantBlock || block == Blocks.SUGAR_CANE || block == Blocks.BAMBOO || block == Blocks.CACTUS) {
			return 3;
		}
		return 0;
	}

	private static Item icon(FarmTemplate template) {
		Map<Item, Integer> counts = new HashMap<>();
		for (MaterialSpec spec : template.materials()) {
			counts.merge(spec.item(), spec.count(), Integer::sum);
		}
		return counts.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse(Items.STRUCTURE_BLOCK);
	}

	private static String uniqueId(String display, Map<String, FarmTemplate> templates) {
		String base = "nbt:" + display.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
		if (base.equals("nbt:")) {
			base = "nbt:structure";
		}
		String id = base;
		int suffix = 2;
		while (templates.containsKey(id) || FarmTemplates.get(id) != null) {
			id = base + "_" + suffix++;
		}
		return id;
	}

}
