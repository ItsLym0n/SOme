package dev.endernode;

import com.citrusclient.CitrusClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ender Node Finder: the End section of Citrus Client. */
public class EnderNodeFinder {
	public static final String MOD_ID = "endernodefinder";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Rescan for nodes every this many ticks (20 ticks = 1 second). */
	private static final int SCAN_INTERVAL = 5;
	/** How far above the floor the path line is drawn. */
	private static final double PATH_HEIGHT = 0.1;
	/** A newly spawned node must exist this long (ticks) before it can take over the path. */
	private static final int NEW_NODE_DELAY = 40;
	/** Head start (in blocks of walking) the current target gets, so the path doesn't flip-flop. */
	private static final double KEEP_TARGET_BONUS = 4.0;
	/** The crosshair guide line points this many path steps ahead. */
	private static final int GUIDE_LOOKAHEAD = 6;

	public static NodeConfig config;

	private static List<BlockPos> nodes = List.of();
	private static @Nullable BlockPos target;
	private static List<BlockPos> path = List.of();
	/** While clearing a group, the last group node targeted; the path stays near it. */
	private static @Nullable BlockPos groupAnchor;
	/** Game tick when each node was first seen. */
	private static final Map<BlockPos, Long> firstSeen = new HashMap<>();
	private static long tickCount;
	private static int scanCooldown;

	private static KeyMapping settingsKey;
	private static KeyMapping toggleKey;

	public static void init(KeyMapping.Category category) {
		config = NodeConfig.load();

		settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.endernodefinder.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, category));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.endernodefinder.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				ClientCommands.literal("endernode").executes(ctx -> {
					CitrusClient.openMenu(CitrusClient.END);
					return 1;
				})));

		ClientTickEvents.END_CLIENT_TICK.register(EnderNodeFinder::onTick);
		LevelRenderEvents.END_EXTRACTION.register(EnderNodeFinder::onRender);
	}

	private static void onTick(Minecraft client) {
		while (settingsKey.consumeClick()) {
			CitrusClient.openMenu(CitrusClient.END);
		}
		while (toggleKey.consumeClick()) {
			config.enabled = !config.enabled;
			config.save();
			if (client.player != null) {
				client.player.sendOverlayMessage(Component.literal("Ender Node Finder: " + (config.enabled ? "§aON" : "§cOFF")));
			}
		}
		if (!config.enabled || client.level == null || client.player == null) {
			nodes = List.of();
			target = null;
			path = List.of();
			firstSeen.clear();
			groupAnchor = null;
			return;
		}
		tickCount++;
		if (--scanCooldown <= 0) {
			scanCooldown = SCAN_INTERVAL;
			BlockPos feet = client.player.blockPosition();
			nodes = NodeScanner.scan(client.level, feet, config.range);
			firstSeen.keySet().retainAll(nodes);
			for (BlockPos node : nodes) {
				firstSeen.putIfAbsent(node, tickCount);
			}

			// While you have a target, brand-new nodes can't steal the path straight away
			BlockPos current = target != null && nodes.contains(target) ? target : null;
			List<BlockPos> choices = current == null ? nodes : nodes.stream()
					.filter(node -> node.equals(current) || tickCount - firstSeen.get(node) >= NEW_NODE_DELAY)
					.toList();
			if (config.groupRadius > 0) {
				choices = preferGroups(choices);
			}
			PathFinder.Route route = PathFinder.findBest(client.level, feet, choices, config.sameLevelRange,
					current, KEEP_TARGET_BONUS);
			target = route == null ? null : route.target();
			path = route == null ? List.of() : route.path();
			if (config.groupRadius <= 0 || target == null) {
				groupAnchor = null;
			} else if (groupAnchor != null
					|| NodeScanner.inGroups(nodes, config.groupRadius, config.groupSize).contains(target)) {
				// Entered (or still clearing) a group: stay near the latest target
				groupAnchor = target;
			}
		}
	}

	/**
	 * Narrows the choices for group mode: while clearing a group, only nodes near the last
	 * one; otherwise only nodes that are part of a group, if there are any.
	 */
	private static List<BlockPos> preferGroups(List<BlockPos> choices) {
		if (groupAnchor != null) {
			List<BlockPos> sameGroup = NodeScanner.near(choices, groupAnchor, config.groupRadius);
			if (!sameGroup.isEmpty()) {
				return sameGroup;
			}
			groupAnchor = null; // group cleared
		}
		// Count group members among all nodes, but only offer ones you're allowed to switch to
		List<BlockPos> grouped = NodeScanner.inGroups(nodes, config.groupRadius, config.groupSize);
		List<BlockPos> groupedChoices = choices.stream().filter(grouped::contains).toList();
		return groupedChoices.isEmpty() ? choices : groupedChoices;
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
		double startY = walk.isEmpty() ? feet.y : walk.get(0).getY();
		List<Vec3> points = new ArrayList<>();
		points.add(new Vec3(feet.x, startY + PATH_HEIGHT, feet.z));
		for (int i = 1; i < walk.size(); i++) {
			BlockPos step = walk.get(i);
			addStep(points, new Vec3(step.getX() + 0.5, step.getY() + PATH_HEIGHT, step.getZ() + 0.5));
		}

		// Finish on the node's surface, but only if nothing solid is in the way
		Vec3 last = points.get(points.size() - 1);
		AABB box = new AABB(goal);
		Vec3 onNode = new Vec3(
				Mth.clamp(last.x, box.minX, box.maxX),
				Mth.clamp(last.y, box.minY, box.maxY),
				Mth.clamp(last.z, box.minZ, box.maxZ));
		BlockHitResult hit = context.level().clip(new ClipContext(last, onNode, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(goal)) {
			points.add(onNode);
		}

		for (int i = 0; i < points.size() - 1; i++) {
			show(Gizmos.line(points.get(i), points.get(i + 1), config.lineColor(), config.lineWidth));
		}

		// Can't see the node? Also draw a line from the crosshair to a point a few steps
		// along the path, drawn on top of everything so you always know which way to go
		Camera camera = context.camera();
		Vec3 eye = camera.position();
		BlockHitResult sight = context.level().clip(new ClipContext(eye, Vec3.atCenterOf(goal),
				ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		boolean nodeVisible = sight.getType() == HitResult.Type.MISS || sight.getBlockPos().equals(goal);
		if (!nodeVisible) {
			Vec3 ahead = walk.size() > 1
					? Vec3.atBottomCenterOf(walk.get(Math.min(GUIDE_LOOKAHEAD, walk.size() - 1))).add(0, PATH_HEIGHT, 0)
					: Vec3.atCenterOf(goal);
			Vector3fc forward = camera.forwardVector();
			Vec3 crosshair = eye.add(forward.x(), forward.y(), forward.z());
			Gizmos.line(crosshair, ahead, config.lineColor(), config.lineWidth).setAlwaysOnTop();
		}
	}

	/**
	 * Adds the next path point. A change in height is drawn as a step (across to the
	 * block edge, straight up or down, then on) so the line doesn't cut through the block.
	 */
	private static void addStep(List<Vec3> points, Vec3 next) {
		Vec3 prev = points.get(points.size() - 1);
		if (Math.abs(next.y - prev.y) > 0.01) {
			double edgeX = (prev.x + next.x) / 2;
			double edgeZ = (prev.z + next.z) / 2;
			points.add(new Vec3(edgeX, prev.y, edgeZ));
			points.add(new Vec3(edgeX, next.y, edgeZ));
		}
		points.add(next);
	}

	private static void show(GizmoProperties gizmo) {
		if (config.throughWalls) {
			gizmo.setAlwaysOnTop();
		}
	}
}
