package io.github.argent404.waterlevels.client;

import net.fabricmc.api.ClientModInitializer;

public class WaterLevelsClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		LevelSync.register();
	}
}
