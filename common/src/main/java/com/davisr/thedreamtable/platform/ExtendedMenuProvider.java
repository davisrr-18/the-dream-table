package com.davisr.thedreamtable.platform;

import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * A menu that sends extra data when it opens. The data is written with the codec the menu type was created with in
 * {@link Menus#extendedType}, and the client's menu constructor receives it decoded.
 */
public interface ExtendedMenuProvider<D> extends NamedScreenHandlerFactory {
	D openingData(ServerPlayerEntity player);
}
