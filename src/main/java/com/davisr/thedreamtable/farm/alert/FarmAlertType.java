package com.davisr.thedreamtable.farm.alert;

/** Server-observed conditions and informational events attached to a finished farm. */
public enum FarmAlertType {
	STORAGE_FULL,
	BLOCKS_MISSING,
	DESIGN_UNAVAILABLE,
	REPAIR_BLOCKED,
	REPAIR_FAILED,
	REPAIR_COMPLETED
}
