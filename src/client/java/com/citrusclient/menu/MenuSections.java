package com.citrusclient.menu;

import java.util.List;

import com.citrusclient.CitrusClient;
import com.citrusclient.menu.MenuModel.Action;
import com.citrusclient.menu.MenuModel.Channel;
import com.citrusclient.menu.MenuModel.Color;
import com.citrusclient.menu.MenuModel.Cycle;
import com.citrusclient.menu.MenuModel.Feature;
import com.citrusclient.menu.MenuModel.Section;
import com.citrusclient.menu.MenuModel.Slider;
import com.citrusclient.menu.MenuModel.Text;
import com.citrusclient.menu.MenuModel.Toggle;
import com.trevortracker.client.TrackerConfig;
import com.trevortracker.client.TrevorTrackerClient;
import dev.endernode.EnderNodeFinder;
import dev.endernode.NodeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

/** Builds the menu's sections from the Trevor Tracker and Ender Node Finder settings. */
final class MenuSections {
	private MenuSections() {
	}

	static List<Section> build() {
		return List.of(hunting(), end(), general());
	}

	private static Section hunting() {
		TrackerConfig c = TrevorTrackerClient.CONFIG;
		return new Section(CitrusClient.HUNTING, List.of(
				new Feature("Trevor Tracker",
						"Guides you to the animal Trevor asks you to hunt.",
						new Toggle("Enabled", () -> c.enabled, v -> c.enabled = v),
						List.of(
								new Cycle("Guide", () -> c.mode.name(), () -> c.mode = c.mode.next()),
								new Toggle("Lines thru walls", "Draw the path, tracer and box on top of blocks",
										() -> c.linesThroughWalls, v -> c.linesThroughWalls = v),
								new Slider("Rainbow Speed", 1, 10, () -> c.rainbowSpeed, v -> c.rainbowSpeed = v))),
				new Feature("Walking Path",
						"Trail on the ground to the animal (Guide: PATH or BOTH).",
						null,
						List.of(
								new Color("Color",
										new Channel(() -> c.pathRed, v -> c.pathRed = v),
										new Channel(() -> c.pathGreen, v -> c.pathGreen = v),
										new Channel(() -> c.pathBlue, v -> c.pathBlue = v),
										new Channel(() -> c.pathAlpha, v -> c.pathAlpha = v),
										c::pathColor),
								new Slider("Thickness", 1, 20, () -> Math.round(c.pathWidth), v -> c.pathWidth = v),
								new Toggle("Rainbow", () -> c.pathRainbow, v -> c.pathRainbow = v))),
				new Feature("Tracer Line",
						"Straight line from your screen to the animal (Guide: LINE or BOTH).",
						null,
						List.of(
								new Color("Color",
										new Channel(() -> c.tracerRed, v -> c.tracerRed = v),
										new Channel(() -> c.tracerGreen, v -> c.tracerGreen = v),
										new Channel(() -> c.tracerBlue, v -> c.tracerBlue = v),
										new Channel(() -> c.tracerAlpha, v -> c.tracerAlpha = v),
										c::tracerColor),
								new Slider("Thickness", 1, 20, () -> Math.round(c.tracerWidth), v -> c.tracerWidth = v),
								new Toggle("Rainbow", () -> c.tracerRainbow, v -> c.tracerRainbow = v))),
				new Feature("Mob Glow",
						"Glowing outline on the animal, visible through walls.",
						new Toggle("Glow", () -> c.glow, v -> c.glow = v),
						List.of(
								new Color("Outline Color (glow + box)",
										new Channel(() -> c.outlineRed, v -> c.outlineRed = v),
										new Channel(() -> c.outlineGreen, v -> c.outlineGreen = v),
										new Channel(() -> c.outlineBlue, v -> c.outlineBlue = v),
										null,
										c::outlineColor),
								new Toggle("Rainbow", () -> c.outlineRainbow, v -> c.outlineRainbow = v))),
				new Feature("Mob Box",
						"Box drawn around the animal.",
						new Toggle("Box", () -> c.box, v -> c.box = v),
						List.of(
								new Slider("Opacity", 0, 255, () -> c.outlineAlpha, v -> c.outlineAlpha = v),
								new Slider("Thickness", 1, 10, () -> Math.round(c.outlineWidth), v -> c.outlineWidth = v))),
				new Feature("Auto Call Trevor",
						"After the animal dies, call Trevor just before his cooldown ends (Booster Cookie).",
						new Toggle("Auto Call", () -> c.autoCall, v -> c.autoCall = v),
						List.of(
								new Slider("Call Early (s)", 0, 10, () -> c.callEarlySeconds, v -> c.callEarlySeconds = v),
								new Text("Command", "Command used to call Trevor", () -> c.callCommand,
										v -> c.callCommand = v.trim()))),
				new Feature("Auto Accept",
						"Click [YES] on Trevor's quest prompt.",
						new Toggle("Auto Accept", () -> c.autoAccept, v -> c.autoAccept = v),
						List.of()),
				new Feature("Auto Warp",
						"Use /warp glowing or /warp desert when the new animal is on the other side.",
						new Toggle("Auto Warp", () -> c.autoWarp, v -> c.autoWarp = v),
						List.of())));
	}

