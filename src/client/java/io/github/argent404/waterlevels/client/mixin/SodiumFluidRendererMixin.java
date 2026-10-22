package io.github.argent404.waterlevels.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.argent404.waterlevels.Levels;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Leveled water in Sodium 0.5's fluid renderer (also used by Iris). Skipped when Sodium is not installed.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer", remap = false)
public abstract class SodiumFluidRendererMixin {

	@Unique private final BlockPos.MutableBlockPos waterlevels$pos = new BlockPos.MutableBlockPos();

	@ModifyReturnValue(method = "fluidHeight", at = @At("RETURN"))
	private float waterlevels$height(float original, @Local(argsOnly = true) Fluid fluid, @Local(argsOnly = true) BlockPos pos) {
		return Levels.renderHeight(original, fluid, Minecraft.getInstance().level, pos);
	}

	/** Hides the top surface of water fully covered by its own block. */
	@ModifyReturnValue(method = "isFluidOccluded", at = @At("RETURN"))
	private boolean waterlevels$occluded(boolean occluded, @Local(argsOnly = true) BlockAndTintGetter world,
										 @Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) int y,
										 @Local(argsOnly = true, ordinal = 2) int z, @Local(argsOnly = true) Direction dir,
										 @Local(argsOnly = true) Fluid fluid) {
		if (occluded || dir != Direction.UP) return occluded;
		BlockPos pos = waterlevels$pos.set(x, y, z);
		return Levels.surfaceHidden(fluid, Minecraft.getInstance().level, pos, world.getBlockState(pos), world);
	}
}
