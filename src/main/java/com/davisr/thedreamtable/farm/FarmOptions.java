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
	public static final FarmOption BAMBOO = new FarmOption("bamboo", Items.BAMBOO, "farm.the_dream_table.bamboo");
	public static final FarmOption CROP = new FarmOption("crop", Items.WHEAT, "farm.the_dream_table.crop");
	public static final FarmOption MOB_DARK_ROOM = new FarmOption("mob_dark_room", Items.ROTTEN_FLESH, "farm.the_dream_table.mob_dark_room");
	public static final FarmOption IRON_GOLEM = new FarmOption("iron_golem", Items.IRON_INGOT, "farm.the_dream_table.iron_golem");

	/** The essential vanilla farms, one design each. Bigger or other versions belong in My Schematics. */
	public static final List<FarmOption> ALL = List.of(SUGAR_CANE, BAMBOO, CROP, MOB_DARK_ROOM, IRON_GOLEM);

	/** The first menu page, by what the farms produce. A new built-in farm goes in {@link #ALL} and one category. */
	public enum Category {
		PLANTS(Items.SUGAR_CANE, "category.the_dream_table.plants", List.of(SUGAR_CANE, BAMBOO, CROP)),
		MOBS(Items.BONE, "category.the_dream_table.mobs", List.of(MOB_DARK_ROOM)),
		RESOURCES(Items.IRON_INGOT, "category.the_dream_table.resources", List.of(IRON_GOLEM));

		private final Item icon;
		private final String translationKey;
		private final List<FarmOption> farms;

		Category(Item icon, String translationKey, List<FarmOption> farms) {
			this.icon = icon;
			this.translationKey = translationKey;
			this.farms = farms;
		}

		public Item icon() {
			return icon;
		}

		public Text title() {
			return Text.translatable(translationKey);
		}

		public List<FarmOption> farms() {
			return farms;
		}

		public static Optional<Category> byIndex(int index) {
			Category[] all = values();
			return index >= 0 && index < all.length ? Optional.of(all[index]) : Optional.empty();
		}

		public static Optional<Category> of(String farmId) {
			for (Category category : values()) {
				if (category.farms.stream().anyMatch(farm -> farm.id().equals(farmId))) {
					return Optional.of(category);
				}
			}
			return Optional.empty();
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
