package io.github.argent404.waterlevels.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.argent404.waterlevels.Levels;
import io.github.argent404.waterlevels.client.Floating;
import io.github.argent404.waterlevels.client.Floats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Lowers the model of blocks in {@code #waterlevels:floats} (lily pads etc.) onto leveled water.
 * Visual only; the hitbox doesn't move.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class FloatingBlockMixin implements Floating {
	@Unique private int waterlevels$tagGeneration = -1;
	@Unique private boolean waterlevels$floats;

	@Override
	public boolean waterlevels$floats() {
		int generation = Floats.generation();

		if (waterlevels$tagGeneration != generation) {
			waterlevels$floats = ((BlockState) (Object) this).is(Floats.TAG);
			waterlevels$tagGeneration = generation;
		}

		return waterlevels$floats;
	}

	@ModifyReturnValue(method = "getOffset", at = @At("RETURN"))
	private Vec3 waterlevels$offset(Vec3 original, @Local(argsOnly = true) BlockGetter view, @Local(argsOnly = true) BlockPos pos) {
		if (!waterlevels$floats()) {
			return original;
		}

		Level world = Minecraft.getInstance().level;

		if (world == null) {
			return original;
		}

		BlockPos below = pos.below();

		if (!view.getFluidState(below).is(FluidTags.WATER)) {
			return original;
		}

		float surface = Levels.levelHeight(world, below);
		return surface < 0 ? original : original.add(0, surface - Levels.VISUAL_DROP - 1.0, 0);
	}
}
