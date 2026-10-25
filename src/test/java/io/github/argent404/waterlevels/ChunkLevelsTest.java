package io.github.argent404.waterlevels;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

class ChunkLevelsTest {
	@Test
	void writesAndReadsBackEveryCellOfALayer() {
		ChunkLevels levels = null;

		for (int cell = 0; cell < 256; cell++) {
			int x = cell & 15, z = cell >> 4;
			levels = ChunkLevels.with(levels, List.of(new BlockPos(x, 63, z)), 1 + cell % 8);
		}

		for (int cell = 0; cell < 256; cell++) {
			assertEquals(1 + cell % 8, levels.get(cell & 15, 63, cell >> 4));
		}

		assertArrayEquals(new int[]{63}, levels.ys);
		assertEquals(ChunkLevels.LAYER_BYTES, levels.data.length);
	}

	@Test
	void packsTwoCellsPerByteLowNibbleFirst() {
		ChunkLevels levels = ChunkLevels.with(null, List.of(new BlockPos(0, 10, 0)), 3);
		levels = ChunkLevels.with(levels, List.of(new BlockPos(1, 10, 0)), 7);
		assertEquals(0x73, levels.data[0] & 0xFF);
	}

	@Test
	void usesOnlyTheLowFourBitsOfWorldCoordinates() {
		ChunkLevels levels = ChunkLevels.with(null, List.of(new BlockPos(-15, 64, 33)), 5);
		assertEquals(5, levels.get(1, 64, 1));
		assertEquals(5, levels.get(-15, 64, 33));
	}

	@Test
	void keepsLayersSortedByHeight() {
		ChunkLevels levels = ChunkLevels.with(null, List.of(new BlockPos(0, 80, 0)), 2);
		levels = ChunkLevels.with(levels, List.of(new BlockPos(0, -20, 0)), 4);
		levels = ChunkLevels.with(levels, List.of(new BlockPos(0, 30, 0)), 6);
		assertArrayEquals(new int[]{-20, 30, 80}, levels.ys);
		assertEquals(4, levels.get(0, -20, 0));
		assertEquals(6, levels.get(0, 30, 0));
		assertEquals(2, levels.get(0, 80, 0));
	}

	@Test
	void returnsTheSameObjectWhenNothingChanges() {
		ChunkLevels levels = ChunkLevels.with(null, List.of(new BlockPos(3, 64, 3)), 4);
		assertSame(levels, ChunkLevels.with(levels, List.of(new BlockPos(3, 64, 3)), 4));
	}

	@Test
	void neverChangesTheOriginal() {
		ChunkLevels before = ChunkLevels.with(null, List.of(new BlockPos(3, 64, 3)), 4);
		ChunkLevels after = ChunkLevels.with(before, List.of(new BlockPos(3, 64, 3)), 8);
		assertEquals(4, before.get(3, 64, 3));
		assertEquals(8, after.get(3, 64, 3));
	}

	@Test
	void clearingTheLastLevelOfALayerDropsTheLayer() {
		ChunkLevels levels = ChunkLevels.with(null, List.of(new BlockPos(0, 64, 0), new BlockPos(0, 70, 0)), 4);
		levels = ChunkLevels.with(levels, List.of(new BlockPos(0, 64, 0)), 0);
		assertArrayEquals(new int[]{70}, levels.ys);
		assertFalse(levels.hasLayer(64));
		assertTrue(levels.hasLayer(70));
	}

	@Test
	void clearingEverythingLeavesNoData() {
		ChunkLevels levels = ChunkLevels.with(null, List.of(new BlockPos(0, 64, 0)), 4);
		assertNull(ChunkLevels.with(levels, List.of(new BlockPos(0, 64, 0)), 0));
	}

	@Test
	void rejectsMalformedArrays() {
		assertNull(ChunkLevels.of(new int[0], new byte[0]));
		assertNull(ChunkLevels.of(new int[]{64}, new byte[ChunkLevels.LAYER_BYTES - 1]));
		assertNull(ChunkLevels.of(new int[]{64, 65}, new byte[ChunkLevels.LAYER_BYTES]));
	}

	@Test
	void unknownHeightsReadAsNoLevel() {
		ChunkLevels levels = ChunkLevels.with(null, List.of(new BlockPos(0, 64, 0)), 4);
		assertEquals(0, levels.get(0, 65, 0));
		assertEquals(0, levels.get(1, 64, 0));
	}

	@Test
	void changedAreaBoundsOnlyTheDifferingCells() {
		ChunkLevels a = ChunkLevels.with(null, List.of(new BlockPos(2, 64, 3)), 4);
		ChunkLevels b = ChunkLevels.with(a, List.of(new BlockPos(5, 64, 9), new BlockPos(7, 64, 4)), 6);
		assertArrayEquals(new int[]{5, 4, 7, 9}, ChunkLevels.changedArea(a, b, 64));
		assertNull(ChunkLevels.changedArea(a, a, 64));
		assertArrayEquals(new int[]{2, 3, 2, 3}, ChunkLevels.changedArea(a, null, 64));
	}

	@Test
	void layersOfCoversBothSides() {
		ChunkLevels a = ChunkLevels.with(null, List.of(new BlockPos(0, 10, 0)), 1);
		ChunkLevels b = ChunkLevels.with(null, List.of(new BlockPos(0, 20, 0)), 1);
		assertArrayEquals(new int[]{10, 20}, ChunkLevels.layersOf(a, b));
		assertArrayEquals(new int[0], ChunkLevels.layersOf(null, null));
	}
}
