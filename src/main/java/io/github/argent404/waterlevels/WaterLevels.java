package io.github.argent404.waterlevels;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;

public class WaterLevels implements ModInitializer {
	public static final String MOD_ID = "waterlevels";

	/** Server to client: a chunk's levels. */
	public static final ResourceLocation LEVELS_PACKET = new ResourceLocation(MOD_ID, "levels");

	@Override
	public void onInitialize() {
		FillCommand.register();
	}
}
