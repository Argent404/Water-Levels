package io.github.argent404.waterlevels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

import io.github.argent404.waterlevels.duck.WLChunk;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;

/** Editing water levels: the wand, {@code /waterlevels fill} and pruning. Server thread only. */
public final class LevelEdits {
	/** Chunks pruned since the server started. */
	private static final Map<ServerLevel, LongOpenHashSet> PRUNED = new WeakHashMap<>();

	private LevelEdits() {
	}

	/**
	 * Wand right-click: adds water with {@link #waterlog}, then sets {@code level} (0 clears) on all
	 * still water at the same height within {@code radius}. Blocks {@code allowed} rejects are left
	 * alone. Returns whether anything changed.
	 */
	public static boolean apply(ServerLevel world, BlockPos center, int level, int radius, Predicate<BlockPos> allowed) {
		boolean watered = waterlog(world, center, true, radius, allowed) > 0;

		if (!world.getFluidState(center).is(Fluids.WATER)) {
			return watered;
		}

		Map<LevelChunk, List<BlockPos>> byChunk = new HashMap<>();

		for (int dx = 1 - radius; dx < radius; dx++) {
			for (int dz = 1 - radius; dz < radius; dz++) {
				if (dx * dx + dz * dz >= radius * radius) {
					continue;
				}

				BlockPos pos = center.offset(dx, 0, dz);

				if (world.getFluidState(pos).is(Fluids.WATER) && allowed.test(pos)) {
					byChunk.computeIfAbsent(world.getChunkAt(pos), c -> new ArrayList<>()).add(pos);
				}
			}
		}

		return store(world, byChunk, level) > 0 || watered;
	}

	/**
	 * Waterlogs ({@code add}) or drains every block in a bowl (lower half-sphere) of {@code radius}
	 * below {@code center}. On the top layer, air becomes water when adding and water becomes air
	 * when removing. No neighbour updates, so nothing starts to flow. Blocks {@code allowed} rejects
	 * are left alone. Returns the number of blocks changed.
	 */
	public static int waterlog(ServerLevel world, BlockPos center, boolean add, int radius, Predicate<BlockPos> allowed) {
		int changed = 0, r2 = radius * radius;

		for (int dy = 0; dy > -radius; dy--) {
			for (int dx = 1 - radius; dx < radius; dx++) {
				for (int dz = 1 - radius; dz < radius; dz++) {
					if (dx * dx + dy * dy + dz * dz >= r2) {
						continue;
					}

					BlockPos pos = center.offset(dx, dy, dz);
					BlockState state = world.getBlockState(pos);
					BlockState next = null;

					if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
						if (state.getValue(BlockStateProperties.WATERLOGGED) != add) {
							next = state.setValue(BlockStateProperties.WATERLOGGED, add);
						}
					} else if (dy == 0) {
						if (add && state.isAir()) {
							next = Blocks.WATER.defaultBlockState();
						} else if (!add && state.is(Blocks.WATER)) {
							next = Blocks.AIR.defaultBlockState();
						}
					}

					if (next != null && allowed.test(pos) && world.setBlock(pos, next, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) {
						changed++;
					}
				}
			}
		}

		// Placed water schedules its own flow tick; cancel it so it stays still.
		if (add && changed > 0) {
			world.getFluidTicks().clearArea(new BoundingBox(center.getX() - radius, center.getY() - radius, center.getZ() - radius,
					center.getX() + radius, center.getY(), center.getZ() + radius));
		}

		return changed;
	}

	/**
	 * Drops levels where there is no longer any water. Runs once per chunk per server run, when
	 * the chunk first loads.
	 */
	public static void prune(ServerLevel world, LevelChunk chunk) {
		ChunkLevels levels = ((WLChunk) chunk).waterlevels$levels();

		if (levels == null || !PRUNED.computeIfAbsent(world, w -> new LongOpenHashSet()).add(chunk.getPos().toLong())) {
			return;
		}

		List<BlockPos> dry = new ArrayList<>();
		int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int y : levels.ys) {
			for (int cell = 0; cell < 256; cell++) {
				int x = x0 + (cell & 15), z = z0 + (cell >> 4);

				if (levels.get(x, y, z) == 0) {
					continue;
				}

				if (!chunk.getFluidState(pos.set(x, y, z)).is(FluidTags.WATER)) {
					dry.add(pos.immutable());
				}
			}
		}

		if (dry.isEmpty()) {
			return;
		}

		((WLChunk) chunk).waterlevels$setLevels(ChunkLevels.with(levels, dry, 0));
		chunk.setUnsaved(true);

		for (ServerPlayer player : PlayerLookup.tracking(world, chunk.getPos())) {
			LevelData.send(player, chunk);
		}
	}

	/**
	 * {@code /waterlevels fill}: sets {@code level} on the top still water of every column within
	 * {@code radius}, in loaded chunks only. Returns {columns changed, columns skipped as unloaded}.
	 */
	public static int[] fill(ServerLevel world, BlockPos center, int level, int radius) {
		Map<LevelChunk, List<BlockPos>> byChunk = new HashMap<>();
		int unloaded = 0;

		for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
			for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
				if (!world.hasChunk(x >> 4, z >> 4)) {
					unloaded++;
					continue;
				}

				BlockPos surface = surfaceWater(world, x, z);

				if (surface != null) {
					byChunk.computeIfAbsent(world.getChunkAt(surface), c -> new ArrayList<>()).add(surface);
				}
			}
		}

		return new int[]{store(world, byChunk, level), unloaded};
	}

	/**
	 * The top still water in a column, searching a few blocks below the surface so lily pads and
	 * docks don't hide it. Null if none.
	 */
	private static BlockPos surfaceWater(ServerLevel world, int x, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);

		for (int i = 0; i < 8; i++, pos.move(Direction.DOWN)) {
			if (world.getFluidState(pos).is(Fluids.WATER)) {
				return pos.immutable();
			}
		}

		return null;
	}

	/** Writes the levels per chunk and syncs each changed chunk. Returns how many positions changed. */
	private static int store(ServerLevel world, Map<LevelChunk, List<BlockPos>> byChunk, int level) {
		int changed = 0;

		for (Map.Entry<LevelChunk, List<BlockPos>> e : byChunk.entrySet()) {
			LevelChunk chunk = e.getKey();
			WLChunk data = (WLChunk) chunk;
			ChunkLevels before = data.waterlevels$levels();
			int changedHere = countChanges(before, e.getValue(), level);

			if (changedHere == 0) {
				continue;
			}

			data.waterlevels$setLevels(ChunkLevels.with(before, e.getValue(), level));
			chunk.setUnsaved(true);

			for (ServerPlayer player : PlayerLookup.tracking(world, chunk.getPos())) {
				LevelData.send(player, chunk);
			}

			changed += changedHere;
		}

		return changed;
	}

	/** How many of {@code positions} don't already hold {@code level}. */
	private static int countChanges(ChunkLevels before, List<BlockPos> positions, int level) {
		int count = 0;

		for (BlockPos pos : positions) {
			int current = before == null ? 0 : before.get(pos.getX(), pos.getY(), pos.getZ());

			if (current != level) {
				count++;
			}
		}

		return count;
	}
}
