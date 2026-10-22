package io.github.argent404.waterlevels.duck;

import io.github.argent404.waterlevels.ChunkLevels;

/** Implemented on every chunk by {@code ChunkAccessMixin}. Levels are null when the chunk has none. */
public interface WLChunk {
	ChunkLevels waterlevels$levels();

	void waterlevels$setLevels(ChunkLevels levels);
}
