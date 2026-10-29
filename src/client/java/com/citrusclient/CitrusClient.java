package com.citrusclient;

import com.citrusclient.menu.CitrusScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.trevortracker.client.TrevorTrackerClient;
import dev.endernode.EnderNodeFinder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Citrus Client: Trevor Tracker (Hunting) and Ender Node Finder (End) in one mod,
 * with one SkyHanni-style settings menu.
 */
public class CitrusClient implements ClientModInitializer {
	public static final String MOD_ID = "citrusclient";
	public static final String VERSION = FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?");
	public static final String DISPLAY_NAME = "Citrus Client [" + VERSION + "]";

	// Menu sections
	public static final String HUNTING = "Hunting";
	public static final String END = "End";

	private static KeyMapping menuKey;
	/** Section to open the menu at on the next tick, or null. */
	private static @Nullable String openNextTick;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.citrusclient.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));

		TrevorTrackerClient.init(category);
		EnderNodeFinder.init(category);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
			for (String name : new String[]{"cc", "citrusclient", "CitrusClient"}) {
				dispatcher.register(ClientCommands.literal(name).executes(ctx -> {
					openMenu(null);
					return 1;
				}));
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (menuKey.consumeClick()) {
				openMenu(null);
			}
			if (openNextTick != null) {
				String section = openNextTick;
				openNextTick = null;
				mc.setScreen(new CitrusScreen(mc.screen, section.isEmpty() ? null : section));
			}
		});
	}

	/**
	 * Opens the menu at {@code section} (null = the last one used). Delayed a tick so a chat
	 * screen closing after a command doesn't close the menu again.
	 */
	public static void openMenu(@Nullable String section) {
		openNextTick = section == null ? "" : section;
	}
}
