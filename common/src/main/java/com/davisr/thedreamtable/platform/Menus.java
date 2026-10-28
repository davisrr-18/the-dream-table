package com.davisr.thedreamtable.platform;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.OptionalInt;

/** Menu types whose opening carries extra data from the server to the client. */
public interface Menus {
	/** A menu type that is not registered yet; the caller registers it under its own id. */
	<T extends ScreenHandler, D> ScreenHandlerType<T> extendedType(Factory<T, D> factory, PacketCodec<? super RegistryByteBuf, D> codec);

	/** Opens the menu for the player; the provider's opening data reaches the client's {@link Factory}. */
	<D> OptionalInt open(ServerPlayerEntity player, ExtendedMenuProvider<D> provider);

	/** The client-side menu constructor, given the decoded opening data. */
	@FunctionalInterface
	interface Factory<T extends ScreenHandler, D> {
		T create(int syncId, PlayerInventory inventory, D data);
	}
}
