# Ender Node Finder (Fabric, Minecraft 26.1.2)

Highlights ender nodes (purple terracotta) and draws a walking path on the floor
to the closest one to walk to.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.1.2.
2. Put these two jars in your `.minecraft/mods` folder:
   - `dist/ender-node-finder-1.3.0+26.1.2.jar` (this mod)
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
| Same Level ± | Nodes more than this many blocks above/below you cost a bit extra when picking a target (default 2) |
| Line Thickness | Line width (1-20, default 6) |
| Scan Range | How far around you to look for nodes (blocks) |
| Group Radius | Nodes this close to each other count as a group, and groups are visited first (0 = off, default 6) |
| Group Size | How many nodes it takes to make a group (default 3) |
| Through Walls | Draw boxes and line on top of everything |
| Enabled | Turn the whole mod on or off |

Settings are saved to `.minecraft/config/endernodefinder.json`.

## How the path works

1. **Picking a node:** it computes a real walking path to the closest few nodes
   and picks the shortest walk. Nodes more than *Same Level ±* blocks above or
   below you cost 2 extra blocks per block of height, so a node on your level wins
   a close race, but a node just down the stairs beats one far away on your level.
2. **Groups first:** if *Group Size* or more nodes are within *Group Radius*
   blocks of each other, the path goes to the nearest group first, even if a
   single node is closer. Once you're in a group it only moves to nodes within
   *Group Radius* of the last one, so it doesn't pull you away until the group is
   cleared. Then it picks the next group, or the nearest node if there are none.
3. **No sudden switching:** a newly spawned node can't take over the path for
   2 seconds, and the current target gets a 4-block head start, so the path only
   switches when another node is clearly closer.
4. **Walking path:** it walks on solid blocks, steps up 1 block, drops down up
   to 3, goes around walls, avoids lava and doesn't cut corners. Height changes
   are drawn as steps so the line doesn't cut through blocks. The line is drawn on
   the floor from your feet and ends on the node's surface.
5. **Crosshair guide:** when you can't see the target node, an extra line goes
   from your crosshair to a point a few steps along the path, drawn on top of
   everything so you always know which way to go.
6. It recalculates 4 times a second and moves on to the next node once you mine one.

## Warning

Hypixel's rules don't allow ESP-style highlighting through walls, and using it can
get you banned. Turn **Through Walls** off to hide boxes and path behind blocks.
The crosshair guide line is always drawn on top, so it counts as a through-walls
hint too. Use at your own risk.

## Building from source

Needs JDK 25: `./gradlew build` → `build/libs/`.
