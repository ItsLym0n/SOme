# Trevor Tracker

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
| `R` | Call Trevor / accept the quest (one step per press, see below) |
| `J` | Turn the tracker on/off |
| `K` | Switch guide: **PATH** → **LINE** (straight tracer) → **BOTH** |
| `/trevortracker help` | List all commands |
| `/trevortracker mode path\|line\|both` | Set the guide mode |
| `/trevortracker area Oasis` | Set the area by hand if you missed Trevor's message |
| `/trevortracker renew [on\|off]` | Auto renew the quest |
| `/trevortracker accept [on\|off]` | Auto accept Trevor's [YES] prompt |
| `/trevortracker cookie [on\|off]` | Whether you have a Booster Cookie |
| `/trevortracker automatic [on\|off]` | Fully automatic mode (macro, ban risk) |
| `/trevortracker early <0-10>` | Seconds before the cooldown ends to call (default 3) |
| `/trevortracker callcommand call trevor` | The command used to call with a Booster Cookie |
| `/trevortracker call` | Same as pressing `R` |
| `/trevortracker clear` | Forget the current hunt |

You can rebind the keys with the **Keybinds...** button in the settings, or in
Options → Controls → Key Binds → *Trevor Tracker*.

## Renewing and accepting quests

Turn on **Auto Renew** and **Auto Accept** in the settings (or `/trevortracker renew on`,
`/trevortracker accept on`).

- Trevor's cooldown is 20 seconds from when he gives you the quest. Once the hunt is over
  (the animal died or time ran out), the mod calls him **3 seconds before** the cooldown
  ends. Change this with the **Call Early** slider.
- **With a Booster Cookie:** the call is the command in the settings (`/call trevor` by default).
- **Without a Booster Cookie:** the mod right-clicks the Abiphone in your hotbar, clicks
  Trevor's contact, then clicks **Call**. Keep the Abiphone in your hotbar.
- **Auto Accept** clicks the **[YES]** on Trevor's "Click an option" prompt.

The HUD shows what's next, e.g. *Calling Trevor in 2s* or *Press R to call Trevor*.

**Mode: Key Press (default).** Nothing happens by itself. Each press of `R` does one
step: call, pick Trevor, press Call, accept. This works inside the Abiphone menu too.

**Mode: Fully Auto.** The mod does every step on its own. ⚠️ This is a macro under
Hypixel's rules and can get your account banned. It stops after 2 failed attempts.

## Settings

Press `O` to open the settings. They're saved to `config/trevortracker.json`.

| Column | Sliders |
|---|---|
| **Walking Path** | Red, Green, Blue, Opacity, Thickness |
| **Tracer Line** | Red, Green, Blue, Opacity, Thickness |
| **Mob Outline** | Red, Green, Blue, Box Opacity, Box Thickness (the color is used for both the glow and the box) |

Buttons below the sliders: **Guide** (path/line/both), **Glow (thru walls)** on/off,
**Box** on/off. Then the quest settings: **Auto Renew**, **Booster Cookie** yes/no,
**Mode** (Key Press / Fully Auto), **Auto Accept**, **Call Early** and the call command.
At the bottom: **Enabled**, **Keybinds...** and **Done**.

The glowing outline is the part that shows through walls. The path and box are hidden
behind blocks like normal.

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
- `dist/trevor-tracker-1.2.0+26.1.2.jar` (the ready-built jar in this repo)

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
3. The mod appears in `build/libs/trevor-tracker-1.2.0.jar`.

To work on it in an editor, open this folder in **IntelliJ IDEA** (Community is free).
It picks up the Gradle project automatically.

## How the code is organised

| File | Job |
|---|---|
| `TrevorTrackerClient.java` | Startup: keybinds, chat listener, commands, hooks |
| `TrackerConfig.java` | Saved settings (colors, thickness, toggles) |
| `TrackerConfigScreen.java` | The settings screen |
| `mixin/MinecraftMixin.java`, `mixin/EntityMixin.java` | Make the hunted animal glow in your color |
| `QuestRenewer.java` | Calls Trevor through the Abiphone and accepts the quest |
| `HuntTracker.java` | Reads chat, finds the animal, runs the pathfinder |
| `PathFinder.java` | A* search for a walkable route, spread over several ticks |
| `GuideRenderer.java` | Draws the path, tracer line and boxes |
| `TrackerHud.java` | The text in the top-left corner |
| `HuntArea.java` | The six Mushroom Desert areas and their coordinates |
