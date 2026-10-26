# Ender Node Finder (Fabric, Minecraft 26.1.2)

Highlights ender nodes (purple terracotta) and draws a walking path on the floor
to the nearest one on your level.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.1.2.
2. Put these two jars in your `.minecraft/mods` folder:
   - `dist/ender-node-finder-1.1.0+26.1.2.jar` (this mod)
   - [Fabric API](https://modrinth.com/mod/fabric-api) for 26.1.2
3. Launch the game with the Fabric profile.

## Use

- **N** opens the settings screen (rebind it in Options → Controls → Key Binds → Ender Node Finder).
- **/endernode** also opens the settings.
- There is also a **Toggle On/Off** key you can bind (unbound by default).

## Settings

| Setting | What it does |
|---|---|
| Box Red / Green / Blue | Color of the box around each node (0-255) |
| Box Opacity | How see-through the box outline is |
| Box Fill | Translucent fill inside the box (0 = no fill) |
| Box Thickness | Outline width |
| Line Red / Green / Blue / Opacity | Color of the path line |
| Same Level ± | How many blocks up/down from your feet still counts as "your level" (default 2) |
| Line Thickness | Line width |
| Scan Range | How far around you to look for nodes (blocks) |
| Through Walls | Draw boxes and line on top of everything |
| Enabled | Turn the whole mod on or off |

Settings are saved to `.minecraft/config/endernodefinder.json`.

## How the path works

1. **Picking a node:** it goes for the nearest node within *Same Level ±* blocks
   of your feet. Nodes above or below that are still boxed, but only used if
   there are none on your level.
2. **Walking path:** it finds a real route along the ground, like a GPS. It walks
   on solid blocks, steps up 1 block, drops down up to 3, goes around walls,
   avoids lava and doesn't cut corners. The line is drawn on the floor from your
   feet and stops once you're close enough to mine the node.
3. It recalculates 4 times a second, so it follows you and jumps to the next node
   as soon as you mine one. If there's no full path (e.g. the node is sealed off),
   it gets you as close as it can.

## Warning

Hypixel's rules don't allow ESP-style highlighting through walls, and using it can
get you banned. Turn **Through Walls** off to only see highlights for nodes in
your line of sight. Use at your own risk.

## Building from source

Needs JDK 25: `./gradlew build` → `build/libs/`.
