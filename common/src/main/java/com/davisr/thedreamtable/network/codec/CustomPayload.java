package com.davisr.thedreamtable.network.codec;

import net.minecraft.util.Identifier;

/** A mod payload; its {@link Id} is the channel it travels on. */
public interface CustomPayload {
	Id<? extends CustomPayload> getId();

	record Id<T extends CustomPayload>(Identifier id) {
	}
}
