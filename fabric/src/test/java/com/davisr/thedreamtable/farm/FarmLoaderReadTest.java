package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.DreamTableConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarmLoaderReadTest {
	@Test
	void aSavedListLongerThanTheHardLimitIsCutAtTheLimit() {
		UUID player = UUID.fromString("10000000-0000-0000-0000-000000000001");
		NbtList keys = new NbtList();
		for (int index = 0; index < 500; index++) {
			keys.add(NbtString.of("minecraft:overworld|00000000-0000-0000-0000-" + String.format("%012d", index)));
		}
		NbtCompound players = new NbtCompound();
		players.put(player.toString(), keys);
		NbtCompound nbt = new NbtCompound();
		nbt.put("Players", players);

		NbtCompound written = FarmLoader.read(nbt, null).writeNbt(new NbtCompound(), null);

		assertEquals(DreamTableConfig.MAX_LOADED_LIMIT,
				written.getCompound("Players").getList(player.toString(), NbtElement.STRING_TYPE).size());
	}
}
