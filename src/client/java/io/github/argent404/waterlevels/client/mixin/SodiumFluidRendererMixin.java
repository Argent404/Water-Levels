package io.github.argent404.waterlevels.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.argent404.waterlevels.Levels;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Leveled water in Sodium 0.5's fluid renderer (also used by Iris). Skipped when Sodium is not installed.
 *
 * <p>Sodium weights corner heights of 0.8 and above ten times when averaging, which leaves gaps
 * between leveled blocks. For water that is leveled or next to a level, corners are instead the
 * plain average of the water around them.
 *
 * <p>Sodium uses one FluidRenderer per meshing thread, so the fields below are thread-confined.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer", remap = false)
public abstract class SodiumFluidRendererMixin {

	@Shadow
	private float fluidHeight(BlockAndTintGetter world, Fluid fluid, BlockPos blockPos, Direction direction) {
		throw new AssertionError();
	}

	@Unique private final BlockPos.MutableBlockPos waterlevels$pos = new BlockPos.MutableBlockPos();
	@Unique private final BlockPos.MutableBlockPos waterlevels$center = new BlockPos.MutableBlockPos();
	/** Whether this surface, or one of its 8 neighbours, is leveled. */
	@Unique private boolean waterlevels$leveled;

	@Inject(method = "render", at = @At("HEAD"))
	private void waterlevels$begin(CallbackInfo ci, @Local(argsOnly = true) FluidState fluidState, @Local(argsOnly = true, ordinal = 0) BlockPos blockPos) {
		waterlevels$leveled = false;
		Level world = Minecraft.getInstance().level;
		if (world == null || !fluidState.is(FluidTags.WATER)) return;
		BlockPos.MutableBlockPos p = waterlevels$pos;
		if (world.getFluidState(p.setWithOffset(blockPos, Direction.UP)).is(FluidTags.WATER)) return; // not a surface
		waterlevels$center.set(blockPos);
		// A corner is shared by 4 blocks, so all of them must compute it the same way.
		boolean leveled = false;
		for (int dx = -1; dx <= 1 && !leveled; dx++) {
			for (int dz = -1; dz <= 1 && !leveled; dz++) {
				leveled = Levels.hasLevel(world, p.set(blockPos.getX() + dx, blockPos.getY(), blockPos.getZ() + dz));
			}
		}
		waterlevels$leveled = leveled;
	}

	@ModifyReturnValue(method = "fluidHeight", at = @At("RETURN"))
	private float waterlevels$height(float original, @Local(argsOnly = true) Fluid fluid, @Local(argsOnly = true) BlockPos pos) {
		return Levels.renderHeight(original, fluid, Minecraft.getInstance().level, pos);
	}

	/** Water above a neighbour (height 1) still pulls the corner to 1, as in vanilla. */
	@Inject(method = "fluidCornerHeight", at = @At("HEAD"), cancellable = true)
	private void waterlevels$corner(BlockAndTintGetter world, Fluid fluid, float own, float x, float y, BlockPos diagonal,
									CallbackInfoReturnable<Float> cir) {
		if (!waterlevels$leveled) return;
		if (x >= 1.0f || y >= 1.0f) {
			cir.setReturnValue(1.0f);
			return;
		}
		// Average of the water heights around the corner (0 = air and -1 = solid are skipped).
		if (!waterlevels$cornerTouchesLevel(diagonal)) return;
		float sum = own;
		int count = 1;
		if (x > 0) { sum += x; count++; }
		if (y > 0) { sum += y; count++; }
		if (x > 0 || y > 0) {
			float d = fluidHeight(world, fluid, diagonal, Direction.UP);
			if (d >= 1.0f) {
				cir.setReturnValue(1.0f);
				return;
			}
			if (d > 0) { sum += d; count++; }
		}
		cir.setReturnValue(sum / count);
	}

	/** Whether any of the 4 blocks around this corner is leveled. */
	@Unique
	private boolean waterlevels$cornerTouchesLevel(BlockPos diagonal) {
		Level world = Minecraft.getInstance().level;
		BlockPos c = waterlevels$center;
		BlockPos.MutableBlockPos p = waterlevels$pos;
		return Levels.hasLevel(world, c)
				|| Levels.hasLevel(world, diagonal)
				|| Levels.hasLevel(world, p.set(diagonal.getX(), c.getY(), c.getZ()))
				|| Levels.hasLevel(world, p.set(c.getX(), c.getY(), diagonal.getZ()));
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
