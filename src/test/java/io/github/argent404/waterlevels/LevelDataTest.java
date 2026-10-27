package io.github.argent404.waterlevels;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** The save format (chunk NBT) and the network format, without a running game. */
class LevelDataTest {
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
		ChunkLevels read = LevelData.read(saved(LevelData.NBT_KEY, levels));
		assertEquals(6, read.get(4, 62, 9));
		assertArrayEquals(levels.data, read.data);
	}

	@Test
	void readsTheOldPartialWaterKey() {
		ChunkLevels levels = ChunkLevels.with(null, List.of(new BlockPos(4, 62, 9)), 3);
		assertEquals(3, LevelData.read(saved("partialwater:levels", levels)).get(4, 62, 9));
	}

	@Test
	void chunksWithoutTheKeyHaveNoLevels() {
		assertNull(LevelData.read(new CompoundTag()));
	}

	@Test
	void malformedSavesAreIgnored() {
		CompoundTag tag = new CompoundTag();
		tag.putIntArray("Y", new int[]{64});
		tag.putByteArray("L", new byte[5]);
		CompoundTag chunk = new CompoundTag();
		chunk.put(LevelData.NBT_KEY, tag);
		assertNull(LevelData.read(chunk));
	}

	@Test
	void readsWhatIsSentOverTheNetwork() {
		ChunkLevels levels = ChunkLevels.with(null, List.of(new BlockPos(0, 64, 0), new BlockPos(15, 100, 15)), 8);
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeVarIntArray(levels.ys);
		buf.writeByteArray(levels.data);
		ChunkLevels received = LevelData.receive(buf);
		assertEquals(8, received.get(0, 64, 0));
		assertEquals(8, received.get(15, 100, 15));
	}

	@Test
	void anEmptyMessageClearsTheChunk() {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeVarIntArray(new int[0]);
		buf.writeByteArray(new byte[0]);
		assertNull(LevelData.receive(buf));
	}
}
