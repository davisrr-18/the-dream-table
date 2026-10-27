package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.farm.schematic.SchematicReader;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the table found wrong with a schematic file. Some kinds keep the file out of My Schematics, with the reason
 * shown in its place; the others are warnings shown before building. {@code names} lists the first few blocks involved.
 */
public record ImportNote(Kind kind, int count, List<String> names) {
	/** Names listed in a note before "and N more". */
	public static final int SHOWN_NAMES = 3;

	public enum Kind {
		/** Not NBT, or damaged. */
		UNREADABLE(true),
		/** NBT, but not in a format the table reads. */
		UNKNOWN_FORMAT(true),
		/** Nothing the table would place. */
		EMPTY(true),
		/** More blocks than {@code SchematicReader.MAX_BLOCKS}; {@code count} is how many. */
		TOO_MANY_BLOCKS(true),
		/** Taller than any world; {@code count} is the height. */
		TOO_TALL(true),
		/** More data than {@code SchematicReader.MAX_BYTES}. */
		TOO_LARGE(true),
		/** Blocks of mods that aren't installed, left out; {@code names} are their ids as written in the file. */
		UNKNOWN_BLOCKS(false),
		/** Blocks a survival player can't get, so only Creative can finish the farm; {@code names} are block ids. */
		CREATIVE_ONLY(false);

		private final boolean rejects;

		Kind(boolean rejects) {
			this.rejects = rejects;
		}
	}

	public static final PacketCodec<RegistryByteBuf, ImportNote> CODEC = PacketCodec.of(
			(note, buf) -> {
				buf.writeString(note.kind().name());
				buf.writeVarInt(note.count());
				buf.writeVarInt(note.names().size());
				for (String name : note.names()) {
					buf.writeString(name);
				}
			},
			buf -> {
				String kind = buf.readString();
				int count = buf.readVarInt();
				int size = Math.min(SHOWN_NAMES, buf.readVarInt());
				List<String> names = new ArrayList<>();
				for (int index = 0; index < size; index++) {
					names.add(buf.readString());
				}
				Kind known;
				try {
					known = Kind.valueOf(kind);
				} catch (IllegalArgumentException unknown) {
					known = Kind.UNREADABLE;
				}
				return new ImportNote(known, count, names);
			});

	public static ImportNote of(Kind kind) {
		return new ImportNote(kind, 0, List.of());
	}

	public static ImportNote of(Kind kind, int count) {
		return new ImportNote(kind, count, List.of());
	}

	/** True when the file can't be used at all. */
	public boolean rejects() {
		return kind.rejects;
	}

	/** "Has 20511 blocks; the limit is 16384", "Needs blocks you can't get in survival: Bedrock, Spawner". */
	public Text text() {
		String key = "gui.the_dream_table.import." + kind.name().toLowerCase(Locale.ROOT);
		return switch (kind) {
			case TOO_MANY_BLOCKS -> Text.translatable(key, count, SchematicReader.MAX_BLOCKS);
			case TOO_TALL -> Text.translatable(key, count);
			case UNKNOWN_BLOCKS -> Text.translatable(key, count, nameList());
			case CREATIVE_ONLY -> Text.translatable(key, nameList());
			default -> Text.translatable(key);
		};
	}

	/** The names, up to {@link #SHOWN_NAMES}, joined with commas. */
	private Text nameList() {
		MutableText list = Text.empty();
		for (int index = 0; index < names.size(); index++) {
			if (index > 0) {
				list.append(Text.translatable("gui.the_dream_table.import.separator"));
			}
			list.append(name(names.get(index)));
		}
		return list;
	}

	private Text name(String raw) {
		Identifier id = Identifier.tryParse(raw);
		if (kind == Kind.CREATIVE_ONLY && id != null && Registries.BLOCK.containsId(id)) {
			return Registries.BLOCK.get(id).getName();
		}
		return Text.literal(raw);
	}
}
