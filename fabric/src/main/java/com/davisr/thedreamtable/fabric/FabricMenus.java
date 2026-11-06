package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.platform.ExtendedMenuProvider;
import com.davisr.thedreamtable.platform.Menus;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import com.davisr.thedreamtable.network.codec.RegistryByteBuf;
import com.davisr.thedreamtable.network.codec.PacketCodec;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/** Fabric API's extended screen handlers, whose opening data is written with the codec each type was created with. */
final class FabricMenus implements Menus {
	private final Map<ScreenHandlerType<?>, PacketCodec<? super RegistryByteBuf, ?>> codecs = new ConcurrentHashMap<>();

	@Override
	public <T extends ScreenHandler, D> ScreenHandlerType<T> extendedType(Factory<T, D> factory, PacketCodec<? super RegistryByteBuf, D> codec) {
		ScreenHandlerType<T> type = new ExtendedScreenHandlerType<>((syncId, inventory, buf) -> factory.create(syncId, inventory, codec.decode(new RegistryByteBuf(buf))));
		codecs.put(type, codec);
		return type;
	}

	@Override
	public <D> OptionalInt open(ServerPlayerEntity player, ExtendedMenuProvider<D> provider) {
		// Fabric writes the opening data after it created the menu but without passing it, so the menu is kept to find its type's codec.
		ScreenHandler[] created = new ScreenHandler[1];
		return player.openHandledScreen(new ExtendedScreenHandlerFactory() {
			@Override
			public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
				write(new RegistryByteBuf(buf), created[0], provider.openingData(opener));
			}

			@Override
			public Text getDisplayName() {
				return provider.getDisplayName();
			}

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity opener) {
				created[0] = provider.createMenu(syncId, inventory, opener);
				return created[0];
			}
		});
	}

	@SuppressWarnings("unchecked")
	private <D> void write(RegistryByteBuf buf, ScreenHandler menu, D data) {
		PacketCodec<? super RegistryByteBuf, D> codec = menu == null ? null : (PacketCodec<? super RegistryByteBuf, D>) codecs.get(menu.getType());
		if (codec == null) {
			throw new IllegalStateException("Menu type was not created through Menus.extendedType: " + (menu == null ? null : menu.getType()));
		}
		codec.encode(buf, data);
	}
}
