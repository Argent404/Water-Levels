package io.github.argent404.waterlevels;

import me.lucko.fabric.api.permissions.v0.Permissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Permission checks through fabric-permissions-api, so LuckPerms can grant them.
 * Without a permissions mod, only operators pass.
 */
public final class Perms {
	private Perms() {
	}

	public static final String WAND = "waterlevels.wand", FILL = "waterlevels.fill";
	private static final int OP_LEVEL = 2;

	public static boolean check(ServerPlayer player, String node) {
		return Permissions.check(player, node, OP_LEVEL);
	}

	public static boolean check(CommandSourceStack source, String node) {
		return Permissions.check(source, node, OP_LEVEL);
	}
}
