package io.github.argent404.waterlevels;

import io.github.argent404.waterlevels.duck.WLChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** The save format (chunk NBT) and the network format for water levels. */
public final class LevelData {
	public static final String NBT_KEY = "waterlevels:levels";
	/** Key used by builds released as Partial Water. */
	private static final String LEGACY_NBT_KEY = "partialwater:levels";
	/** Cap on layers per packet, against malformed packets: one per block of the overworld's height. */
	private static final int MAX_LAYERS = 384;
	private static final Logger LOGGER = LoggerFactory.getLogger(WaterLevels.MOD_ID);

	private LevelData() {
	}

	/** Writes the chunk's levels into its NBT, if it has any. */
	public static void write(ChunkAccess chunk, CompoundTag nbt) {
		ChunkLevels levels = ((WLChunk) chunk).waterlevels$levels();

		if (levels == null) {
			return;
		}

		CompoundTag tag = new CompoundTag();
		tag.putIntArray("Y", levels.ys);
		tag.putByteArray("L", levels.data);
		nbt.put(NBT_KEY, tag);
	}

	/** Reads levels written by {@link #write}; null if absent or malformed. Malformed levels are logged and dropped. */
	public static ChunkLevels read(CompoundTag nbt) {
		String key = savedKey(nbt);

		if (key == null) {
			return null;
		}

		CompoundTag tag = nbt.getCompound(key);
		ChunkLevels levels = ChunkLevels.of(tag.getIntArray("Y").clone(), tag.getByteArray("L").clone());

		if (levels == null) {
			LOGGER.warn("Dropping malformed water levels from a saved chunk");
		}

		return levels;
	}

	/** The key a chunk's levels are saved under, or null if it has none. */
	private static String savedKey(CompoundTag nbt) {
		if (nbt.contains(NBT_KEY, Tag.TAG_COMPOUND)) {
			return NBT_KEY;
		}

		if (nbt.contains(LEGACY_NBT_KEY, Tag.TAG_COMPOUND)) {
			return LEGACY_NBT_KEY;
		}

		return null;
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
}
