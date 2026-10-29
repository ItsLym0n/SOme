package com.trevortracker.client;

import java.util.regex.Matcher;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Warps toward the hunted animal with a travel scroll when Trevor gives a quest:
 * {@code /warp glowing} for the cave and gorge areas, {@code /warp desert} for the rest
 * (see {@link HuntArea#warp}). Skipped if you're already close to the area.
 */
public class TrevorWarper {
	/** Wait a moment after Trevor's message so the warp doesn't land on top of other commands. */
	private static final long WARP_DELAY_MS = 1_000;
	/** Already this close to the area (in blocks): walking is quicker than warping. */
	private static final double SKIP_DISTANCE = 60;

	private HuntArea pendingArea;
	private long warpAtMs;

	public void onChat(Component message) {
		String text = ChatFormatting.stripFormatting(message.getString()).trim();
		Matcher m = HuntTracker.QUEST.matcher(text);
		if (!m.find()) return;

		HuntArea area = HuntArea.fromName(m.group(2));
		if (area == null) return;
		pendingArea = area;
		warpAtMs = System.currentTimeMillis() + WARP_DELAY_MS;
	}

	public void tick(Minecraft mc) {
		TrackerConfig config = TrevorTrackerClient.CONFIG;
		if (pendingArea == null || mc.player == null) return;
		if (!config.enabled || !config.autoWarp) {
			pendingArea = null;
			return;
		}
		if (System.currentTimeMillis() < warpAtMs) return;

		HuntArea area = pendingArea;
		pendingArea = null;
		if (mc.player.position().distanceTo(Vec3.atCenterOf(area.pos)) < SKIP_DISTANCE) return;

		mc.player.connection.sendCommand(area.warp);
	}

	/** HUD line, or null when no warp is coming up. */
	public String getHint() {
		if (pendingArea == null || !TrevorTrackerClient.CONFIG.autoWarp) return null;
		return "Warping: /" + pendingArea.warp;
	}
}
