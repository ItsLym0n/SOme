# ULTIMATE LYMON's TREVOR ESP SIMULATOR

A Fabric mod for **Minecraft 26.1.2** that guides you to the animal Trevor asks you
to hunt in the Mushroom Desert on Hypixel SkyBlock.

## What it does

- **Reads Trevor's message.** When he says *"You can find your TRACKABLE animal near
  the Desert Settlement."*, the mod remembers the rarity and the area.
- **Before the animal loads**, it puts a purple column on that area and draws a
  walking path toward it.
- **When the animal loads** (Minecraft only knows about mobs that are fairly close),
  it finds the mob by its nametag (`Trackable Cow`, `Elusive Horse`, ...), gives it a
  **glowing outline you can see through walls**, draws a box around it and switches
  the path to lead straight to it.
- **The walking path** goes around walls, steps up blocks, drops down ledges, swims
  through water and avoids cactus and lava. Every few blocks there's an arrow showing
  which way to walk.
- **The HUD** in the top-left shows the target, its distance, a compass direction, an
  arrow showing which way to turn, and how far up or down it is.
- **It clears itself** when the mob dies or the hunt runs out of time.

## Controls

| Key / command | What it does |
|---|---|
| `O` or `/trevortracker` | Open the settings screen |
| `J` | Turn the tracker on/off |
| `K` | Switch guide: **PATH** → **LINE** (straight tracer) → **BOTH** |
| `/trevortracker help` | List all commands |
| `/trevortracker mode path\|line\|both` | Set the guide mode |
| `/trevortracker area Oasis` | Set the area by hand if you missed Trevor's message |
| `/trevortracker autocall [on\|off]` | Call Trevor after a kill (see below) |
| `/trevortracker early <0-10>` | Seconds before the cooldown ends to call (default 3) |
| `/trevortracker callcommand call Trevor` | The command used to call Trevor |
| `/trevortracker clear` | Forget the current hunt |

You can rebind the keys with the **Keybinds...** button in the settings, or in
Options → Controls → Key Binds → *ULTIMATE LYMON's TREVOR ESP SIMULATOR*.

## Auto call Trevor (Booster Cookie)

Turn on **Auto Call Trevor** in the settings or with `/trevortracker autocall on`.

- While the hunted animal is **alive**, nothing happens.
- When it **dies**, the mod sends `/call Trevor` **3 seconds before** Trevor's 20 second
  cooldown ends (counted from when he gave you the quest). If the cooldown is already
  over, it calls right away.
- If the hunt **runs out of time**, it doesn't call.
- If Trevor doesn't give a new quest, it tries again every 5 seconds, 3 calls at most.
- The HUD shows *Calling Trevor in 2s* while it waits.

Change the timing with the **Call Early** slider or `/trevortracker early <seconds>`.

> ⚠️ The mod sends the command by itself, which Hypixel may treat as a macro.

## Settings

Press `O` or type `/trevortracker` to open the settings. They're saved to `config/trevortracker.json`.

| Column | Sliders |
|---|---|
| **Walking Path** | Red, Green, Blue, Opacity, Thickness |
| **Tracer Line** | Red, Green, Blue, Opacity, Thickness |
| **Mob Outline** | Red, Green, Blue, Box Opacity, Box Thickness (the color is used for both the glow and the box) |

Under each column there's a **Rainbow** toggle. It replaces that column's color with a
moving rainbow (opacity and thickness still apply). On the path the colors flow toward
the animal, and the glow and box cycle through every color. **Rainbow Speed** (1-10)
sets how fast it moves.

Buttons below that: **Guide** (path/line/both), **Glow (thru walls)** on/off,
**Box** on/off. At the bottom: **Enabled**, **Rainbow Speed**, **Keybinds...**, **Lines thru walls**,
then **Auto Call Trevor**, **Call Early (s)**, the call command box, and **Done**.

**Glow (thru walls)** outlines the animal itself through walls. **Lines thru walls**
(on by default) draws the path, tracer line and box on top of blocks too. Turn it off
to hide them behind blocks. The hunted animal is always drawn while it's loaded, even
past the distance where Minecraft normally stops drawing small mobs, so the glow shows
from farther away.

> ⚠️ **Use at your own risk.** Hypixel's rules forbid mods that give an unfair advantage,
> and seeing mobs through walls can count as "ESP". You can turn the glow off in the
> settings. Similar Trevor features exist in popular mods like SkyHanni, but any custom
> mod is your own responsibility.

---

## Installing (step by step)

### 1. Install Fabric for 26.1.2
1. Go to https://fabricmc.net/use/installer/ and download the installer.
2. Run it, choose **Minecraft version 26.1.2**, and click **Install**.
3. Open the Minecraft Launcher. There's now a profile called **fabric-loader-26.1.2**.
   Launch it once and close the game. This creates the `mods` folder.

### 2. Install Fabric API
1. Go to https://modrinth.com/mod/fabric-api/versions
2. Download a version for **26.1.2** (for example `0.155.3+26.1.2`).

### 3. Add the mods
Put **both** of these files into your `mods` folder:
- `fabric-api-....jar`
- `dist/trevor-tracker-1.3.1+26.1.2.jar` (the ready-built jar in this repo)

Where the `mods` folder is:
- **Windows:** press `Win + R`, type `%appdata%\.minecraft\mods` and press Enter
- **Mac:** `~/Library/Application Support/minecraft/mods`
- **Linux:** `~/.minecraft/mods`

### 4. Play
Launch the **fabric-loader-26.1.2** profile, join Hypixel, warp to the trapper
(`/warp trapper`), talk to Trevor, and follow the line.

---

## Building it yourself (optional)

You only need this if you change the code.

1. Install **JDK 25**, for example from https://adoptium.net/temurin/releases/?version=25
2. Open a terminal in this folder and run:
   - Windows: `gradlew.bat build`
   - Mac/Linux: `./gradlew build`
3. The mod appears in `build/libs/trevor-tracker-1.3.1.jar`.

To work on it in an editor, open this folder in **IntelliJ IDEA** (Community is free).
It picks up the Gradle project automatically.

## How the code is organised

| File | Job |
|---|---|
| `TrevorTrackerClient.java` | Startup: keybinds, chat listener, commands, hooks |
| `TrackerConfig.java` | Saved settings (colors, thickness, toggles) |
| `TrackerConfigScreen.java` | The settings screen |
| `mixin/MinecraftMixin.java`, `mixin/EntityMixin.java` | Make the hunted animal glow in your color |
| `TrevorCaller.java` | Calls Trevor after a kill, just before the cooldown ends |
| `HuntTracker.java` | Reads chat, finds the animal, runs the pathfinder |
| `PathFinder.java` | A* search for a walkable route, spread over several ticks |
| `GuideRenderer.java` | Draws the path, tracer line and boxes |
| `TrackerHud.java` | The text in the top-left corner |
| `HuntArea.java` | The six Mushroom Desert areas and their coordinates |
