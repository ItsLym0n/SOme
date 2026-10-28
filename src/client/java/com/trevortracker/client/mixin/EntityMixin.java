package com.trevortracker.client.mixin;

import com.trevortracker.client.TrevorTrackerClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Glow outlines use the team color; give the hunted animal the color from the settings. */
@Mixin(Entity.class)
public class EntityMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void trevortracker$glowColor(CallbackInfoReturnable<Integer> cir) {
		if (TrevorTrackerClient.isGlowTarget((Entity) (Object) this)) {
			cir.setReturnValue(TrevorTrackerClient.CONFIG.glowColor());
		}
	}
}
