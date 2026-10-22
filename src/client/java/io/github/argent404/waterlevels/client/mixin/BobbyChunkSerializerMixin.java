package io.github.argent404.waterlevels.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.argent404.waterlevels.Levels;
import io.github.argent404.waterlevels.duck.WLChunk;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

/**
 * Keeps levels in Bobby's client-side chunk cache (Bobby 5.0.x), which uses its own serializer.
 * Skipped when Bobby is not installed.
 */
@Pseudo
@Mixin(targets = "de.johni0702.minecraft.bobby.ChunkSerializer", remap = false)
public abstract class BobbyChunkSerializerMixin {

	/** Unloading: copy the levels to Bobby's fake chunk. */
	@ModifyReturnValue(method = "shallowCopy", at = @At("RETURN"))
	private static Pair<LevelChunk, Supplier<LevelChunk>> waterlevels$copy(Pair<LevelChunk, Supplier<LevelChunk>> copy,
																			@Local(argsOnly = true) LevelChunk original) {
		((WLChunk) copy.getLeft()).waterlevels$setLevels(((WLChunk) original).waterlevels$levels());
		return copy;
	}

	@ModifyReturnValue(method = "serialize", at = @At("RETURN"))
	private static CompoundTag waterlevels$save(CompoundTag nbt, @Local(argsOnly = true) LevelChunk chunk) {
		Levels.write(chunk, nbt);
		return nbt;
	}

	/** Bobby creates the chunk inside the returned supplier, so levels are attached there. */
	@ModifyReturnValue(method = "deserialize", at = @At("RETURN"))
	private static Supplier<LevelChunk> waterlevels$load(Supplier<LevelChunk> load, @Local(argsOnly = true) CompoundTag nbt) {
		if (load == null) return null;
		return () -> {
			LevelChunk chunk = load.get();
			((WLChunk) chunk).waterlevels$setLevels(Levels.read(nbt));
			return chunk;
		};
	}
}
