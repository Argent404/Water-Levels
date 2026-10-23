package io.github.argent404.waterlevels;

import io.github.argent404.waterlevels.duck.WLChunk;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.tags.FluidTags;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Reading, editing, saving and syncing water levels.
 *
 * <p>A level (1-8) is the water surface height in eighths of a block. It only applies to still
 * water with no water above it; anywhere else it is ignored.
 */
public final class Levels {
	private Levels() {}

	public static final String NBT_KEY = "waterlevels:levels";
	/** Key used by builds released as Partial Water. */
	private static final String LEGACY_NBT_KEY = "partialwater:levels";

	/** Vanilla height of a still water surface. */
	private static final float SOURCE_HEIGHT = 8 / 9.0f;

	// Lookups. Safe off the main thread: level data is immutable and replaced whole on each edit,
	// so a reader always sees a complete old or new copy.

	/**
	 * Returns the leveled height at {@code pos}, or {@code original} if there is no level.
	 * Vanilla only returns SOURCE_HEIGHT for surface source water, so other water exits early.
	 */
	public static float adjust(float original, Fluid fluid, BlockGetter view, BlockPos pos) {
		if (original != SOURCE_HEIGHT) return original;
		int level = level(fluid, view, pos);
		return level > 0 ? level / 8f : original;
	}

	/**
	 * Fluid shape for leveled water, or null to use vanilla's. Vanilla caches one shape per
	 * FluidState, so leveled positions must not reach that cache.
	 */
	public static VoxelShape shape(FluidState state, BlockGetter view, BlockPos pos) {
		if (level(state.getType(), view, pos) == 0) return null;
		return Shapes.box(0, 0, 0, 1, state.getHeight(view, pos), 1);
	}

	private static final VoxelShape[] VOLUMES = new VoxelShape[9];
	static {
		for (int i = 1; i <= 8; i++) VOLUMES[i] = Shapes.box(0, 0, 0, 1, i / 8.0, 1);
	}

	/**
	 * Whether an opaque block fully covers this water (e.g. a bottom slab holding level 4),
	 * in which case the surface is not drawn.
	 */
	public static boolean surfaceHidden(Fluid fluid, BlockGetter levels, BlockPos pos, BlockState state, BlockGetter view) {
		if (!state.canOcclude()) return false;
		int level = level(fluid, levels, pos);
		return level > 0 && !Shapes.joinIsNotEmpty(VOLUMES[level], state.getOcclusionShape(view, pos), BooleanOp.ONLY_FIRST);
	}

	/**
	 * Leveled surfaces are drawn one pixel (1/32) below their true height so they don't sit flush
	 * with an adjacent slab top. Rendering only.
	 */
	public static final float VISUAL_DROP = 1 / 32f;

	/** {@link #adjust} for rendering, lowered by {@link #VISUAL_DROP}. */
	public static float renderHeight(float original, Fluid fluid, BlockGetter view, BlockPos pos) {
		float height = adjust(original, fluid, view, pos);
		return height != original ? height - VISUAL_DROP : original;
	}

	/**
	 * Whether a level is stored at {@code pos} and there is water there for it to apply to. Levels
	 * stay stored after their water is removed, until the chunk is next pruned.
	 */
	public static boolean hasLevel(BlockGetter view, BlockPos pos) {
		return level(Fluids.WATER, view, pos) > 0 && view.getFluidState(pos).is(FluidTags.WATER);
	}

