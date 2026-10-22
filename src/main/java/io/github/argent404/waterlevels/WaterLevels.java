package io.github.argent404.waterlevels;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.WeakHashMap;

public class WaterLevels implements ModInitializer {
	public static final String MOD_ID = "waterlevels";

	/** Server to client: a chunk's levels. */
	public static final ResourceLocation LEVELS_PACKET = new ResourceLocation(MOD_ID, "levels");
	/** Client to server: wand right-click (position, level, radius). */
	public static final ResourceLocation APPLY_PACKET = new ResourceLocation(MOD_ID, "apply");
	/** Client to server: wand left-click (position, radius). */
	public static final ResourceLocation REMOVE_PACKET = new ResourceLocation(MOD_ID, "remove");
	public static final int MAX_RADIUS = 5;

	/** Feedback sound for wand edits, pitched by level. */
	public static final SoundEvent DRIP = SoundEvent.createVariableRangeEvent(new ResourceLocation("minecraft", "block.pointed_dripstone.drip_water"));

	public static float dripPitch(int level) {
		return 0.7f + level * 0.08f;
	}

	/** The wand. Its level and radius are stored on the client, not on the item. */
	public static final Item WATER_WAND = new Item(new Item.Properties().stacksTo(1));

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(MOD_ID, "water_wand"), WATER_WAND);
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(WATER_WAND));

		ServerPlayNetworking.registerGlobalReceiver(APPLY_PACKET, (server, player, handler, buf, sender) -> {
			BlockPos pos = buf.readBlockPos();
			int level = Mth.clamp(buf.readByte(), 0, 8);
			int radius = Mth.clamp(buf.readByte(), 1, MAX_RADIUS);
			server.execute(() -> {
				if (!mayEdit(server, player, pos)) return;
				if (Levels.apply(player.serverLevel(), pos, level, radius)) {
					player.playNotifySound(DRIP, SoundSource.BLOCKS, 1f, dripPitch(level));
				} else if (!player.serverLevel().getFluidState(pos).is(Fluids.WATER)) {
					player.displayClientMessage(Component.literal("This block can't hold water"), true);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(REMOVE_PACKET, (server, player, handler, buf, sender) -> {
			BlockPos pos = buf.readBlockPos();
			int radius = Mth.clamp(buf.readByte(), 1, MAX_RADIUS);
			server.execute(() -> {
				if (mayEdit(server, player, pos) && Levels.waterlog(player.serverLevel(), pos, false, radius) > 0) {
					player.playNotifySound(SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.6f, 1f);
				}
			});
		});

		FillCommand.register();
		WandCommand.register();

		// The client already handles wand clicks; stop the server from also using or breaking the block.
		UseBlockCallback.EVENT.register((player, world, hand, hit) ->
				!world.isClientSide && player.getItemInHand(hand).is(WATER_WAND) ? InteractionResult.SUCCESS : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> !player.getMainHandItem().is(WATER_WAND));
	}

	/**
	 * Checks a wand edit: rate limit (one per player every 2 ticks), permission, holding the wand,
	 * build rights, reach, and whether the player may edit at {@code pos}.
	 */
	private static boolean mayEdit(MinecraftServer server, ServerPlayer player, BlockPos pos) {
		Integer last = LAST_APPLY.put(player, server.getTickCount());
		if (last != null && server.getTickCount() - last < 2) return false;
		if (!Perms.check(player, Perms.WAND)) {
			player.displayClientMessage(Component.literal("You don't have permission to change water levels."), true);
			return false;
		}
		return holdsWand(player) && player.getAbilities().mayBuild && pos.closerToCenterThan(player.getEyePosition(), 8)
				&& player.serverLevel().mayInteract(player, pos);
	}

	/** Server tick of each player's last wand edit. */
	private static final Map<ServerPlayer, Integer> LAST_APPLY = new WeakHashMap<>();

	public static boolean holdsWand(Player player) {
		return player.getMainHandItem().is(WATER_WAND) || player.getOffhandItem().is(WATER_WAND);
	}
}