	private static Section end() {
		NodeConfig c = EnderNodeFinder.config;
		return new Section(CitrusClient.END, List.of(
				new Feature("Ender Node Finder",
						"Highlights ender nodes and draws a walking path to the closest one.",
						new Toggle("Enabled", () -> c.enabled, v -> c.enabled = v),
						List.of(
								new Slider("Scan Range", 8, 96, () -> c.range, v -> c.range = v),
								new Toggle("Through Walls", () -> c.throughWalls, v -> c.throughWalls = v))),
				new Feature("Node Box",
						"Box drawn around every ender node in range.",
						null,
						List.of(
								new Color("Color",
										new Channel(() -> c.boxRed, v -> c.boxRed = v),
										new Channel(() -> c.boxGreen, v -> c.boxGreen = v),
										new Channel(() -> c.boxBlue, v -> c.boxBlue = v),
										new Channel(() -> c.boxAlpha, v -> c.boxAlpha = v),
										position -> c.boxColor()),
								new Slider("Fill", 0, 255, () -> c.fillAlpha, v -> c.fillAlpha = v),
								new Slider("Thickness", 1, 10, () -> Math.round(c.boxWidth), v -> c.boxWidth = v))),
				new Feature("Route Line",
						"Walking path to the node you're going to.",
						null,
						List.of(
								new Color("Color",
										new Channel(() -> c.lineRed, v -> c.lineRed = v),
										new Channel(() -> c.lineGreen, v -> c.lineGreen = v),
										new Channel(() -> c.lineBlue, v -> c.lineBlue = v),
										new Channel(() -> c.lineAlpha, v -> c.lineAlpha = v),
										position -> c.lineColor()),
								new Slider("Thickness", 1, 20, () -> Math.round(c.lineWidth), v -> c.lineWidth = v))),
				new Feature("Node Groups",
						"Visit clusters of nodes first, and prefer nodes on your level.",
						null,
						List.of(
								new Slider("Group Radius", 0, 16, () -> c.groupRadius, v -> c.groupRadius = v, "OFF"),
								new Slider("Group Size", 2, 8, () -> c.groupSize, v -> c.groupSize = v),
								new Slider("Same Level ±", 0, 10, () -> c.sameLevelRange, v -> c.sameLevelRange = v)))));
	}

	private static Section general() {
		return new Section("General", List.of(
				new Feature("Keybinds",
						"Change the keys for the menu, Trevor Tracker and Ender Node Finder.",
						null,
						List.of(new Action("Open Keybinds...", () -> {
							Minecraft mc = Minecraft.getInstance();
							mc.setScreen(new KeyBindsScreen(mc.screen, mc.options));
						})))));
	}
}
