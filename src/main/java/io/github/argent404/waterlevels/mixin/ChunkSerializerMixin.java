package io.github.argent404.waterlevels.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.argent404.waterlevels.Levels;
import io.github.argent404.waterlevels.duck.WLChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

/** Saves and loads levels with the chunk. Vanilla ignores the extra key. */
@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerMixin {
	@ModifyReturnValue(method = "write", at = @At("RETURN"))
	private static CompoundTag waterlevels$save(CompoundTag nbt, @Local(argsOnly = true) ChunkAccess chunk) {
		Levels.write(owner(chunk), nbt);
		return nbt;
	}

	@ModifyReturnValue(method = "read", at = @At("RETURN"))
	private static ProtoChunk waterlevels$load(ProtoChunk chunk, @Local(argsOnly = true) CompoundTag nbt) {
		((WLChunk) owner(chunk)).waterlevels$setLevels(Levels.read(nbt));
		return chunk;
	}

	/** Full chunks are loaded wrapped in an ImposterProtoChunk; levels belong on the chunk inside. */
	@Unique
	private static ChunkAccess owner(ChunkAccess chunk) {
		return chunk instanceof ImposterProtoChunk wrapper ? wrapper.getWrapped() : chunk;
	}
}
