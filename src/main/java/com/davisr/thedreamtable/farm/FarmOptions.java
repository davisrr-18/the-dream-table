package com.davisr.thedreamtable.farm;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;

public final class FarmOptions {
	public record FarmOption(String id, Item icon, String translationKey, String literalName) {
		public FarmOption(String id, Item icon, String translationKey) {
			this(id, icon, translationKey, null);
		}

		public Text title() {
			if (literalName != null && !literalName.isBlank()) {
				return Text.literal(literalName);
			}
			return Text.translatable(translationKey);
		}
	}

	public static final FarmOption SUGAR_CANE = new FarmOption("sugarcane", Items.SUGAR_CANE, "farm.the_dream_table.sugarcane");
	public static final FarmOption SUGAR_CANE_MEDIUM = new FarmOption("sugarcane_medium", Items.SUGAR_CANE, "farm.the_dream_table.sugarcane");
	public static final FarmOption SUGAR_CANE_BIG = new FarmOption("sugarcane_big", Items.SUGAR_CANE, "farm.the_dream_table.sugarcane");
	public static final FarmOption BAMBOO = new FarmOption("bamboo", Items.BAMBOO, "farm.the_dream_table.bamboo");
	public static final FarmOption BAMBOO_MEDIUM = new FarmOption("bamboo_medium", Items.BAMBOO, "farm.the_dream_table.bamboo");
	public static final FarmOption BAMBOO_BIG = new FarmOption("bamboo_big", Items.BAMBOO, "farm.the_dream_table.bamboo");
	public static final FarmOption CROP_TINY = new FarmOption("crop_tiny", Items.WHEAT, "farm.the_dream_table.crop");
	public static final FarmOption CROP = new FarmOption("crop", Items.WHEAT, "farm.the_dream_table.crop");
	public static final FarmOption MOB_DARK_ROOM = new FarmOption("mob_dark_room", Items.ROTTEN_FLESH, "farm.the_dream_table.mob_dark_room");
	public static final FarmOption MOB_DARK_ROOM_BIG = new FarmOption("mob_dark_room_big", Items.ROTTEN_FLESH, "farm.the_dream_table.mob_dark_room");
	public static final FarmOption IRON_GOLEM = new FarmOption("iron_golem", Items.IRON_INGOT, "farm.the_dream_table.iron_golem");

	/** Sizes of one farm sit together, smallest first; the size tag on each card tells them apart. */
	public static final List<FarmOption> ALL = List.of(
			SUGAR_CANE, SUGAR_CANE_MEDIUM, SUGAR_CANE_BIG,
			BAMBOO, BAMBOO_MEDIUM, BAMBOO_BIG,
			CROP_TINY, CROP,
			MOB_DARK_ROOM, MOB_DARK_ROOM_BIG,
			IRON_GOLEM);

	/** One kind of farm in its sizes, smallest first, such as Sugar Cane in Tiny, Medium and Big. */
	public enum Group {
		SUGAR_CANE_FARMS(Items.SUGAR_CANE, "farm.the_dream_table.sugarcane", List.of(SUGAR_CANE, SUGAR_CANE_MEDIUM, SUGAR_CANE_BIG)),
		BAMBOO_FARMS(Items.BAMBOO, "farm.the_dream_table.bamboo", List.of(BAMBOO, BAMBOO_MEDIUM, BAMBOO_BIG)),
		CROP_FARMS(Items.WHEAT, "farm.the_dream_table.crop", List.of(CROP_TINY, CROP)),
		MOB_FARMS(Items.ROTTEN_FLESH, "farm.the_dream_table.mob_dark_room", List.of(MOB_DARK_ROOM, MOB_DARK_ROOM_BIG)),
		IRON_FARMS(Items.IRON_INGOT, "farm.the_dream_table.iron_golem", List.of(IRON_GOLEM));

		private final Item icon;
		private final String translationKey;
		private final List<FarmOption> sizes;

		Group(Item icon, String translationKey, List<FarmOption> sizes) {
			this.icon = icon;
			this.translationKey = translationKey;
			this.sizes = sizes;
		}

		public Item icon() {
			return icon;
		}

		public Text title() {
			return Text.translatable(translationKey);
		}

		public List<FarmOption> sizes() {
			return sizes;
		}

		public static Optional<Group> byIndex(int index) {
			Group[] all = values();
			return index >= 0 && index < all.length ? Optional.of(all[index]) : Optional.empty();
		}

		public static Optional<Group> of(String farmId) {
			for (Group group : values()) {
				if (group.sizes.stream().anyMatch(farm -> farm.id().equals(farmId))) {
					return Optional.of(group);
				}
			}
			return Optional.empty();
		}
	}

	/**
	 * The first menu page, by what the farms produce. Each category lists its {@link Group}s, and each group its sizes.
	 * A new built-in farm goes in {@link #ALL}, one group, and that group in one category.
	 */
	public enum Category {
		PLANTS(Items.SUGAR_CANE, "category.the_dream_table.plants", List.of(Group.SUGAR_CANE_FARMS, Group.BAMBOO_FARMS, Group.CROP_FARMS)),
		MOBS(Items.BONE, "category.the_dream_table.mobs", List.of(Group.MOB_FARMS)),
		RESOURCES(Items.IRON_INGOT, "category.the_dream_table.resources", List.of(Group.IRON_FARMS));

		private final Item icon;
		private final String translationKey;
		private final List<Group> groups;

		Category(Item icon, String translationKey, List<Group> groups) {
			this.icon = icon;
			this.translationKey = translationKey;
			this.groups = groups;
		}

		public Item icon() {
			return icon;
		}

		public Text title() {
			return Text.translatable(translationKey);
		}

		public List<Group> groups() {
			return groups;
		}

		/** Every size of every group, for counting and lookups. */
		public List<FarmOption> farms() {
			return groups.stream().flatMap(group -> group.sizes().stream()).toList();
		}

		public static Optional<Category> byIndex(int index) {
			Category[] all = values();
			return index >= 0 && index < all.length ? Optional.of(all[index]) : Optional.empty();
		}

		public static Optional<Category> of(Group group) {
			for (Category category : values()) {
				if (category.groups.contains(group)) {
					return Optional.of(category);
				}
			}
			return Optional.empty();
		}

		public static Optional<Category> of(String farmId) {
			return Group.of(farmId).flatMap(Category::of);
		}
	}

	private FarmOptions() {
	}

	public static Optional<FarmOption> byIndex(int index) {
		if (index < 0 || index >= ALL.size()) {
			return Optional.empty();
		}
		return Optional.of(ALL.get(index));
	}

	public static Optional<FarmOption> byId(String id) {
		return FarmTemplates.options().stream().filter(option -> option.id().equals(id)).findFirst();
	}
}
