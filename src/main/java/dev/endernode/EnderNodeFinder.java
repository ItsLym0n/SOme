package dev.endernode;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class EnderNodeFinder implements ClientModInitializer {
	public static final String MOD_ID = "endernodefinder";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Rescan for nodes every this many ticks (20 ticks = 1 second). */
	private static final int SCAN_INTERVAL = 5;
	/** How far above the floor the path line is drawn. */
	private static final double PATH_HEIGHT = 0.1;

	public static NodeConfig config;

	private static List<BlockPos> nodes = List.of();
	private static @Nullable BlockPos target;
	private static List<BlockPos> path = List.of();
	private static int scanCooldown;
	private static boolean openSettingsNextTick;

	private static KeyMapping settingsKey;
	private static KeyMapping toggleKey;

	@Override
	public void onInitializeClient() {
		config = NodeConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.endernodefinder.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, category));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.endernodefinder.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				ClientCommands.literal("endernode").executes(ctx -> {
					// The chat screen is still open while the command runs, so open the GUI next tick
					openSettingsNextTick = true;
					return 1;
				})));

		ClientTickEvents.END_CLIENT_TICK.register(EnderNodeFinder::onTick);
		LevelRenderEvents.END_EXTRACTION.register(EnderNodeFinder::onRender);
	}

	private static void onTick(Minecraft client) {
		while (settingsKey.consumeClick()) {
			openSettingsNextTick = true;
		}
		while (toggleKey.consumeClick()) {
			config.enabled = !config.enabled;
			config.save();
			if (client.player != null) {
				client.player.sendOverlayMessage(Component.literal("Ender Node Finder: " + (config.enabled ? "§aON" : "§cOFF")));
			}
		}
		if (openSettingsNextTick) {
			openSettingsNextTick = false;
			client.setScreen(new NodeConfigScreen(client.screen));
		}

		if (!config.enabled || client.level == null || client.player == null) {
			nodes = List.of();
			target = null;
			path = List.of();
			return;
		}
		if (--scanCooldown <= 0) {
			scanCooldown = SCAN_INTERVAL;
			BlockPos feet = client.player.blockPosition();
			nodes = NodeScanner.scan(client.level, feet, config.range);
			target = NodeScanner.pickTarget(nodes, feet, config.sameLevelRange);
			path = target == null ? List.of() : PathFinder.find(client.level, feet, target);
		}
	}

	private static void onRender(LevelExtractionContext context) {
		if (!config.enabled || nodes.isEmpty()) {
			return;
		}
		try {
			drawNodes(context);
		} catch (IllegalStateException e) {
			// Frame rendered outside the normal game loop (no gizmo collector); skip it
		}
	}

	private static void drawNodes(LevelExtractionContext context) {
		// Box around every node in range
		GizmoStyle boxStyle = config.fillAlpha > 0
				? GizmoStyle.strokeAndFill(config.boxColor(), config.boxWidth, config.fillColor())
				: GizmoStyle.stroke(config.boxColor(), config.boxWidth);
		for (BlockPos node : nodes) {
			show(Gizmos.cuboid(new AABB(node).inflate(0.002), boxStyle));
		}

		BlockPos goal = target;
		List<BlockPos> walk = path;
		Entity player = context.camera().entity();
		if (goal == null || player == null) {
			return;
		}

		// Walking path along the floor: your feet -> path blocks -> the node
		Vec3 feet = player.getPosition(context.deltaTracker().getGameTimeDeltaPartialTick(false));
		List<Vec3> points = new ArrayList<>();
		points.add(feet.add(0, PATH_HEIGHT, 0));
		for (int i = 1; i < walk.size(); i++) {
			BlockPos step = walk.get(i);
			points.add(new Vec3(step.getX() + 0.5, step.getY() + PATH_HEIGHT, step.getZ() + 0.5));
		}
		points.add(Vec3.atCenterOf(goal));

		for (int i = 0; i < points.size() - 1; i++) {
			show(Gizmos.line(points.get(i), points.get(i + 1), config.lineColor(), config.lineWidth));
		}
	}

	private static void show(GizmoProperties gizmo) {
		if (config.throughWalls) {
			gizmo.setAlwaysOnTop();
		}
	}
}
