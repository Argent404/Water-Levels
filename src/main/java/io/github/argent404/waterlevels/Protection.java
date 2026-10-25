package io.github.argent404.waterlevels;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Per-block build checks for wand edits. Every block the brush changes is checked, not only the
 * one clicked, so an edit can't reach past a protected border.
 *
 * <p>Vanilla's check covers spawn protection and the world border. ArdaCraft's PlotSquared port
 * doesn't hook it, so when that mod is installed each block also goes through the same check
 * PlotSquared uses for Axiom edits ({@code BlockEventListener.blockCreateAxiom}): plot membership,
 * roads, unowned plots, height limits and the admin bypass permissions.
 */
public final class Protection {
	private Protection() {
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(WaterLevels.MOD_ID);
	private static final boolean PLOTS = FabricLoader.getInstance().isModLoaded("plotsquared-fabric");
	private static Field platform, listener;
	private static Method check;
	private static boolean broken;

	/** Whether {@code player} may change the block at {@code pos}. */
	public static boolean mayEdit(ServerPlayer player, BlockPos pos) {
		ServerLevel world = player.serverLevel();

		if (!world.mayInteract(player, pos)) {
			return false;
		}

		return !PLOTS || plotsAllow(player, world, pos);
	}

	/** Fails closed: if the PlotSquared check can't be reached, nothing is edited while it is installed. */
	private static boolean plotsAllow(ServerPlayer player, ServerLevel world, BlockPos pos) {
		if (broken) {
			return false;
		}

		try {
			if (check == null) {
				Class<?> platformClass = Class.forName("com.plotsquared.fabric.FabricPlatform");
				platform = platformClass.getField("PLATFORM");
				listener = platformClass.getField("blockEventListener");

				for (Method m : listener.getType().getMethods()) {
					if (m.getName().equals("blockCreateAxiom") && m.getParameterCount() == 5) {
						check = m;
					}
				}

				if (check == null) {
					throw new NoSuchMethodException("blockCreateAxiom");
				}
			}

			Object instance = listener.get(platform.get(null));
			return check.invoke(instance, player, world, pos, world.getBlockState(pos), null) != InteractionResult.FAIL;
		} catch (ReflectiveOperationException | RuntimeException e) {
			broken = true;
			LOGGER.error("Water Levels: can't reach PlotSquared's build check; wand edits are disabled while PlotSquared is installed", e);
			return false;
		}
	}
}
