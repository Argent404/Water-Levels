package io.github.argent404.waterlevels.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.argent404.waterlevels.Levels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Leveled heights for the vanilla fluid renderer, used without Sodium and by mods that render
 * their own worlds (e.g. Create contraptions). Those virtual worlds are left alone.
 */
@Mixin(LiquidBlockRenderer.class)
public abstract class LiquidBlockRendererMixin {

	@ModifyReturnValue(method = "getHeight(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)F",
			at = @At("RETURN"))
	private float waterlevels$height(float original, @Local(argsOnly = true) BlockAndTintGetter view,
			@Local(argsOnly = true) Fluid fluid, @Local(argsOnly = true) BlockPos pos) {
		return Levels.renderHeight(original, fluid, levelsView(view), pos);
	}

	@Unique
	private static BlockGetter levelsView(BlockAndTintGetter view) {
		return view instanceof Level ? view : Minecraft.getInstance().level;
	}
}
