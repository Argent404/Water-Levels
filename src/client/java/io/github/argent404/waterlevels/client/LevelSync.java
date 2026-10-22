package io.github.argent404.waterlevels.client;

import io.github.argent404.waterlevels.ChunkLevels;
import io.github.argent404.waterlevels.Levels;
import io.github.argent404.waterlevels.WaterLevels;
import io.github.argent404.waterlevels.duck.WLChunk;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

/**
 * Receives levels from the server. Levels arrive just before their chunk and are attached when
 * the chunk loads, so it is meshed with the right heights the first time. Edits to loaded chunks
 * are applied directly and the changed area is re-meshed.
 */
final class LevelSync {
	private LevelSync() {}

	/** Levels received for chunks that haven't loaded yet. */
	private static final Map<Long, ChunkLevels> waiting = new HashMap<>();
	private static final int MAX_WAITING = 8192;

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(WaterLevels.LEVELS_PACKET, (client, handler, buf, sender) -> {
			ChunkPos pos = buf.readChunkPos();
			ChunkLevels levels = Levels.receive(buf);
			client.execute(() -> receive(client, pos, levels));
		});

		// Runs before the chunk can be meshed.
		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			long key = chunk.getPos().toLong();
			if (waiting.containsKey(key)) ((WLChunk) chunk).waterlevels$setLevels(waiting.remove(key));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(waiting::clear));
	}

	private static void receive(Minecraft client, ChunkPos pos, ChunkLevels after) {
		if (client.level == null) return;
		LevelChunk chunk = client.level.getChunkSource().getChunkNow(pos.x, pos.z);
		if (chunk == null) {
			if (waiting.size() >= MAX_WAITING) waiting.clear();
			waiting.put(pos.toLong(), after);
			return;
		}
		WLChunk data = (WLChunk) chunk;
		ChunkLevels before = data.waterlevels$levels();
		data.waterlevels$setLevels(after);

		// Include a one-block margin: water corners are shared with neighbours, and Sodium adds no margin itself.
		for (int y : ChunkLevels.layersOf(before, after)) {
			int[] box = ChunkLevels.changedArea(before, after, y);
			if (box != null) {
				client.levelRenderer.setBlocksDirty(pos.getMinBlockX() + box[0] - 1, y, pos.getMinBlockZ() + box[1] - 1,
						pos.getMinBlockX() + box[2] + 1, y, pos.getMinBlockZ() + box[3] + 1);
			}
		}
	}
}
