package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.DataFixes;
import com.davisr.thedreamtable.Nbts;
import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmOptions;
import com.davisr.thedreamtable.platform.Content;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A table saved by Minecraft 1.21.1 (fixtures made by the 1.21.1 build of this mod, see
 * {@code fixtures/1.21.1/README.md}) must load on 1.21.11, through the new view reader, and save back the same
 * sessions and jobs. A malformed entry must come back unchanged instead of being dropped.
 */
class TableSaveFormatTest {
	private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID SHARED = new UUID(0L, 0L);
	private static final BlockPos TABLE = new BlockPos(100, 64, 100);
	/** The static registries resolve items; the empty manager resolves none. Built after the bootstrap. */
	private static DynamicRegistryManager registries() {
		return DynamicRegistryManager.of(Registries.REGISTRIES);
	}

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
		// ModBlockEntities creates the table's type through the loader's Content seam; tests install a stand-in.
		Platform.install(null, null, null, null, null, null, new StandInContent());
	}

	private static NbtCompound fixture(String name) throws IOException {
		try (InputStream in = TableSaveFormatTest.class.getResourceAsStream("/fixtures/1.21.1/" + name)) {
			return NbtIo.readCompressed(in, NbtSizeTracker.ofUnlimitedBytes());
		}
	}

	private static DreamTableBlockEntity table() {
		BlockState state = ModBlocks.DREAM_TABLE.get().getDefaultState();
		return new DreamTableBlockEntity(TABLE, state);
	}

	private static DreamTableBlockEntity load(NbtCompound saved) {
		DreamTableBlockEntity table = table();
		try (ErrorReporter.Logging reporter = new ErrorReporter.Logging(() -> "table test", TheDreamTableMod.LOGGER)) {
			table.read(NbtReadView.create(reporter, registries(), saved));
		}
		return table;
	}

	private static NbtCompound save(DreamTableBlockEntity table) {
		try (ErrorReporter.Logging reporter = new ErrorReporter.Logging(() -> "table test", TheDreamTableMod.LOGGER)) {
			NbtWriteView view = NbtWriteView.create(reporter, registries());
			table.writeDataWithoutId(view);
			return view.getNbt();
		}
	}

	@Test
	void aTableSavedBy1_21_1LoadsItsSessionAndItsDiscardJob() throws IOException {
		NbtCompound saved = fixture("table_block_entity.dat");

		DreamTableBlockEntity loaded = load(saved);

		TableSession session = loaded.session(PLAYER);
		assertEquals(FarmOptions.SUGAR_CANE.id(), session.getSelectedFarm());
		assertTrue(ItemStack.areEqual(new ItemStack(Items.LAPIS_LAZULI, 5), session.getMaterials().getStack(0)));
		assertTrue(ItemStack.areEqual(new ItemStack(Items.OAK_PLANKS, 3), session.getMaterials().getStack(1)));
		// The job is still there after the load, for the right player.
		assertEquals(1, save(loaded).getListOrEmpty("Dismantling").size());
		assertEquals(OTHER, Nbts.getUuid(save(loaded).getListOrEmpty("Dismantling").getCompoundOrEmpty(0), "Player"));
	}

	@Test
	void theSessionsAndJobsSaveBackExactlyAsTheyWereRead() throws IOException {
		NbtCompound saved = fixture("table_block_entity.dat");

		NbtCompound back = save(load(saved));

		// Only the keys the mod owns: the vanilla part of a block entity's data is not this test's business. A session
		// now carries the data version of its item stacks (the 1.21.1 file has none), which is the one added key.
		assertEquals(saved.get("Sessions"), withoutStamp(back.getListOrEmpty("Sessions")));
		assertEquals(saved.get("Dismantling"), back.get("Dismantling"));
		for (var entry : back.getListOrEmpty("Sessions")) {
			assertEquals(DataFixes.current(), ((NbtCompound) entry).getInt("DataVersion", 0));
		}
	}

	private static NbtList withoutStamp(NbtList sessions) {
		NbtList copy = sessions.copy();
		for (int index = 0; index < copy.size(); index++) {
			copy.getCompoundOrEmpty(index).remove("DataVersion");
		}
		return copy;
	}

	@Test
	void aTableSavedBeforeSessionsReadsAsTheSharedSessionAndUpgrades() throws IOException {
		NbtCompound legacy = fixture("table_block_entity_legacy.dat");
		assertFalse(legacy.contains("Sessions"));

		DreamTableBlockEntity loaded = load(legacy);

		assertEquals(FarmOptions.CROP.id(), loaded.session(SHARED).getSelectedFarm());
		assertTrue(ItemStack.areEqual(new ItemStack(Items.LAPIS_LAZULI, 2), loaded.session(SHARED).getMaterials().getStack(0)));
		NbtCompound upgraded = save(loaded);
		assertEquals(1, upgraded.getListOrEmpty("Sessions").size());
		assertEquals(upgraded.get("Sessions"), save(load(upgraded)).get("Sessions"));
	}

	@Test
	void aMalformedSessionComesBackUnchangedAndTheOthersStillLoad() throws IOException {
		NbtCompound saved = fixture("table_block_entity.dat");
		NbtList sessions = saved.getListOrEmpty("Sessions");
		NbtCompound broken = new NbtCompound();
		// No Player: the entry cannot be read, so it must be kept as it is for a later version.
		broken.putString("SelectedFarm", "iron_farm");
		sessions.add(broken.copy());

		NbtCompound back = save(load(saved));

		NbtList backSessions = back.getListOrEmpty("Sessions");
		assertEquals(2, backSessions.size());
		assertTrue(backSessions.stream().anyMatch(entry -> entry.equals(broken)));
		assertEquals(FarmOptions.SUGAR_CANE.id(), load(saved).session(PLAYER).getSelectedFarm());
	}

	@Test
	void theClientCopyMarkerLoadsNothing() {
		NbtCompound client = new NbtCompound();
		client.putBoolean("ClientView", true);

		DreamTableBlockEntity loaded = load(client);

		assertTrue(loaded.session(PLAYER).isEmpty());
	}

	/** Creates the entry when first used, as the loader does, and the table's block entity type by reflection. */
	private static final class StandInContent implements Content {
		@Override
		public <R, T extends R> Supplier<T> register(RegistryKey<? extends Registry<R>> registry, Identifier id, Supplier<T> factory) {
			return new Supplier<T>() {
				private T value;

				@Override
				public T get() {
					if (value == null) {
						value = factory.get();
					}
					return value;
				}
			};
		}

		@Override
		public void addToCreativeTab(RegistryKey<ItemGroup> tab, Supplier<? extends ItemConvertible> item) {
		}

		@Override
		@SuppressWarnings({"unchecked", "rawtypes"})
		public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BlockEntityFactory<T> factory, Block... blocks) {
			// 1.21.2+ has no public builder; the constructor taking the factory and the block set is private.
			try {
				for (Constructor<?> constructor : BlockEntityType.class.getDeclaredConstructors()) {
					if (constructor.getParameterCount() == 2) {
						constructor.setAccessible(true);
						BlockEntityType.BlockEntityFactory<T> wrapped = factory::create;
						return (BlockEntityType<T>) constructor.newInstance(wrapped, Set.of(blocks));
					}
				}
				throw new IllegalStateException("no two-argument BlockEntityType constructor");
			} catch (ReflectiveOperationException failed) {
				throw new IllegalStateException(failed);
			}
		}
	}
}
