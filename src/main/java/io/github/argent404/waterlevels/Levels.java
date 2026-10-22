package io.github.argent404.waterlevels;

import io.github.argent404.waterlevels.duck.WLChunk;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Reading, editing, saving and syncing water levels.
 *
 * <p>A level (1-8) is the water surface height in eighths of a block. It only applies to still
 * water with no water above it; anywhere else it is ignored.
 */
public final class Levels {
	private Levels() {}

	public static final String NBT_KEY = "waterlevels:levels";
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
}
