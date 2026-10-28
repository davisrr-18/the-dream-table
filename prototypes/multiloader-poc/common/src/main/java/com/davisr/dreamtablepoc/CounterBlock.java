package com.davisr.dreamtablepoc;

import dev.architectury.registry.menu.ExtendedMenuProvider;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class CounterBlock extends Block {
	public CounterBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
			int counter = CounterState.get(world.getServer()).value();
			MenuRegistry.openExtendedMenu(serverPlayer, new ExtendedMenuProvider() {
				@Override
				public void saveExtraData(PacketByteBuf buf) {
					buf.writeBlockPos(pos);
					buf.writeVarInt(counter);
				}

				@Override
				public Text getDisplayName() {
					return CounterBlock.this.getName();
				}

				@Override
				public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity menuPlayer) {
					return new CounterMenu(syncId, inventory, pos, counter);
				}
			});
		}
		return ActionResult.success(world.isClient);
	}
}
