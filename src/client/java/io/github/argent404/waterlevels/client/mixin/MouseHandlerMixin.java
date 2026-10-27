package io.github.argent404.waterlevels.client.mixin;

import io.github.argent404.waterlevels.WaterLevels;
import io.github.argent404.waterlevels.client.WaterLevelsClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

/** Radius modifier + scroll changes the wand radius instead of the hotbar slot. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void waterlevels$radius(long window, double horizontal, double vertical, CallbackInfo ci) {
		// The key poll asks GLFW, so it goes last.
		if (vertical == 0
				|| minecraft.screen != null
				|| minecraft.player == null
				|| !minecraft.player.getMainHandItem().is(WaterLevels.WATER_WAND)
				|| !WaterLevelsClient.radiusModifierHeld(minecraft)) {
			return;
		}

		WaterLevelsClient.scrollRadius(minecraft, vertical);
		ci.cancel();
	}
}
