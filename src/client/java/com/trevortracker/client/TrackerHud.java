package com.trevortracker.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Top-left text showing what you're hunting, how far away it is and which way to turn. */
public final class TrackerHud {
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
	private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	private TrackerHud() {
	}

	public static void render(HuntTracker tracker, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (!TrevorTrackerClient.CONFIG.enabled || mc.player == null || mc.options.hideGui) return;
		if (!tracker.isActive()) return;

		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
		Vec3 goal = tracker.getGoalPos(partialTick);
		if (goal == null) return;

		Vec3 player = mc.player.getPosition(partialTick);
		double dx = goal.x - player.x;
		double dy = goal.y - player.y;
		double dz = goal.z - player.z;
		int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));

		// Minecraft yaw: 0 = south (+Z), increasing clockwise.
		float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float relative = Mth.wrapDegrees(targetYaw - mc.player.getYRot(partialTick));
		String arrow = ARROWS[Math.floorMod(Math.round(relative / 45f), 8)];
		double compassAngle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north
		String compass = COMPASS[Math.floorMod((int) Math.round(compassAngle / 45.0), 8)];

		String what;
		int color;
		if (tracker.getTarget() != null) {
			what = tracker.getTargetName();
			color = 0xFF55FF55;
		} else {
			String rarity = tracker.getRarity() != null ? tracker.getRarity().charAt(0)
					+ tracker.getRarity().substring(1).toLowerCase() + " animal" : "Animal";
			what = rarity + " near " + tracker.getArea().displayName;
			color = 0xFFFFAA00;
		}

		String height = "";
		if (Math.abs(dy) >= 3) height = String.format("  (%d %s)", (int) Math.abs(dy), dy > 0 ? "up" : "down");

		Font font = mc.font;
		int x = 4;
		int y = 4;
		graphics.text(font, "Trevor Tracker [" + TrevorTrackerClient.CONFIG.mode + "]", x, y, 0xFFAAAAAA, true);
		graphics.text(font, what, x, y + 11, color, true);
		String status = arrow + " " + distance + "m " + compass + height;
		if (tracker.getTarget() == null) status += "  - get closer to load the mob";
		graphics.text(font, status, x, y + 22, 0xFFFFFFFF, true);
	}
}
