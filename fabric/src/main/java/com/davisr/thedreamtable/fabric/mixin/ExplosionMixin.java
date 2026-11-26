package com.davisr.thedreamtable.fabric.mixin;

import com.davisr.thedreamtable.fabric.FabricGriefHooks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric API has no explosion event: this is the hook {@code ServerEvents.onExplosion} uses on Fabric. */
@Mixin(Explosion.class)
abstract class ExplosionMixin {
	@Shadow
	@Final
	private World world;

	/** Before the blocks of a server-side explosion are destroyed (and before the client is told which ones). */
	@Inject(method = "affectWorld(Z)V", at = @At("HEAD"))
	private void thedreamtable$filterBlocks(boolean particles, CallbackInfo info) {
		if (world instanceof ServerWorld serverWorld) {
			Explosion self = (Explosion) (Object) this;
			FabricGriefHooks.filterExplosion(serverWorld, self.getCausingEntity(), self.getAffectedBlocks());
		}
	}
}
