package io.github.argent404.waterlevels;

import io.github.argent404.waterlevels.duck.WLChunk;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Reading, editing, saving and syncing water levels.
 *
 * <p>A level (1-8) is the water surface height in eighths of a block. It only applies to still
 * water with no water above it; anywhere else it is ignored.
 */
public final class Levels {
	private Levels() {}

	public static final String NBT_KEY = "waterlevels:levels";
	/** Vanilla height of a still water surface. */
	private static final float SOURCE_HEIGHT = 8 / 9.0f;

	// Lookups (safe on any thread)

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
		if (!nbt.contains(NBT_KEY, Tag.TAG_COMPOUND)) return null;
		CompoundTag tag = nbt.getCompound(NBT_KEY);
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
