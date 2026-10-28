package com.davisr.thedreamtable.farm;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;
import java.util.UUID;

/** Server-issued token proving which actor and table own an in-progress repair. */
public record RepairLease(UUID operationId, UUID actorUuid, BlockPos tablePos) {
	public RepairLease {
		Objects.requireNonNull(operationId, "operationId");
		Objects.requireNonNull(actorUuid, "actorUuid");
		Objects.requireNonNull(tablePos, "tablePos");
		tablePos = tablePos.toImmutable();
	}

	void write(NbtCompound nbt) {
		nbt.putUuid("RepairOperationId", operationId);
		nbt.putUuid("RepairActor", actorUuid);
		nbt.putLong("RepairTable", tablePos.asLong());
	}

	static RepairLease read(NbtCompound nbt) {
		if (!nbt.containsUuid("RepairOperationId") || !nbt.containsUuid("RepairActor")) {
			return null;
		}
		return new RepairLease(nbt.getUuid("RepairOperationId"), nbt.getUuid("RepairActor"), BlockPos.fromLong(nbt.getLong("RepairTable")));
	}
}
