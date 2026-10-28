package com.trevortracker.client;

import java.util.List;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Draws the walking path, the straight tracer line and a box around the target,
 * using Minecraft's built-in gizmo line drawing. Called once per frame.
 * Colors and thickness come from {@link TrackerConfig}.
 */
public final class GuideRenderer {
	private static final int AREA_COLOR = 0xFFB066FF;       // purple

	private GuideRenderer() {
	}

	public static void render(HuntTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		TrackerConfig config = TrevorTrackerClient.CONFIG;
		if (!config.enabled || !tracker.isActive() || mc.player == null) return;

		float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 goal = tracker.getGoalPos(partialTick);
		if (goal == null) return;

		if (config.mode != HuntTracker.Mode.LINE) drawPath(tracker, config);
		if (config.mode != HuntTracker.Mode.PATH) drawTracer(mc, goal, config);
		drawGoalMarker(tracker, config, partialTick);
	}

	private static void drawPath(HuntTracker tracker, TrackerConfig config) {
		List<BlockPos> path = tracker.getPath();
		if (path.size() < 2) return;

		Vec3 playerPos = Minecraft.getInstance().player.position();
		int start = closestIndex(path, playerPos);
		int color = config.pathColor();

		for (int i = start; i < path.size() - 1; i++) {
			Vec3 a = nodeCenter(path.get(i));
			Vec3 b = nodeCenter(path.get(i + 1));
			if ((i - start) % 4 == 3) {
				Gizmos.arrow(a, b, color, config.pathWidth); // an arrow every few blocks shows which way to go
			} else {
				Gizmos.line(a, b, color, config.pathWidth);
			}
		}
	}

	private static void drawTracer(Minecraft mc, Vec3 goal, TrackerConfig config) {
		Camera camera = mc.gameRenderer.getMainCamera();
		Vector3fc forward = camera.forwardVector();
		// Start slightly in front of the camera, otherwise the line is just a dot.
		Vec3 start = camera.position().add(forward.x(), forward.y(), forward.z());
		Gizmos.line(start, goal, config.tracerColor(), config.tracerWidth);
	}

	private static void drawGoalMarker(HuntTracker tracker, TrackerConfig config, float partialTick) {
		Entity target = tracker.getTarget();
		if (target != null) {
			// The glow outline (see the mixins) is what shows through walls; this box is depth-tested.
			if (!config.box) return;
			Vec3 offset = target.getPosition(partialTick).subtract(target.position());
			AABB box = target.getBoundingBox().move(offset).inflate(0.1);
			Gizmos.cuboid(box, GizmoStyle.stroke(config.outlineColor(), config.outlineWidth));
		} else if (tracker.getArea() != null) {
			// A tall column marks the area Trevor named, visible from far away.
			BlockPos p = tracker.getArea().pos;
			AABB column = new AABB(p.getX(), p.getY() - 5, p.getZ(), p.getX() + 1, p.getY() + 40, p.getZ() + 1);
			Gizmos.cuboid(column, GizmoStyle.strokeAndFill(AREA_COLOR, 2.0f, 0x40B066FF)).setAlwaysOnTop();
		}
	}

	private static Vec3 nodeCenter(BlockPos p) {
		return new Vec3(p.getX() + 0.5, p.getY() + 0.15, p.getZ() + 0.5);
	}

	/** Only draw the part of the path still ahead of the player. */
	private static int closestIndex(List<BlockPos> path, Vec3 pos) {
		int best = 0;
		double bestDist = Double.MAX_VALUE;
		for (int i = 0; i < path.size(); i++) {
			double d = nodeCenter(path.get(i)).distanceToSqr(pos);
			if (d < bestDist) {
				bestDist = d;
				best = i;
			}
		}
		return Math.min(best, path.size() - 2);
	}
}
