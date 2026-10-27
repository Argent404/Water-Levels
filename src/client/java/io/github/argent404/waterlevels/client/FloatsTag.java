package io.github.argent404.waterlevels.client;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;

/** The {@code #waterlevels:floats} block tag. Block states cache membership until tags reload. */
public final class FloatsTag {
	private FloatsTag() {
	}

	public static final TagKey<Block> TAG = TagKey.create(Registries.BLOCK, new ResourceLocation("waterlevels", "floats"));

	private static volatile int generation;

	public static int generation() {
		return generation;
	}

	static void register() {
		CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> generation++);
	}
}
