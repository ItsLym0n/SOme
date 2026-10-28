package com.trevortracker.client.mixin;

import com.trevortracker.client.TrevorTrackerClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
	/**
	 * Small mobs stop being drawn past about 58 blocks (a pig) or 33 (a chicken), and a mob
	 * that isn't drawn can't glow. Keep the hunted animal drawn as long as it's loaded.
	 */
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void trevortracker$alwaysRenderTarget(double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
		if (TrevorTrackerClient.isHuntTarget((Entity) (Object) this)) cir.setReturnValue(true);
	}

	/** Glow outlines use the team color; give the hunted animal the color from the settings. */
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void trevortracker$glowColor(CallbackInfoReturnable<Integer> cir) {
		if (TrevorTrackerClient.isGlowTarget((Entity) (Object) this)) {
			cir.setReturnValue(TrevorTrackerClient.CONFIG.glowColor());
		}
	}
}
