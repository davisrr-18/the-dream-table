package com.davisr.thedreamtable.fabric.mixin;

import com.davisr.thedreamtable.fabric.FabricGriefHooks;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fabric API has no mob-griefing event: this is the hook {@code ServerEvents.onEndermanGrief} uses on Fabric. */
@Mixin(targets = "net.minecraft.entity.mob.EndermanEntity$PickUpBlockGoal")
abstract class EndermanPickUpBlockGoalMixin {
	@Shadow
	@Final
	private EndermanEntity enderman;

	/** Only after the game itself would start the goal, so the listeners run for the rare pickup, not every tick. */
	@Inject(method = "canStart()Z", at = @At("RETURN"), cancellable = true)
	private void thedreamtable$guardFarms(CallbackInfoReturnable<Boolean> info) {
		if (info.getReturnValueZ() && enderman.getEntityWorld() instanceof ServerWorld world && !FabricGriefHooks.endermanAllowed(world, enderman)) {
			info.setReturnValue(false);
		}
	}
}
