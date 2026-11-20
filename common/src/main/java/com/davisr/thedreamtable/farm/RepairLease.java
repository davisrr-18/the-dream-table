package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.Nbts;
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
		Nbts.putUuid(nbt, "RepairOperationId", operationId);
		Nbts.putUuid(nbt, "RepairActor", actorUuid);
		nbt.putLong("RepairTable", tablePos.asLong());
	}

	static RepairLease read(NbtCompound nbt) {
		if (!Nbts.containsUuid(nbt, "RepairOperationId") || !Nbts.containsUuid(nbt, "RepairActor")) {
			return null;
		}
		return new RepairLease(Nbts.getUuid(nbt, "RepairOperationId"), Nbts.getUuid(nbt, "RepairActor"), BlockPos.fromLong(nbt.getLong("RepairTable", 0L)));
	}
}
