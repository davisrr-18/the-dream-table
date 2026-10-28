package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.platform.ExtendedMenuProvider;
import com.davisr.thedreamtable.platform.Menus;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.OptionalInt;

/** Fabric API's extended screen handlers, whose opening data is written with the type's codec. */
final class FabricMenus implements Menus {
	@Override
	public <T extends ScreenHandler, D> ScreenHandlerType<T> extendedType(Factory<T, D> factory, PacketCodec<? super RegistryByteBuf, D> codec) {
		return new ExtendedScreenHandlerType<>(factory::create, codec);
	}

	@Override
	public <D> OptionalInt open(ServerPlayerEntity player, ExtendedMenuProvider<D> provider) {
		return player.openHandledScreen(new ExtendedScreenHandlerFactory<D>() {
			@Override
			public D getScreenOpeningData(ServerPlayerEntity opener) {
				return provider.openingData(opener);
			}

			@Override
			public Text getDisplayName() {
				return provider.getDisplayName();
			}

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity opener) {
				return provider.createMenu(syncId, inventory, opener);
			}

			@Override
			public boolean shouldCloseCurrentScreen() {
				return provider.shouldCloseCurrentScreen();
			}
		});
	}
}
