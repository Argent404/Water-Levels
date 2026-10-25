package io.github.argent404.waterlevels.mixin;

import io.github.argent404.waterlevels.ChunkLevels;
import io.github.argent404.waterlevels.duck.WLChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.world.level.chunk.ChunkAccess;

/** Adds the levels field to chunks. */
@Mixin(ChunkAccess.class)
public abstract class ChunkAccessMixin implements WLChunk {
	/** Volatile: written on the main thread, read by Sodium's mesh threads. */
	@Unique
	private volatile ChunkLevels waterlevels$levels;

	@Override
	public ChunkLevels waterlevels$levels() {
		return this.waterlevels$levels;
	}

	@Override
	public void waterlevels$setLevels(ChunkLevels levels) {
		this.waterlevels$levels = levels;
	}
}
