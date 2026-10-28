package com.trevortracker.client.mixin;

import com.trevortracker.client.TrevorTrackerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes the hunted animal glow, so its outline shows through walls. */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void trevortracker$glowTarget(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (TrevorTrackerClient.isGlowTarget(entity)) cir.setReturnValue(true);
	}
}
