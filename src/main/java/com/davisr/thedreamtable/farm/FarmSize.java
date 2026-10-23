package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.farm.schematic.SchematicReader;
import net.minecraft.text.Text;

/**
 * Tiny, Medium or Big, from a farm's block count. Each size holds 8 times more than the one before and Big ends at the
 * schematic limit, so the sizes spread farms out the way they really grow: plant farms are tiny, the iron farm medium.
 */
public enum FarmSize {
	TINY("size.the_dream_table.tiny"),
	MEDIUM("size.the_dream_table.medium"),
	BIG("size.the_dream_table.big");

	public static final int TINY_MAX = SchematicReader.MAX_BLOCKS / 32;
	public static final int MEDIUM_MAX = SchematicReader.MAX_BLOCKS / 4;

	private final String translationKey;

	FarmSize(String translationKey) {
		this.translationKey = translationKey;
	}

	public Text title() {
		return Text.translatable(translationKey);
	}

	public static FarmSize of(int blocks) {
		if (blocks <= TINY_MAX) {
			return TINY;
		}
		return blocks <= MEDIUM_MAX ? MEDIUM : BIG;
	}
}
