# Citrus Client

A Fabric mod for **Minecraft 26.1.2** on Hypixel SkyBlock. It combines two features in
one mod with a SkyHanni-style settings menu:

- **Hunting:** Trevor Tracker guides you to the animal Trevor asks you to hunt, and can
  call Trevor, accept his quest and warp for you.
- **End:** Ender Node Finder highlights ender nodes and draws a walking path to them.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.1.2.
2. Put these two jars in your `.minecraft/mods` folder:
   - `dist/citrus-client-1.0.0+26.1.2.jar` (this mod)
   - [Fabric API](https://modrinth.com/mod/fabric-api) for 26.1.2
3. **Remove the old `trevor-tracker-…` and `ender-node-finder-…` jars** if you have them.
   Citrus Client replaces both, and your settings carry over.
4. Launch the game with the Fabric profile.

## The menu

Open it with **Right Shift**, `/cc` or `/citrusclient`.

- **Sections** on the left: **Hunting**, **End** and **General** (keybinds).
- **Features** on the right. Each row has an **ON/OFF** switch, and the **▶** arrow drops
  down its options (colors, thickness, rainbow, timings...).
- **Search** at the top finds features and options in every section.

Shortcuts straight to a section: `/trevortracker` or `O` for Hunting, `/endernode` or `N`
for End.

Settings are saved to `config/trevortracker.json` and `config/endernodefinder.json`.

## Keys and commands

| Key / command | What it does |
|---|---|
| `Right Shift`, `/cc`, `/citrusclient` | Open the Citrus Client menu |
| `O`, `/trevortracker` | Open the Hunting section |
| `N`, `/endernode` | Open the End section |
| `J` | Trevor Tracker on/off |
| `K` | Trevor guide: PATH → LINE → BOTH |
| *(unbound)* | Ender Node Finder on/off |
| `/trevortracker help` | List the Trevor commands |

Change keys in the menu under **General → Keybinds**, or in Options → Controls → Key Binds →
*Citrus Client*.

---

## Hunting (Trevor Tracker)

- **Reads Trevor's message** ("You can find your TRACKABLE animal near the Desert
  Settlement.") and puts a purple column on that area with a walking path to it.
- **When the animal loads**, it finds it by its nametag, makes it **glow through walls**
  and leads the path straight to it.
- **Walking path** goes around walls, steps up blocks, drops down ledges, swims and avoids
  cactus and lava, with an arrow every few blocks.
- **HUD** in the top-left shows the target, distance, direction and height difference.

| Feature | Options |
|---|---|
| **Trevor Tracker** | Guide (path / line / both), Lines thru walls, Rainbow Speed |
| **Walking Path** | Color + opacity, Thickness, Rainbow |
| **Tracer Line** | Color + opacity, Thickness, Rainbow |
| **Mob Glow** | Outline color (also used for the box), Rainbow |
| **Mob Box** | Opacity, Thickness |
| **Auto Call Trevor** | Call Early (seconds), Command |
| **Auto Accept** | – |
| **Auto Warp** | – |

**Auto Call Trevor** (Booster Cookie): after the animal **dies**, sends `/call Trevor`
3 seconds before Trevor's 20 second cooldown ends, or right away if it's already over. It
doesn't call while the animal is alive or after a timeout, and stops as soon as Trevor answers.

**Auto Accept** clicks **[YES]** on Trevor's prompt half a second after it shows up.

**Auto Warp** uses your travel scrolls when a quest starts, based on where your last
quest was:

| Last quest → new quest | Result |
|---|---|
| Oasis / Desert Settlement / Desert Mountain → one of those | no warp |
| Mushroom Gorge / Glowing / Overgrown caves → one of those | no warp |
| Desert Mountain ↔ Mushroom Gorge | no warp |
| desert area → cave area | `/warp glowing` |
| cave area → desert area | `/warp desert` |
| first quest after logging in | no warp |

Other Trevor commands: `/trevortracker mode <path|line|both>`, `area <name>`,
`autocall`, `autoaccept`, `autowarp`, `early <0-10>`, `callcommand <command>`, `clear`.

---

## End (Ender Node Finder)

| Feature | Options |
|---|---|
| **Ender Node Finder** | Scan Range, Through Walls |
| **Node Box** | Color + opacity, Fill, Thickness |
| **Route Line** | Color + opacity, Thickness |
| **Node Groups** | Group Radius (0 = off), Group Size, Same Level ± |

How the path works:

1. **Picking a node:** it computes a real walking path to the closest few nodes and picks
   the shortest walk. Nodes more than *Same Level ±* blocks above or below you cost extra.
2. **Groups first:** if *Group Size* or more nodes are within *Group Radius* of each
   other, it goes to the nearest group first and stays there until it's cleared.
3. **No sudden switching:** a new node can't take over the path for 2 seconds, and the
   current target gets a 4-block head start.
4. **Walking path** on solid blocks, drawn as steps so it doesn't cut through blocks.
5. **Crosshair guide:** when you can't see the node, a line from your crosshair shows
   which way to go.

---

> ⚠️ **Use at your own risk.** Hypixel's rules don't allow mods that give an unfair
> advantage. Seeing mobs or nodes through walls can count as ESP, and Auto Call, Auto
> Accept and Auto Warp send commands by themselves, which can count as macros. All of
> these can be turned off in the menu.

## Building it yourself

Install **JDK 25**, then run `./gradlew build` (Windows: `gradlew.bat build`). The mod
appears in `build/libs/citrus-client-1.0.0.jar`.

## How the code is organised

| Folder | What's in it |
|---|---|
| `com/citrusclient` | Startup, menu key and commands |
| `com/citrusclient/menu` | The SkyHanni-style menu (`CitrusScreen`) and its sections (`MenuSections`) |
| `com/trevortracker/client` | Hunting: Trevor Tracker, auto call/accept/warp, HUD, glow mixins |
| `dev/endernode` | End: Ender Node Finder scanner, pathfinder and rendering |
