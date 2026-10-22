package io.github.argent404.waterlevels;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

/**
 * {@code /waterlevels fill <level> <radius>}: sets the level of the top still water in every
 * column within the radius. Requires {@code waterlevels.fill}.
 */
final class FillCommand {
	private FillCommand() {}

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
				Commands.literal("waterlevels").requires(source -> Perms.check(source, Perms.FILL))
						.then(Commands.literal("fill")
								.then(Commands.argument("level", IntegerArgumentType.integer(0, 8))
										.then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
												.executes(context -> {
													CommandSourceStack source = context.getSource();
													int[] result = Levels.fill(source.getLevel(), BlockPos.containing(source.getPosition()),
															IntegerArgumentType.getInteger(context, "level"),
															IntegerArgumentType.getInteger(context, "radius"));
													source.sendSuccess(() -> Component.literal("Changed " + result[0] + " water columns"
															+ (result[1] > 0 ? ", skipped " + result[1] + " columns in unloaded chunks" : "")), true);
													return result[0];
												}))))));
	}
}
