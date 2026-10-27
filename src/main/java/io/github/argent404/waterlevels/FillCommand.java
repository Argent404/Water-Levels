package io.github.argent404.waterlevels;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * {@code /waterlevels fill <level> <radius>}: sets the level of the top still water in every
 * column within the radius. Requires {@code waterlevels.fill}.
 */
final class FillCommand {
	private FillCommand() {
	}

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
				Commands.literal("waterlevels")
						.requires(source -> Perms.check(source, Perms.FILL))
						.then(Commands.literal("fill")
								.then(Commands.argument("level", IntegerArgumentType.integer(0, 8))
										.then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
												.executes(FillCommand::fill))))));
	}

	private static int fill(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		int level = IntegerArgumentType.getInteger(context, "level");
		int radius = IntegerArgumentType.getInteger(context, "radius");
		int[] result = LevelEdits.fill(source.getLevel(), BlockPos.containing(source.getPosition()), level, radius);
		source.sendSuccess(() -> message(result[0], result[1]), true);
		return result[0];
	}

	private static Component message(int changed, int unloaded) {
		if (unloaded > 0) {
			return Component.translatable("commands.waterlevels.fill.skipped", changed, unloaded);
		}

		return Component.translatable("commands.waterlevels.fill", changed);
	}
}
