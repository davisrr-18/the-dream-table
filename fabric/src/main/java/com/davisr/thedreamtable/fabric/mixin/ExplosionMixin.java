package com.davisr.thedreamtable.fabric.mixin;

import com.davisr.thedreamtable.fabric.FabricGriefHooks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Fabric API has no explosion event: this is the hook {@code ServerEvents.onExplosion} uses on Fabric. */
@Mixin(ExplosionImpl.class)
abstract class ExplosionMixin {
	/**
	 * Where the server-side explosion has chosen its blocks and nothing is destroyed yet. The returned list is the one
	 * {@code explode()} destroys, burns and counts, so removing positions here protects them from all three.
	 */
	@Inject(method = "getBlocksToDestroy()Ljava/util/List;", at = @At("RETURN"))
	private void thedreamtable$filterBlocks(CallbackInfoReturnable<List<BlockPos>> info) {
		ExplosionImpl self = (ExplosionImpl) (Object) this;
		FabricGriefHooks.filterExplosion(self.getWorld(), self.getCausingEntity(), info.getReturnValue());
	}
}
