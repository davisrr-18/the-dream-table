package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.Nbts;
import com.davisr.thedreamtable.farm.FarmKey;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** Durable identities for a repair; ownership never comes from the actor's table session. */
public record RepairContext(FarmKey farmKey, UUID farmOwnerUuid, UUID actorUuid, UUID operationId) {
	public RepairContext {
		Objects.requireNonNull(farmKey, "farmKey");
		Objects.requireNonNull(farmOwnerUuid, "farmOwnerUuid");
		Objects.requireNonNull(actorUuid, "actorUuid");
		Objects.requireNonNull(operationId, "operationId");
	}

	void write(NbtCompound nbt) {
		nbt.putString("RepairFarmKey", farmKey.externalForm());
		Nbts.putUuid(nbt, "RepairFarmOwner", farmOwnerUuid);
		Nbts.putUuid(nbt, "RepairActor", actorUuid);
		Nbts.putUuid(nbt, "RepairOperationId", operationId);
	}

	static @Nullable RepairContext read(NbtCompound nbt) {
		if (!Nbts.containsUuid(nbt, "RepairFarmOwner") || !Nbts.containsUuid(nbt, "RepairActor") || !Nbts.containsUuid(nbt, "RepairOperationId")) {
			return null;
		}
		try {
			return new RepairContext(FarmKey.parse(nbt.getString("RepairFarmKey", "")), Nbts.getUuid(nbt, "RepairFarmOwner"),
					Nbts.getUuid(nbt, "RepairActor"), Nbts.getUuid(nbt, "RepairOperationId"));
		} catch (IllegalArgumentException malformed) {
			return null;
		}
	}
}
