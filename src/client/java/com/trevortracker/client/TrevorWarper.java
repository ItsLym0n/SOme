package com.trevortracker.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Warps toward the hunted animal with a travel scroll when Trevor gives a quest:
 * {@code /warp glowing} for the cave and gorge areas, {@code /warp desert} for the rest
 * (see {@link HuntArea#warp}).
 *
 * It doesn't warp if you're already in an area of the same group (e.g. in the Oasis
 * with the animal near the Desert Settlement), or between Desert Mountain and Mushroom
 * Gorge, which are right next to each other. Your current area is read from the
 * location line in Hypixel's sidebar.
 */
public class TrevorWarper {
	/** Wait a moment after Trevor's message so the warp doesn't land on top of other commands. */
	private static final long WARP_DELAY_MS = 1_000;
	/** If the sidebar can't be read, count you as in the nearest area when within this many blocks of it. */
	private static final double FALLBACK_AREA_DISTANCE = 80;
	private static final String MOB_LOCATION_LABEL = "Tracker Mob Location";

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

		HuntArea target = pendingArea;
		pendingArea = null;

		HuntArea current = currentArea(mc);
		// Same warp group: you're already on that side, so walk instead.
		if (current != null && current.warp.equals(target.warp)) return;
		if (isMountainGorge(current, target)) return;

		mc.player.connection.sendCommand(target.warp);
	}

	/** Desert Mountain and Mushroom Gorge are next to each other, so never warp between them. */
	private static boolean isMountainGorge(HuntArea a, HuntArea b) {
		return (a == HuntArea.DESERT_MOUNTAIN && b == HuntArea.MUSHROOM_GORGE)
				|| (a == HuntArea.MUSHROOM_GORGE && b == HuntArea.DESERT_MOUNTAIN);
	}

	/** The Mushroom Desert area you're standing in, or null if you're somewhere else (e.g. Trapper's Den). */
	private static HuntArea currentArea(Minecraft mc) {
		List<String> lines = sidebarLines(mc);
		if (!lines.isEmpty()) {
			HuntArea found = null;
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);
				// "Tracker Mob Location:" is followed by the animal's area, not yours.
				if (line.contains(MOB_LOCATION_LABEL)) continue;
				if (i > 0 && lines.get(i - 1).contains(MOB_LOCATION_LABEL)) continue;
				HuntArea area = HuntArea.fromName(line);
				if (area == null) continue;
				if (line.contains("⏣")) return area; // Hypixel's location marker
				if (found == null) found = area;
			}
			return found;
		}

		// No sidebar: guess from your position.
		Vec3 pos = mc.player.position();
		HuntArea nearest = null;
		double nearestDist = FALLBACK_AREA_DISTANCE;
		for (HuntArea area : HuntArea.values()) {
			double d = pos.distanceTo(Vec3.atCenterOf(area.pos));
			if (d < nearestDist) {
				nearestDist = d;
				nearest = area;
			}
		}
		return nearest;
	}

	/** Sidebar lines from top to bottom, without formatting codes. */
	private static List<String> sidebarLines(Minecraft mc) {
		List<String> lines = new ArrayList<>();
		if (mc.level == null) return lines;
		Scoreboard scoreboard = mc.level.getScoreboard();
		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) return lines;

		List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(sidebar));
		entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());
		for (PlayerScoreEntry entry : entries) {
			if (entry.isHidden()) continue;
			PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
			String text = PlayerTeam.formatNameForTeam(team, entry.ownerName()).getString();
			lines.add(ChatFormatting.stripFormatting(text).trim());
		}
		return lines;
	}

	/** HUD line, or null when no warp is coming up. */
	public String getHint() {
		if (pendingArea == null || !TrevorTrackerClient.CONFIG.autoWarp) return null;
		return "Warping: /" + pendingArea.warp;
	}
}
