package com.davisr.thedreamtable.network.codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.DynamicRegistryManager;

/** Name kept from 1.20.5+ so payload codecs read the same on both branches; 1.20.1 needs no registry access to encode. */
public class RegistryByteBuf extends PacketByteBuf {
	public RegistryByteBuf(ByteBuf parent) {
		super(parent);
	}

	/** Same signature as 1.20.5+; the registries are not needed here. */
	public RegistryByteBuf(ByteBuf parent, DynamicRegistryManager registries) {
		this(parent);
	}
}
