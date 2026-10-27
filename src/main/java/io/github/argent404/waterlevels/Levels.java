package io.github.argent404.waterlevels;

import io.github.argent404.waterlevels.duck.WLChunk;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Looking up water levels. Editing is in {@link LevelEdits}, saving and syncing in {@link LevelData}.
 *
 * <p>A level (1-8) is the water surface height in eighths of a block. It only applies to still
 * water with no water above it; anywhere else it is ignored. Because "level" always means this,
 * Minecraft's {@code Level} is called {@code world} throughout the mod.
 *
 * <p>Safe off the main thread: level data is immutable and replaced whole on each edit through a
 * volatile field, so a reader always sees a complete old or new copy.
 */
public final class Levels {
	/**
	 * Leveled surfaces are drawn 1/32 of a block (one pixel of a 32x texture) below their true
	 * height so they don't sit flush with an adjacent slab top. Rendering only.
	 */
	public static final float VISUAL_DROP = 1 / 32f;

	/** Vanilla height of a still water surface. */
	private static final float SOURCE_HEIGHT = 8 / 9.0f;

	private static final VoxelShape[] VOLUMES = new VoxelShape[9];

	static {
		for (int i = 1; i <= 8; i++) {
			VOLUMES[i] = Shapes.box(0, 0, 0, 1, i / 8.0, 1);
		}
	}

	private Levels() {
	}

	/**
	 * Returns the leveled height at {@code pos}, or {@code original} if there is no level.
	 * Vanilla only returns SOURCE_HEIGHT for surface source water, so other water exits early.
	 */
	public static float adjust(float original, Fluid fluid, BlockGetter view, BlockPos pos) {
		if (original != SOURCE_HEIGHT) {
			return original;
		}

		int level = level(fluid, view, pos);
		return level > 0 ? level / 8f : original;
	}

	/** {@link #adjust} for rendering, lowered by {@link #VISUAL_DROP}. */
	public static float renderHeight(float original, Fluid fluid, BlockGetter view, BlockPos pos) {
		float height = adjust(original, fluid, view, pos);
		return height != original ? height - VISUAL_DROP : original;
	}

	/**
	 * Fluid shape for leveled water, or null to use vanilla's. Vanilla caches one shape per
	 * FluidState, so leveled positions must not reach that cache.
	 */
	public static VoxelShape shape(FluidState state, BlockGetter view, BlockPos pos) {
		if (level(state.getType(), view, pos) == 0) {
			return null;
		}

		return Shapes.box(0, 0, 0, 1, state.getHeight(view, pos), 1);
	}

	/**
	 * Whether an opaque block fully covers this water (e.g. a bottom slab holding level 4),
	 * in which case the surface is not drawn.
	 */
	public static boolean isSurfaceHidden(Fluid fluid, BlockGetter levelSource, BlockPos pos, BlockState state, BlockGetter view) {
		if (!state.canOcclude()) {
			return false;
		}

		int level = level(fluid, levelSource, pos);
		return level > 0 && !Shapes.joinIsNotEmpty(VOLUMES[level], state.getOcclusionShape(view, pos), BooleanOp.ONLY_FIRST);
	}

	/**
	 * Whether a level is stored at {@code pos} and currently applies: still water with no water
	 * above, the same case as {@link #adjust}. Levels stay stored after their water is removed or
	 * covered, until the chunk is next pruned.
	 */
	public static boolean hasLevel(BlockGetter view, BlockPos pos) {
		if (level(Fluids.WATER, view, pos) == 0) {
			return false;
		}

		FluidState fluid = view.getFluidState(pos);
		return fluid.is(FluidTags.WATER) && fluid.isSource() && !view.getFluidState(pos.above()).is(FluidTags.WATER);
	}

	/**
	 * Whether any level is stored at height {@code y} within one block of (x, z). Lets renderers
	 * skip per-block lookups for water nowhere near a level.
	 */
	public static boolean hasLayerNear(Level world, int x, int y, int z) {
		for (int cx = (x - 1) >> 4; cx <= (x + 1) >> 4; cx++) {
			for (int cz = (z - 1) >> 4; cz <= (z + 1) >> 4; cz++) {
				LevelChunk chunk = world.getChunkSource().getChunkNow(cx, cz);
				ChunkLevels levels = chunk == null ? null : ((WLChunk) chunk).waterlevels$levels();

				if (levels != null && levels.hasLayer(y)) {
					return true;
				}
			}
		}

		return false;
	}

	/** The stored level at {@code pos} as a height (level / 8), or -1 if none. */
	public static float levelHeight(BlockGetter view, BlockPos pos) {
		int level = level(Fluids.WATER, view, pos);
		return level > 0 ? level / 8f : -1;
	}

	/**
	 * The stored level (1-8) at {@code pos}, or 0. Never loads a chunk: getChunkNow returns null
	 * for unloaded chunks, and on the server also off the server thread.
	 */
	private static int level(Fluid fluid, BlockGetter view, BlockPos pos) {
		if (!(view instanceof Level world) || !fluid.isSame(Fluids.WATER)) {
			return 0;
		}

		LevelChunk chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
		ChunkLevels levels = chunk == null ? null : ((WLChunk) chunk).waterlevels$levels();
		return levels == null ? 0 : levels.get(pos.getX(), pos.getY(), pos.getZ());
	}
}
