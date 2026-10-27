package io.github.argent404.waterlevels.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.argent404.waterlevels.client.Floating;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Sodium only asks for a model offset when {@code hasOffsetFunction()} is true, so floating blocks
 * report true here and nowhere else. Skipped when Sodium is not installed.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumBlockRendererMixin {
	@WrapOperation(method = "renderModel",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;hasOffsetFunction()Z", remap = true))
	private boolean waterlevels$hasOffsetFunction(BlockState state, Operation<Boolean> original) {
		return original.call(state) || ((Floating) state).waterlevels$isFloating();
	}
}
