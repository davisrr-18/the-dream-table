package com.davisr.thedreamtable;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.registry.MutableRegistry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.tag.TagKey;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Loads the vanilla registries once, so tests can use blocks and items. */
public final class GameSetup {
	private static boolean ready;

	private GameSetup() {
	}

	public static synchronized void bootstrap() {
		if (!ready) {
			SharedConstants.createGameVersion();
			Bootstrap.initialize();
			// 1.21.2+ throws "Tags not bound" on any tag check of a block or item until its registry's tags are refreshed. The game does
			// that when it freezes the registries, which would stop the tests from creating blocks, so the tags are set empty and
			// refreshed here instead: no data pack loads, and an empty tag is what an unbound one answered on 1.21.1.
			bindEmptyTags(Registries.BLOCK);
			bindEmptyTags(Registries.ITEM);
			bindEmptyTags(Registries.FLUID);
			bindEmptyTags(Registries.ENTITY_TYPE);
			ready = true;
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> void bindEmptyTags(Registry<T> registry) {
		try {
			Field tagsField = SimpleRegistry.class.getDeclaredField("tags");
			tagsField.setAccessible(true);
			Map<TagKey<T>, ?> tags = (Map<TagKey<T>, ?>) tagsField.get(registry);
			for (TagKey<T> tag : new ArrayList<>(tags.keySet())) {
				((MutableRegistry<T>) registry).setEntries(tag, List.of());
			}
			Method fromMap = Class.forName("net.minecraft.registry.SimpleRegistry$TagLookup").getDeclaredMethod("fromMap", Map.class);
			fromMap.setAccessible(true);
			Field lookup = SimpleRegistry.class.getDeclaredField("tagLookup");
			lookup.setAccessible(true);
			lookup.set(registry, fromMap.invoke(null, tags));
			Method refresh = SimpleRegistry.class.getDeclaredMethod("refreshTags");
			refresh.setAccessible(true);
			refresh.invoke(registry);
		} catch (ReflectiveOperationException failed) {
			throw new IllegalStateException(failed);
		}
	}
}
