/**
 * The 1.20.5+ payload API ({@code CustomPayload}, {@code PacketCodec}, {@code PacketCodecs}, {@code RegistryByteBuf})
 * rebuilt on 1.20.1's {@link net.minecraft.network.PacketByteBuf}, with the same names and shapes, so the payload
 * classes stay as on {@code main} apart from their imports and fixes cherry-pick with few conflicts. Only the parts the
 * mod uses exist. Fabric sends each payload on its id's channel; Forge sends all of them on one channel, id first.
 */
package com.davisr.thedreamtable.network.codec;
