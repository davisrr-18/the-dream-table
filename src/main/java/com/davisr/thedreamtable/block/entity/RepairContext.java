package com.davisr.thedreamtable.block.entity;

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
		nbt.putUuid("RepairFarmOwner", farmOwnerUuid);
		nbt.putUuid("RepairActor", actorUuid);
		nbt.putUuid("RepairOperationId", operationId);
	}

	static @Nullable RepairContext read(NbtCompound nbt) {
		if (!nbt.containsUuid("RepairFarmOwner") || !nbt.containsUuid("RepairActor") || !nbt.containsUuid("RepairOperationId")) {
			return null;
		}
		try {
			return new RepairContext(FarmKey.parse(nbt.getString("RepairFarmKey")), nbt.getUuid("RepairFarmOwner"),
					nbt.getUuid("RepairActor"), nbt.getUuid("RepairOperationId"));
		} catch (IllegalArgumentException malformed) {
			return null;
		}
	}
}
