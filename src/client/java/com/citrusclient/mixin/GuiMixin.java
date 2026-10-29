package com.citrusclient.mixin;

import com.citrusclient.CitrusClient;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Third Person Crosshair: the crosshair is normally hidden outside first person; also show it in back view. */
@Mixin(Gui.class)
public class GuiMixin {
	@ModifyExpressionValue(method = "extractCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
	private boolean citrusclient$showInThirdPerson(boolean isFirstPerson) {
		return isFirstPerson || (CitrusClient.CONFIG.thirdPersonCrosshair
				&& Minecraft.getInstance().options.getCameraType() == CameraType.THIRD_PERSON_BACK);
	}
}
