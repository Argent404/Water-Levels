package io.github.argent404.waterlevels;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

public class WaterLevels implements ModInitializer {
	public static final String MOD_ID = "waterlevels";

	/** Server to client: a chunk's levels. */
	public static final ResourceLocation LEVELS_PACKET = new ResourceLocation(MOD_ID, "levels");
	/** The water level wand. */
	public static final Item WATER_WAND = new Item(new Item.Properties().stacksTo(1));

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(MOD_ID, "water_wand"), WATER_WAND);
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(WATER_WAND));

		FillCommand.register();
	}
}
