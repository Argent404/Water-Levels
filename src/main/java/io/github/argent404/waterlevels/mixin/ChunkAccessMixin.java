package io.github.argent404.waterlevels.mixin;

import io.github.argent404.waterlevels.ChunkLevels;
import io.github.argent404.waterlevels.duck.WLChunk;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Adds the levels field to chunks. */
@Mixin(ChunkAccess.class)
public abstract class ChunkAccessMixin implements WLChunk {
	@Unique
	private ChunkLevels waterlevels$levels;

	@Override
	public ChunkLevels waterlevels$levels() {
		return this.waterlevels$levels;
	}

	@Override
	public void waterlevels$setLevels(ChunkLevels levels) {
		this.waterlevels$levels = levels;
	}
}
