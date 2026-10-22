package io.github.argent404.waterlevels.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * Wand key bindings (Options > Controls > Water Levels). While unbound, the modifiers fall back
 * to the physical Left Ctrl and Left Shift keys, so they don't conflict with sprint and sneak.
 */
final class WandKeys {
	private WandKeys() {}

	private static final String CATEGORY = "key.categories.waterlevels";

	static final KeyMapping RADIUS_MODIFIER = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.waterlevels.radius_modifier", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
	static final KeyMapping LEVEL_MODIFIER = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.waterlevels.level_modifier", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
	static final KeyMapping TOGGLE_OUTLINES = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.waterlevels.toggle_outlines", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));

	/** Loads the class so the bindings above are registered. */
	static void register() {}

	static boolean radiusModifier(Minecraft client) {
		return isUnbound(RADIUS_MODIFIER) ? physical(client, GLFW.GLFW_KEY_LEFT_CONTROL) : held(client, RADIUS_MODIFIER);
	}

	static boolean levelModifier(Minecraft client) {
		return isUnbound(LEVEL_MODIFIER) ? physical(client, GLFW.GLFW_KEY_LEFT_SHIFT) : held(client, LEVEL_MODIFIER);
	}

	/** Key names for help text. */
	static String radiusName() {
		return isUnbound(RADIUS_MODIFIER) ? "Left Ctrl" : RADIUS_MODIFIER.getTranslatedKeyMessage().getString();
	}

	static String levelName() {
		return isUnbound(LEVEL_MODIFIER) ? "Left Shift" : LEVEL_MODIFIER.getTranslatedKeyMessage().getString();
	}

	private static boolean physical(Minecraft client, int glfwKey) {
		return InputConstants.isKeyDown(client.getWindow().getWindow(), glfwKey);
	}

	private static boolean isUnbound(KeyMapping binding) {
		return KeyBindingHelper.getBoundKeyOf(binding).equals(InputConstants.UNKNOWN);
	}

	/** Whether the key or mouse button bound to {@code binding} is held; false if unbound. */
	private static boolean held(Minecraft client, KeyMapping binding) {
		InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(binding);
		if (key.equals(InputConstants.UNKNOWN)) return false;
		long window = client.getWindow().getWindow();
		return key.getType() == InputConstants.Type.MOUSE
				? GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS
				: InputConstants.isKeyDown(window, key.getValue());
	}
}
