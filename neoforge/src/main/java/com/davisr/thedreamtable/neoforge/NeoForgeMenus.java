package com.davisr.thedreamtable.neoforge;

import com.davisr.thedreamtable.platform.ExtendedMenuProvider;
import com.davisr.thedreamtable.platform.Menus;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/** NeoForge's menu types with extra opening data, written with the codec each type was created with. */
final class NeoForgeMenus implements Menus {
	private final Map<ScreenHandlerType<?>, PacketCodec<? super RegistryByteBuf, ?>> codecs = new ConcurrentHashMap<>();

	@Override
	public <T extends ScreenHandler, D> ScreenHandlerType<T> extendedType(Factory<T, D> factory, PacketCodec<? super RegistryByteBuf, D> codec) {
		ScreenHandlerType<T> type = IMenuTypeExtension.create((syncId, inventory, buf) -> factory.create(syncId, inventory, codec.decode(buf)));
		codecs.put(type, codec);
		return type;
	}

	@Override
	public <D> OptionalInt open(ServerPlayerEntity player, ExtendedMenuProvider<D> provider) {
		// NeoForge creates the menu before it writes the opening data, so the data's codec is known by then.
		ScreenHandler[] created = new ScreenHandler[1];
		NamedScreenHandlerFactory recording = new NamedScreenHandlerFactory() {
			@Override
			public Text getDisplayName() {
				return provider.getDisplayName();
			}

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity opener) {
				created[0] = provider.createMenu(syncId, inventory, opener);
				return created[0];
			}
		};
		return player.openMenu(recording, buf -> write(buf, created[0], provider.openingData(player)));
	}

	@SuppressWarnings("unchecked")
	private <D> void write(RegistryByteBuf buf, ScreenHandler menu, D data) {
		PacketCodec<? super RegistryByteBuf, D> codec = (PacketCodec<? super RegistryByteBuf, D>) codecs.get(menu.getType());
		if (codec == null) {
			throw new IllegalStateException("Menu type was not created through Menus.extendedType: " + menu.getType());
		}
		codec.encode(buf, data);
	}
}
