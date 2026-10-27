package io.github.argent404.waterlevels.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.argent404.waterlevels.Levels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

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
	/** Whether any level is stored near this block; if not, every hook returns immediately. */
	@Unique private boolean waterlevels$isNearLevel = true;
	/** Whether this surface, or one of its 8 neighbours, is leveled. */
	@Unique private boolean waterlevels$leveled;
	/** Whether leveled water sits one block up next to this block (a step). */
	@Unique private boolean waterlevels$hasStepUp;
	/** Lower water at least this high joins the step above it. */
	@Unique private static final float WATERLEVELS_STEP_JOIN_HEIGHT = 0.8f;
	/**
	 * Lowest corner height returned. Sodium lowers exposed top corners by 0.001 and crashes on a
	 * side face whose corners are both negative, so a corner must never be 0.
	 */
	@Unique private static final float WATERLEVELS_STEP_FLOOR = 0.01f;

	@Inject(method = "render", at = @At("HEAD"))
	private void waterlevels$begin(CallbackInfo ci, @Local(argsOnly = true) FluidState fluidState, @Local(argsOnly = true, ordinal = 0) BlockPos blockPos) {
		waterlevels$leveled = false;
		waterlevels$hasStepUp = false;
		Level world = Minecraft.getInstance().level;
		int bx = blockPos.getX(), by = blockPos.getY(), bz = blockPos.getZ();
		waterlevels$isNearLevel = world != null && fluidState.is(FluidTags.WATER)
				&& (Levels.hasLayerNear(world, bx, by, bz) || Levels.hasLayerNear(world, bx, by + 1, bz) || Levels.hasLayerNear(world, bx, by - 1, bz));
		if (!waterlevels$isNearLevel) {
			return;
		}

		BlockPos.MutableBlockPos p = waterlevels$pos;

		// Not a surface.
		if (world.getFluidState(p.setWithOffset(blockPos, Direction.UP)).is(FluidTags.WATER)) {
			return;
		}

		waterlevels$center.set(blockPos);
		// A corner is shared by 4 blocks, so all of them must compute it the same way.
		boolean leveled = false;

		for (int dx = -1; dx <= 1 && !leveled; dx++) {
			for (int dz = -1; dz <= 1 && !leveled; dz++) {
				leveled = Levels.hasLevel(world, p.set(blockPos.getX() + dx, blockPos.getY(), blockPos.getZ() + dz));
			}
		}

		waterlevels$leveled = leveled;
		boolean hasStepUp = false;

		for (int dx = -1; dx <= 1 && !hasStepUp; dx++) {
			for (int dz = -1; dz <= 1 && !hasStepUp; dz++) {
				hasStepUp = Levels.hasLevel(world, p.set(bx + dx, by + 1, bz + dz));
			}
		}

		waterlevels$hasStepUp = hasStepUp;
	}

	@ModifyReturnValue(method = "fluidHeight", at = @At("RETURN"))
	private float waterlevels$height(float original, @Local(argsOnly = true) Fluid fluid, @Local(argsOnly = true) BlockPos pos) {
		if (!waterlevels$isNearLevel) {
			return original;
		}

		return Levels.renderHeight(original, fluid, Minecraft.getInstance().level, pos);
	}

	/** Water above a neighbour (height 1) still pulls the corner to 1, as in vanilla. */
	@Inject(method = "fluidCornerHeight", at = @At("HEAD"), cancellable = true)
	private void waterlevels$corner(BlockAndTintGetter world, Fluid fluid, float own, float x, float y, BlockPos diagonal,
			CallbackInfoReturnable<Float> cir) {
		if (!waterlevels$leveled && !waterlevels$hasStepUp) {
			return;
		}

		if (x >= 1.0f || y >= 1.0f) {
			cir.setReturnValue(1.0f);
			return;
		}

		// Steps: the lower water rises to the top of the bank block and the upper water comes down to meet it.
		BlockPos c = waterlevels$center;
		BlockPos.MutableBlockPos p = waterlevels$pos;

		for (int i = 0; i < 3; i++) {
			if (i == 0) {
				p.set(diagonal.getX(), c.getY(), c.getZ());
			} else if (i == 1) {
				p.set(c.getX(), c.getY(), diagonal.getZ());
			} else {
				p.set(diagonal);
			}

			float side = fluidHeight(world, fluid, p, Direction.UP);

			if (waterlevels$hasStepUp && side <= 0 && Levels.hasLevel(Minecraft.getInstance().level, p.move(Direction.UP))) {
				cir.setReturnValue(1.0f); // lower side of a step
				return;
			}
		}

		if (waterlevels$leveled) {
			for (int i = 0; i < 3; i++) {
				if (i == 0) {
					p.set(diagonal.getX(), c.getY(), c.getZ());
				} else if (i == 1) {
					p.set(c.getX(), c.getY(), diagonal.getZ());
				} else {
					p.set(diagonal);
				}

				if (fluidHeight(world, fluid, p, Direction.UP) != 0) {
					continue;
				}

				if (fluidHeight(world, fluid, p.move(Direction.DOWN), Direction.UP) >= WATERLEVELS_STEP_JOIN_HEIGHT) {
					cir.setReturnValue(WATERLEVELS_STEP_FLOOR); // upper side of a step
					return;
				}
			}
		}

		if (!waterlevels$leveled) {
			return;
		}

		// Average of the water heights around the corner (0 = air and -1 = solid are skipped).
		if (!waterlevels$cornerTouchesLevel(diagonal)) {
			return;
		}

		float sum = own;
		int count = 1;

		if (x > 0) {
			sum += x;
			count++;
		}

		if (y > 0) {
			sum += y;
			count++;
		}

		if (x > 0 || y > 0) {
			float d = fluidHeight(world, fluid, diagonal, Direction.UP);

			if (d >= 1.0f) {
				cir.setReturnValue(1.0f);
				return;
			}

			if (d > 0) {
				sum += d;
				count++;
			}
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
	@Inject(method = "isFluidOccluded", at = @At("RETURN"), cancellable = true)
	private void waterlevels$occluded(BlockAndTintGetter world, int x, int y, int z, Direction dir, Fluid fluid,
			CallbackInfoReturnable<Boolean> cir) {
		if (!waterlevels$isNearLevel || cir.getReturnValueZ() || dir != Direction.UP) {
			return;
		}

		BlockPos pos = waterlevels$pos.set(x, y, z);

		if (Levels.isSurfaceHidden(fluid, Minecraft.getInstance().level, pos, world.getBlockState(pos), world)) {
			cir.setReturnValue(true);
		}
	}
}
