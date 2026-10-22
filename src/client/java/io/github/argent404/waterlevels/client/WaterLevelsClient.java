package io.github.argent404.waterlevels.client;

import io.github.argent404.waterlevels.WaterLevels;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;

public class WaterLevelsClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		WandKeys.register();
		Floats.register();
		LevelSync.register();

		// Wand clicks are read from the use and attack keys at the start of the tick, before Minecraft
		// (or mods like Axiom) handle them. Right-click applies, modifier + right-click cycles the level.
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (client.player == null || client.screen != null || !client.player.getMainHandItem().is(WaterLevels.WATER_WAND)) return;
			boolean used = false;
			while (client.options.keyUse.consumeClick()) used = true;
			if (used) use(client);
		});
		// Cancel the vanilla use/attack/break that holding a button would otherwise trigger.
		UseBlockCallback.EVENT.register((player, world, hand, hit) ->
				world.isClientSide && player.getItemInHand(hand).is(WaterLevels.WATER_WAND) ? InteractionResult.CONSUME : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, world, hand) -> world.isClientSide && player.getItemInHand(hand).is(WaterLevels.WATER_WAND)
				? InteractionResultHolder.consume(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand)));
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
				world.isClientSide && player.getMainHandItem().is(WaterLevels.WATER_WAND) ? InteractionResult.FAIL : InteractionResult.PASS);

		// Show the controls the first time the wand is held.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!helpShown && client.player != null && client.player.getMainHandItem().is(WaterLevels.WATER_WAND)) {
				helpShown = true;
				help(client);
			}
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
				dispatcher.register(ClientCommandManager.literal("wandhelp").executes(c -> {
					help(c.getSource().getClient());
					return 1;
				})));
	}

	private static boolean helpShown;

	static void help(Minecraft client) {
		if (client.player == null) return;
		client.player.displayClientMessage(Component.literal("Right-click: set level   "
				+ WandKeys.levelName() + " + right-click: level   " + WandKeys.radiusName() + " + scroll: radius"), true);
	}

	/** Wand settings, kept on the client so creative mode can't overwrite them. */
	private static int level = 4; // 1-8, 0 = clear
	private static int radius = 1;

	/** Called by {@code MouseHandlerMixin}. */
	public static void scrollRadius(Minecraft client, double amount) {
		radius = Mth.clamp(radius + (int) Math.signum(amount), 1, WaterLevels.MAX_RADIUS);
		show(client);
	}

	/** Called by {@code MouseHandlerMixin}. */
	public static boolean radiusModifierHeld(Minecraft client) {
		return WandKeys.radiusModifier(client);
	}

	private static void use(Minecraft client) {
		if (WandKeys.levelModifier(client)) {
			level = (level + 1) % 9;
			client.getSoundManager().play(SimpleSoundInstance.forUI(WaterLevels.DRIP, WaterLevels.dripPitch(level)));
			show(client);
			return;
		}
		BlockPos pos = target(client);
		if (pos == null) return;
		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		buf.writeByte(level);
		buf.writeByte(radius);
		ClientPlayNetworking.send(WaterLevels.APPLY_PACKET, buf);
		client.player.swing(InteractionHand.MAIN_HAND);
	}

	/** The targeted block, counting water surfaces; null if nothing is in reach. */
	private static BlockPos target(Minecraft client) {
		HitResult hit = client.player.pick(client.gameMode.getPickRange(), 1f, true);
		return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null;
	}

	private static void show(Minecraft client) {
		String height = level == 0 ? "vanilla" : level + "/8";
		client.player.displayClientMessage(Component.literal("Wand: " + height + ", radius " + radius), true);
	}

}
