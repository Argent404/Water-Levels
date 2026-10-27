package io.github.argent404.waterlevels.mixin;

import io.github.argent404.waterlevels.LevelData;
import io.github.argent404.waterlevels.duck.WLChunk;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Sends a chunk's levels right before the chunk itself, so the client has them when it
 * first builds the chunk's mesh.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
	@Inject(method = "playerLoadedChunk", at = @At("HEAD"))
	private void waterlevels$sendLevels(ServerPlayer player, MutableObject<ClientboundLevelChunkWithLightPacket> packet,
			LevelChunk chunk, CallbackInfo ci) {
		if (((WLChunk) chunk).waterlevels$levels() != null) {
			LevelData.send(player, chunk);
		}
	}
}
