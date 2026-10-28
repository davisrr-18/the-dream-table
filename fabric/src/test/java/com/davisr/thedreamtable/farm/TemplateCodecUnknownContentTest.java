package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins how a design that names content this game doesn't have (a removed mod) decodes. */
class TemplateCodecUnknownContentTest {
	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	private static NbtCompound named(String key, String value) {
		NbtCompound compound = new NbtCompound();
		compound.putString(key, value);
		return compound;
	}

	/** Steps sit at x = 0..n-1; a state of -1 marks an entity step. */
	private static NbtCompound design(NbtList palette, NbtList items, NbtList entities, int[] state, int[] item) {
		NbtCompound nbt = new NbtCompound();
		nbt.put("Palette", palette);
		nbt.put("Items", items);
		nbt.put("Entities", entities);
		int[] x = new int[state.length];
		for (int index = 0; index < x.length; index++) {
			x[index] = index;
		}
		nbt.putIntArray("X", x);
		nbt.putIntArray("Y", new int[state.length]);
		nbt.putIntArray("Z", new int[state.length]);
		nbt.putIntArray("State", state);
		nbt.putIntArray("Item", item);
		return nbt;
	}

	private static NbtCompound entity(String id) {
		NbtCompound entity = named("Id", id);
		entity.putInt("Flags", 0);
		return entity;
	}

	@Test
	void unknownBlockBecomesAir() {
		NbtList palette = new NbtList();
		palette.add(named("Name", "gone_mod:mystery_block"));
		palette.add(named("Name", "minecraft:stone"));

		FarmTemplate template = TemplateCodec.decode("test", design(palette, new NbtList(), new NbtList(), new int[]{0, 1}, new int[]{-1, -1}));

		assertEquals(2, template.steps().size());
		assertTrue(template.steps().get(0).state().isOf(Blocks.AIR));
		assertTrue(template.steps().get(1).state().isOf(Blocks.STONE));
	}

	@Test
	void unknownEntityIsSkippedAndLaterEntitiesKeepTheirOwnData() {
		NbtList entities = new NbtList();
		entities.add(entity("gone_mod:mystery_mob"));
		entities.add(entity("minecraft:cow"));

		FarmTemplate template = TemplateCodec.decode("test", design(new NbtList(), new NbtList(), entities, new int[]{-1, -1}, new int[]{-1, -1}));

		assertEquals(1, template.steps().size());
		FarmStep kept = template.steps().get(0);
		assertSame(EntityType.COW, kept.entity());
		assertEquals(1, kept.x());
	}

	@Test
	void unknownItemInTheMaterialsBecomesTheAirItem() {
		NbtList palette = new NbtList();
		palette.add(named("Name", "minecraft:stone"));
		NbtList items = new NbtList();
		items.add(NbtString.of("gone_mod:mystery_item"));

		FarmTemplate template = TemplateCodec.decode("test", design(palette, items, new NbtList(), new int[]{0}, new int[]{0}));

		assertEquals(1, template.steps().size());
		assertSame(Items.AIR, template.steps().get(0).item());
	}

	@Test
	void malformedItemIdBecomesNoItem() {
		NbtList palette = new NbtList();
		palette.add(named("Name", "minecraft:stone"));
		NbtList items = new NbtList();
		items.add(NbtString.of("Not A Valid Id!"));

		FarmTemplate template = TemplateCodec.decode("test", design(palette, items, new NbtList(), new int[]{0}, new int[]{0}));

		assertNull(template.steps().get(0).item());
	}
}
