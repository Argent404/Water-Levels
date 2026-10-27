package io.github.argent404.waterlevels;

import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import net.minecraft.core.BlockPos;

/**
 * A chunk's water levels. Immutable: edits return a new instance, and nothing writes to the arrays.
 *
 * <p>Only Y layers that contain a level are stored. {@code ys} holds the layer heights in
 * ascending order and {@code data} holds 128 bytes per layer, two 4-bit levels per byte
 * (0 = none). The same arrays are used in memory, in the save file and on the network.
 */
public final class ChunkLevels {
	public static final int LAYER_BYTES = 128;

	final int[] ys;
	final byte[] data;

	private ChunkLevels(int[] ys, byte[] data) {
		this.ys = ys;
		this.data = data;
	}

	/**
	 * Wraps arrays read from disk or the network. Null if empty or malformed: a size that doesn't
	 * match, heights out of order or repeated, or a level above 8.
	 */
	public static ChunkLevels of(int[] ys, byte[] data) {
		if (ys.length == 0 || data.length != ys.length * LAYER_BYTES) {
			return null;
		}

		for (int i = 1; i < ys.length; i++) {
			if (ys[i] <= ys[i - 1]) {
				return null;
			}
		}

		for (byte pair : data) {
			if ((pair & 15) > 8 || (pair >> 4 & 15) > 8) {
				return null;
			}
		}

		return new ChunkLevels(ys, data);
	}

	/** Whether any level is stored at height {@code y}. */
	public boolean hasLayer(int y) {
		for (int stored : this.ys) {
			if (stored == y) {
				return true;
			}
		}

		return false;
	}

	/** The stored level (1-8) at a block in this chunk, or 0. */
	public int get(int x, int y, int z) {
		int[] ys = this.ys;

		for (int i = 0; i < ys.length; i++) {
			if (ys[i] == y) {
				return nibble(this.data, i * LAYER_BYTES, cell(x, z));
			}
		}

		return 0;
	}

	/** Bounds {minX, minZ, maxX, maxZ} of the cells in layer y that differ between a and b, or null. */
	public static int[] changedArea(ChunkLevels a, ChunkLevels b, int y) {
		int[] box = null;

		for (int cell = 0; cell < 256; cell++) {
			int x = cell & 15, z = cell >> 4;

			if ((a == null ? 0 : a.get(x, y, z)) == (b == null ? 0 : b.get(x, y, z))) {
				continue;
			}

			if (box == null) {
				box = new int[]{x, z, x, z};
			}

			box[0] = Math.min(box[0], x);
			box[1] = Math.min(box[1], z);
			box[2] = Math.max(box[2], x);
			box[3] = Math.max(box[3], z);
		}

		return box;
	}

	/** Every layer height present in a or b. */
	public static int[] layersOf(ChunkLevels a, ChunkLevels b) {
		int[] ya = a == null ? new int[0] : a.ys, yb = b == null ? new int[0] : b.ys;
		return Arrays.stream(concat(ya, yb)).distinct().toArray();
	}

	/** Returns a copy with {@code level} (0-8, 0 clears) set at each position, {@code old} if nothing changed, or null if empty. */
	public static ChunkLevels with(ChunkLevels old, List<BlockPos> positions, int level) {
		if (level < 0 || level > 8) {
			throw new IllegalArgumentException("level " + level + " is outside 0-8");
		}

		TreeMap<Integer, byte[]> layers = new TreeMap<>();

		if (old != null) {
			for (int i = 0; i < old.ys.length; i++) {
				layers.put(old.ys[i], Arrays.copyOfRange(old.data, i * LAYER_BYTES, (i + 1) * LAYER_BYTES));
			}
		}

		boolean changed = false;

		for (BlockPos pos : positions) {
			byte[] layer = layers.computeIfAbsent(pos.getY(), y -> new byte[LAYER_BYTES]);
			int cell = cell(pos.getX(), pos.getZ());

			if (nibble(layer, 0, cell) == level) {
				continue;
			}

			setNibble(layer, cell, level);
			changed = true;
		}

		if (!changed) {
			return old;
		}

		layers.values().removeIf(ChunkLevels::isEmpty);

		if (layers.isEmpty()) {
			return null;
		}

		int[] ys = layers.keySet().stream().mapToInt(Integer::intValue).toArray();
		byte[] data = new byte[ys.length * LAYER_BYTES];
		int i = 0;

		for (byte[] layer : layers.values()) {
			System.arraycopy(layer, 0, data, i++ * LAYER_BYTES, LAYER_BYTES);
		}

		return new ChunkLevels(ys, data);
	}

	/** A block's index within its layer, from 0 to 255: rows of 16 along x, one row per z. */
	private static int cell(int x, int z) {
		return ((z & 15) << 4) | (x & 15);
	}

	/** The level of a cell in the layer starting at {@code offset}. Two cells share a byte, even cells in the low 4 bits. */
	private static int nibble(byte[] data, int offset, int cell) {
		return (data[offset + (cell >> 1)] >> ((cell & 1) << 2)) & 15;
	}

	private static void setNibble(byte[] layer, int cell, int level) {
		int shift = (cell & 1) << 2;
		layer[cell >> 1] = (byte) ((layer[cell >> 1] & ~(15 << shift)) | (level << shift));
	}

	private static int[] concat(int[] a, int[] b) {
		int[] out = Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}

	private static boolean isEmpty(byte[] layer) {
		for (byte b : layer) {
			if (b != 0) {
				return false;
			}
		}

		return true;
	}
}
