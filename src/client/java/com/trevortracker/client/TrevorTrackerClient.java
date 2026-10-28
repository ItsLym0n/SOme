package com.trevortracker.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class TrevorTrackerClient implements ClientModInitializer {
	public static final String MOD_ID = "trevortracker";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final HuntTracker TRACKER = new HuntTracker();
	public static TrackerConfig CONFIG;

	private static KeyMapping toggleKey;
	private static KeyMapping modeKey;
	private static KeyMapping settingsKey;
	private static boolean openSettingsNextTick;

	@Override
	public void onInitializeClient() {
		CONFIG = TrackerConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.trevortracker.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, category));
		modeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.trevortracker.mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
		settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.trevortracker.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, category));

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) TRACKER.onChat(message);
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (toggleKey.consumeClick()) {
				CONFIG.enabled = !CONFIG.enabled;
				CONFIG.save();
				say(mc, "Tracker " + (CONFIG.enabled ? "enabled" : "disabled"));
			}
			while (modeKey.consumeClick()) {
				CONFIG.mode = CONFIG.mode.next();
				CONFIG.save();
				say(mc, "Guide mode: " + CONFIG.mode);
			}
			while (settingsKey.consumeClick()) {
				openSettingsNextTick = true;
			}
			if (openSettingsNextTick) {
				// Delayed a tick so the chat screen closing after /trevortracker doesn't close it again.
				openSettingsNextTick = false;
				mc.setScreen(new TrackerConfigScreen(mc.screen));
			}
			TRACKER.tick(mc);
		});

		LevelRenderEvents.BEFORE_GIZMOS.register(context -> GuideRenderer.render(TRACKER));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
				(graphics, deltaTracker) -> TrackerHud.render(TRACKER, graphics, deltaTracker));

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
				dispatcher.register(buildCommand()));
	}

	/** True for the hunted animal while the glow setting is on; used by the glow mixins. */
	public static boolean isGlowTarget(Entity entity) {
		return CONFIG != null && CONFIG.enabled && CONFIG.glow && entity == TRACKER.getTarget();
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> buildCommand() {
		return literal("trevortracker")
				.executes(ctx -> {
					openSettingsNextTick = true;
					return 1;
				})
				.then(literal("help").executes(ctx -> {
					ctx.getSource().sendFeedback(Component.literal(
							"§6Trevor Tracker§r - enabled: " + CONFIG.enabled + ", mode: " + CONFIG.mode + "\n"
									+ "§7/trevortracker  (opens settings: colors, thickness, glow, keybinds)\n"
									+ "/trevortracker mode <path|line|both>\n"
									+ "/trevortracker area <name>  (if you missed Trevor's message)\n"
									+ "/trevortracker clear"));
					return 1;
				}))
				.then(literal("mode").then(argument("mode", StringArgumentType.word())
						.suggests((ctx, builder) -> {
							for (HuntTracker.Mode m : HuntTracker.Mode.values()) builder.suggest(m.name().toLowerCase());
							return builder.buildFuture();
						})
						.executes(ctx -> {
							String value = StringArgumentType.getString(ctx, "mode").toUpperCase();
							try {
								CONFIG.mode = HuntTracker.Mode.valueOf(value);
								CONFIG.save();
								ctx.getSource().sendFeedback(Component.literal("Guide mode: " + CONFIG.mode));
							} catch (IllegalArgumentException e) {
								ctx.getSource().sendError(Component.literal("Use path, line or both"));
							}
							return 1;
						})))
				.then(literal("area").then(argument("name", StringArgumentType.greedyString())
						.suggests((ctx, builder) -> {
							for (HuntArea a : HuntArea.values()) builder.suggest(a.displayName);
							return builder.buildFuture();
						})
						.executes(ctx -> {
							HuntArea area = HuntArea.fromName(StringArgumentType.getString(ctx, "name"));
							if (area == null) {
								ctx.getSource().sendError(Component.literal("Unknown area"));
								return 0;
							}
							TRACKER.startHunt(null, area);
							ctx.getSource().sendFeedback(Component.literal("Tracking area: " + area.displayName));
							return 1;
						})))
				.then(literal("settings").executes(ctx -> {
					openSettingsNextTick = true;
					return 1;
				}))
				.then(literal("clear").executes(ctx -> {
					TRACKER.clear();
					ctx.getSource().sendFeedback(Component.literal("Hunt cleared"));
					return 1;
				}));
	}

	private static void say(Minecraft mc, String text) {
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal("[Trevor Tracker] ").withStyle(ChatFormatting.GOLD)
					.append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
		}
	}
}
