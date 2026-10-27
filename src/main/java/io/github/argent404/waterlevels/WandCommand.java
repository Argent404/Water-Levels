package io.github.argent404.waterlevels;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * {@code /ww}: puts a wand in the player's hand and removes any other wands from their inventory.
 * The held item moves to a free slot; if there is none, nothing changes. Requires {@code waterlevels.wand}.
 */
final class WandCommand {
	private static final Component INVENTORY_FULL = Component.translatable("message.waterlevels.inventory_full");

	private WandCommand() {
	}

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
				Commands.literal("ww")
						.requires(source -> source.isPlayer() && Perms.check(source, Perms.WAND))
						.executes(context -> giveToHand(context.getSource().getPlayerOrException()) ? 1 : 0)));
	}

	private static boolean giveToHand(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		int hand = inventory.selected;
		ItemStack held = inventory.items.get(hand);
		boolean holdingWand = held.is(WaterLevels.WATER_WAND);

		// The held item needs a free slot unless it is a wand or empty. Another wand's slot can be reused.
		int free = -1;

		if (!holdingWand && !held.isEmpty()) {
			for (int slot = 0; slot < inventory.items.size() && free < 0; slot++) {
				if (slot != hand && inventory.items.get(slot).is(WaterLevels.WATER_WAND)) {
					free = slot;
				}
			}

			if (free < 0) {
				free = inventory.getFreeSlot();
			}

			if (free < 0) {
				player.displayClientMessage(INVENTORY_FULL, true);
				return false;
			}
		}

		for (int slot = 0; slot < inventory.items.size(); slot++) {
			if (slot != hand && inventory.items.get(slot).is(WaterLevels.WATER_WAND)) {
				inventory.items.set(slot, ItemStack.EMPTY);
			}
		}

		if (inventory.offhand.get(0).is(WaterLevels.WATER_WAND)) {
			inventory.offhand.set(0, ItemStack.EMPTY);
		}

		if (free >= 0) {
			inventory.items.set(free, held);
		}

		inventory.items.set(hand, new ItemStack(WaterLevels.WATER_WAND));
		player.playNotifySound(WaterLevels.DRIP, SoundSource.PLAYERS, 1f, 1f);
		return true;
	}
}