	/**
	 * Whether any level is stored at height {@code y} within one block of (x, z). Lets renderers
	 * skip per-block lookups for water nowhere near a level.
	 */
	public static boolean layerNear(Level world, int x, int y, int z) {
		for (int cx = (x - 1) >> 4; cx <= (x + 1) >> 4; cx++) {
			for (int cz = (z - 1) >> 4; cz <= (z + 1) >> 4; cz++) {
				LevelChunk chunk = world.getChunkSource().getChunkNow(cx, cz);
				ChunkLevels levels = chunk == null ? null : ((WLChunk) chunk).waterlevels$levels();
				if (levels != null && levels.hasLayer(y)) return true;
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
		if (!(view instanceof Level world) || !fluid.isSame(Fluids.WATER)) return 0;
		LevelChunk chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
		ChunkLevels levels = chunk == null ? null : ((WLChunk) chunk).waterlevels$levels();
		if (levels == null) return 0;
		int level = levels.get(pos.getX(), pos.getY(), pos.getZ());
		return level <= 8 ? level : 0;
	}

	// Editing (server thread)

	/**
	 * Wand right-click: adds water with {@link #waterlog}, then sets {@code level} (0 clears) on all
	 * still water at the same height within {@code radius}. Blocks {@code allowed} rejects are left
	 * alone. Returns whether anything changed.
	 */
	public static boolean apply(ServerLevel world, BlockPos center, int level, int radius, Predicate<BlockPos> allowed) {
		boolean watered = waterlog(world, center, true, radius, allowed) > 0;
		if (!world.getFluidState(center).is(Fluids.WATER)) return watered;
		Map<LevelChunk, List<BlockPos>> byChunk = new HashMap<>();
		for (int dx = 1 - radius; dx < radius; dx++) {
			for (int dz = 1 - radius; dz < radius; dz++) {
				if (dx * dx + dz * dz >= radius * radius) continue;
				BlockPos pos = center.offset(dx, 0, dz);
				if (world.getFluidState(pos).is(Fluids.WATER) && allowed.test(pos)) byChunk.computeIfAbsent(world.getChunkAt(pos), c -> new ArrayList<>()).add(pos);
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
					if (dx * dx + dy * dy + dz * dz >= r2) continue;
					BlockPos pos = center.offset(dx, dy, dz);
					BlockState state = world.getBlockState(pos);
					BlockState next = null;
					if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
						if (state.getValue(BlockStateProperties.WATERLOGGED) != add) next = state.setValue(BlockStateProperties.WATERLOGGED, add);
					} else if (dy == 0) {
						if (add && state.isAir()) next = Blocks.WATER.defaultBlockState();
						else if (!add && state.is(Blocks.WATER)) next = Blocks.AIR.defaultBlockState();
					}
					if (next != null && allowed.test(pos) && world.setBlock(pos, next, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) changed++;
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

	/** Chunks pruned since the server started. */
	private static final Map<ServerLevel, LongOpenHashSet> PRUNED = new WeakHashMap<>();

	/**
	 * Drops levels where there is no longer any water. Runs once per chunk per server run, when
	 * the chunk first loads.
	 */
	public static void prune(ServerLevel world, LevelChunk chunk) {
		ChunkLevels levels = ((WLChunk) chunk).waterlevels$levels();
		if (levels == null || !PRUNED.computeIfAbsent(world, w -> new LongOpenHashSet()).add(chunk.getPos().toLong())) return;
		List<BlockPos> dry = new ArrayList<>();
		int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y : levels.ys) {
			for (int cell = 0; cell < 256; cell++) {
				int x = x0 + (cell & 15), z = z0 + (cell >> 4);
				if (levels.get(x, y, z) == 0) continue;
				if (!chunk.getFluidState(pos.set(x, y, z)).is(FluidTags.WATER)) dry.add(pos.immutable());
			}
		}
		if (dry.isEmpty()) return;
		((WLChunk) chunk).waterlevels$setLevels(ChunkLevels.with(levels, dry, 0));
		chunk.setUnsaved(true);
		for (ServerPlayer player : PlayerLookup.tracking(world, chunk.getPos())) send(player, chunk);
	}

	/**
	 * {@code /waterlevels fill}: sets {@code level} on the top still water of every column within
	 * {@code radius}, in loaded chunks only. Returns {changed columns, skipped unloaded columns}.
	 */
	public static int[] fill(ServerLevel world, BlockPos center, int level, int radius) {
		Map<LevelChunk, List<BlockPos>> byChunk = new HashMap<>();
		int unloaded = 0;
		for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
			for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
				if (!world.hasChunk(x >> 4, z >> 4)) { unloaded++; continue; }
				BlockPos surface = surfaceWater(world, x, z);
				if (surface != null) byChunk.computeIfAbsent(world.getChunkAt(surface), c -> new ArrayList<>()).add(surface);
			}
		}
		int columns = 0;
		for (List<BlockPos> list : byChunk.values()) columns += list.size();
		store(world, byChunk, level);
		return new int[]{columns, unloaded};
	}

	/**
	 * The top still water in a column, searching a few blocks below the surface so lily pads and
	 * docks don't hide it. Null if none.
	 */
	private static BlockPos surfaceWater(ServerLevel world, int x, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
		for (int i = 0; i < 8; i++, pos.move(Direction.DOWN)) {
			if (world.getFluidState(pos).is(Fluids.WATER)) return pos.immutable();
		}
		return null;
	}

	/** Writes the levels per chunk and syncs each changed chunk. Returns how many changed. */
	private static int store(ServerLevel world, Map<LevelChunk, List<BlockPos>> byChunk, int level) {
		int changed = 0;
		for (Map.Entry<LevelChunk, List<BlockPos>> e : byChunk.entrySet()) {
			LevelChunk chunk = e.getKey();
			WLChunk data = (WLChunk) chunk;
			ChunkLevels before = data.waterlevels$levels(), after = ChunkLevels.with(before, e.getValue(), level);
			if (after == before) continue;
			data.waterlevels$setLevels(after);
			chunk.setUnsaved(true);
			for (ServerPlayer player : PlayerLookup.tracking(world, chunk.getPos())) send(player, chunk);
			changed++;
		}
		return changed;
	}

	// Save format and networking

	/** Writes the chunk's levels into its NBT, if it has any. */
	public static void write(ChunkAccess chunk, CompoundTag nbt) {
		ChunkLevels levels = ((WLChunk) chunk).waterlevels$levels();
		if (levels == null) return;
		CompoundTag tag = new CompoundTag();
		tag.putIntArray("Y", levels.ys);
		tag.putByteArray("L", levels.data);
		nbt.put(NBT_KEY, tag);
	}

	/** Reads levels written by {@link #write}; null if absent or malformed. */
	public static ChunkLevels read(CompoundTag nbt) {
		String key = nbt.contains(NBT_KEY, Tag.TAG_COMPOUND) ? NBT_KEY
				: nbt.contains(LEGACY_NBT_KEY, Tag.TAG_COMPOUND) ? LEGACY_NBT_KEY : null;
		if (key == null) return null;
		CompoundTag tag = nbt.getCompound(key);
		return ChunkLevels.of(tag.getIntArray("Y").clone(), tag.getByteArray("L").clone());
	}

	/** Sends a chunk's levels to a player, replacing what the client had. */
	public static void send(ServerPlayer player, LevelChunk chunk) {
		ChunkLevels levels = ((WLChunk) chunk).waterlevels$levels();
		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeChunkPos(chunk.getPos());
		buf.writeVarIntArray(levels == null ? new int[0] : levels.ys);
		buf.writeByteArray(levels == null ? new byte[0] : levels.data);
		ServerPlayNetworking.send(player, WaterLevels.LEVELS_PACKET, buf);
	}

	/** Reads a packet from {@link #send}. Sizes are capped against malformed packets. */
	public static ChunkLevels receive(FriendlyByteBuf buf) {
		int[] ys = buf.readVarIntArray(MAX_LAYERS);
		byte[] data = buf.readByteArray(MAX_LAYERS * ChunkLevels.LAYER_BYTES);
		return ChunkLevels.of(ys, data);
	}

	private static final int MAX_LAYERS = 384; // overworld height
}
