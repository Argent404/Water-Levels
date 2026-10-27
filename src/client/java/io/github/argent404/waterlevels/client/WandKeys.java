package io.github.argent404.waterlevels.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/**
 * Wand key bindings (Options > Controls > Water Levels). While unbound, the modifiers fall back
 * to the physical Left Ctrl and Left Shift keys, so they don't conflict with sprint and sneak.
 */
final class WandKeys {
	private WandKeys() {
	}

	private static final String CATEGORY = "key.categories.waterlevels";
	private static final Component LEFT_CTRL = Component.translatable("key.keyboard.left.control");
	private static final Component LEFT_SHIFT = Component.translatable("key.keyboard.left.shift");

	static final KeyMapping RADIUS_MODIFIER = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.waterlevels.radius_modifier", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
	static final KeyMapping LEVEL_MODIFIER = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.waterlevels.level_modifier", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
	static final KeyMapping TOGGLE_OUTLINES = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.waterlevels.toggle_outlines", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));

	/** Loads the class so the bindings above are registered. */
	static void register() {
	}

	static boolean isRadiusModifierHeld(Minecraft client) {
		return isUnbound(RADIUS_MODIFIER) ? isKeyDown(client, GLFW.GLFW_KEY_LEFT_CONTROL) : isHeld(client, RADIUS_MODIFIER);
	}

	static boolean isLevelModifierHeld(Minecraft client) {
		return isUnbound(LEVEL_MODIFIER) ? isKeyDown(client, GLFW.GLFW_KEY_LEFT_SHIFT) : isHeld(client, LEVEL_MODIFIER);
	}

	/** Key names for help text. */
	static Component radiusName() {
		return isUnbound(RADIUS_MODIFIER) ? LEFT_CTRL : RADIUS_MODIFIER.getTranslatedKeyMessage();
	}

	static Component levelName() {
		return isUnbound(LEVEL_MODIFIER) ? LEFT_SHIFT : LEVEL_MODIFIER.getTranslatedKeyMessage();
	}

	private static boolean isKeyDown(Minecraft client, int glfwKey) {
		return InputConstants.isKeyDown(client.getWindow().getWindow(), glfwKey);
	}

	private static boolean isUnbound(KeyMapping binding) {
		return KeyBindingHelper.getBoundKeyOf(binding).equals(InputConstants.UNKNOWN);
	}

	/** Whether the key or mouse button bound to {@code binding} is held; false if unbound. */
	private static boolean isHeld(Minecraft client, KeyMapping binding) {
		InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(binding);

		if (key.equals(InputConstants.UNKNOWN)) {
			return false;
		}

		long window = client.getWindow().getWindow();

		if (key.getType() == InputConstants.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
		}

		return InputConstants.isKeyDown(window, key.getValue());
	}
}
