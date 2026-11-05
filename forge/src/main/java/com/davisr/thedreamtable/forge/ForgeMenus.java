package com.davisr.thedreamtable.forge;

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
import net.minecraftforge.common.extensions.IForgeMenuType;

import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/** Forge's menu types with extra opening data, written with the codec each type was created with. */
final class ForgeMenus implements Menus {
	private final Map<ScreenHandlerType<?>, PacketCodec<? super RegistryByteBuf, ?>> codecs = new ConcurrentHashMap<>();

	@Override
	public <T extends ScreenHandler, D> ScreenHandlerType<T> extendedType(Factory<T, D> factory, PacketCodec<? super RegistryByteBuf, D> codec) {
		// Forge hands over a plain buffer; the codecs may read registry-bound values, so give them the registries.
		ScreenHandlerType<T> type = IForgeMenuType.create((syncId, inventory, buf) ->
				factory.create(syncId, inventory, codec.decode(new RegistryByteBuf(buf, inventory.player.getRegistryManager()))));
		codecs.put(type, codec);
		return type;
	}

	@Override
	public <D> OptionalInt open(ServerPlayerEntity player, ExtendedMenuProvider<D> provider) {
		// Forge writes the opening data before it asks for the menu, so the menu is created inside the writer, with the
		// sync id Forge has just picked, to learn its type's codec; Forge's later request gets that same menu.
		ScreenHandler[] created = new ScreenHandler[1];
		NamedScreenHandlerFactory precreated = new NamedScreenHandlerFactory() {
			@Override
			public Text getDisplayName() {
				return provider.getDisplayName();
			}

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity opener) {
				return created[0];
			}
		};
		player.openMenu(precreated, buf -> {
			created[0] = provider.createMenu(player.screenHandlerSyncId, player.getInventory(), player);
			if (created[0] != null) {
				write(new RegistryByteBuf(buf, player.getRegistryManager()), created[0], provider.openingData(player));
			}
		});
		return created[0] != null && player.currentScreenHandler == created[0] ? OptionalInt.of(created[0].syncId) : OptionalInt.empty();
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
