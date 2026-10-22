package io.github.argent404.waterlevels;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The save format (chunk NBT), without a running game. */
class LevelsFormatTest {

	private static CompoundTag saved(String key, ChunkLevels levels) {
		CompoundTag tag = new CompoundTag();
		tag.putIntArray("Y", levels.ys);
		tag.putByteArray("L", levels.data);
		CompoundTag chunk = new CompoundTag();
		chunk.put(key, tag);
		return chunk;
	}

	@Test
	void readsTheSavedFormat() {
		ChunkLevels levels = ChunkLevels.with(null, List.of(new BlockPos(4, 62, 9)), 6);
		ChunkLevels read = Levels.read(saved(Levels.NBT_KEY, levels));
		assertEquals(6, read.get(4, 62, 9));
		assertArrayEquals(levels.data, read.data);
	}

	@Test
	void chunksWithoutTheKeyHaveNoLevels() {
		assertNull(Levels.read(new CompoundTag()));
	}

	@Test
	void malformedSavesAreIgnored() {
		CompoundTag tag = new CompoundTag();
		tag.putIntArray("Y", new int[]{64});
		tag.putByteArray("L", new byte[5]);
		CompoundTag chunk = new CompoundTag();
		chunk.put(Levels.NBT_KEY, tag);
		assertNull(Levels.read(chunk));
	}
}
