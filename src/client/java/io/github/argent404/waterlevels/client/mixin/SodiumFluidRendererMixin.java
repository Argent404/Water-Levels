package io.github.argent404.waterlevels.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.argent404.waterlevels.Levels;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Leveled water in Sodium 0.5's fluid renderer (also used by Iris). Skipped when Sodium is not installed.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer", remap = false)
public abstract class SodiumFluidRendererMixin {

	@ModifyReturnValue(method = "fluidHeight", at = @At("RETURN"))
	private float waterlevels$height(float original, @Local(argsOnly = true) Fluid fluid, @Local(argsOnly = true) BlockPos pos) {
		return Levels.renderHeight(original, fluid, Minecraft.getInstance().level, pos);
	}
}
