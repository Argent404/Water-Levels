package io.github.argent404.waterlevels.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.argent404.waterlevels.Levels;
import io.github.argent404.waterlevels.client.Floating;
import io.github.argent404.waterlevels.client.FloatsTag;
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
	/**
	 * Tag generation times 2, plus 1 if the block floats; -1 until first asked. One field, because
	 * Sodium's mesh threads read it and two fields could be seen half updated.
	 */
	@Unique private volatile int waterlevels$floatsCache = -1;

	@Override
	public boolean waterlevels$isFloating() {
		int generation = FloatsTag.generation();
		int cache = waterlevels$floatsCache;

		if (cache >> 1 != generation) {
			boolean floats = ((BlockState) (Object) this).is(FloatsTag.TAG);
			cache = generation << 1 | (floats ? 1 : 0);
			waterlevels$floatsCache = cache;
		}

		return (cache & 1) == 1;
	}

	@ModifyReturnValue(method = "getOffset", at = @At("RETURN"))
	private Vec3 waterlevels$offset(Vec3 original, @Local(argsOnly = true) BlockGetter view, @Local(argsOnly = true) BlockPos pos) {
		if (!waterlevels$isFloating()) {
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
