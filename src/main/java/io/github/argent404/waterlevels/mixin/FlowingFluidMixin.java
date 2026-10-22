package io.github.argent404.waterlevels.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.argent404.waterlevels.Levels;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies levels to fluid height (swimming, boats, camera) and shape (raycasts). */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {

	@ModifyReturnValue(method = "getHeight(Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F",
			at = @At("RETURN"))
	private float waterlevels$height(float original, @Local(argsOnly = true) FluidState state,
			@Local(argsOnly = true) BlockGetter world, @Local(argsOnly = true) BlockPos pos) {
		return Levels.adjust(original, state.getType(), world, pos);
	}

	/** Runs before vanilla's shape cache, which is shared by every block with the same FluidState. */
	@Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
	private void waterlevels$shape(FluidState state, BlockGetter world, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
		VoxelShape shape = Levels.shape(state, world, pos);
		if (shape != null) cir.setReturnValue(shape);
	}
}
