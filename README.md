# Client Interactions

**English** | [中文](#中文说明)

> **Reading guide**
> This English section is 3 209 words (about 16 500 characters without spaces) and takes roughly
> 13 minutes to read at a normal technical-reading pace. The Chinese section is 10 533 characters
> (without spaces) and takes roughly 23 minutes, so reading both end to end takes about 36 minutes.
> The full [`LICENSE`](LICENSE) is 1 212 characters (197 words, 24 lines) and takes under a minute.
>
> If you only want to use the mod, read **Quick start** and **The five modes**. Everything else is for
> troubleshooting, for understanding why the mod behaves the way it does, or for building from source.

A client-side Fabric mod that automates the two mouse actions of Minecraft - **attack** (left button)
and **use** (right button) - with five configurable modes, configurable keybinds and a config screen that
is also reachable from Mod Menu.

Nothing is sent to the server that a normal player could not do: every action goes through the very same
vanilla code path that a real mouse click runs.

---

## Table of contents

1. [Supported versions](#supported-versions)
2. [Quick start](#quick-start)
3. [The five modes](#the-five-modes)
4. [What the mod does *not* do](#what-the-mod-does-not-do)
5. [Behaviour while a GUI is open](#behaviour-while-a-gui-is-open)
6. [Configuration](#configuration)
7. [Keybinds](#keybinds)
8. [HUD](#hud)
9. [Mod Menu integration](#mod-menu-integration)
10. [Config file format](#config-file-format)
11. [Building from source](#building-from-source)
12. [How it works](#how-it-works)
13. [Testing](#testing)
14. [Troubleshooting](#troubleshooting)
15. [Credits](#credits)
16. [License](#license)

---

## Supported versions

| Minecraft | Fabric Loader | Java | Jar |
|-----------|---------------|------|-----|
| 1.19.4    | >= 0.14.21    | 17   | `clientinteractions-1.19.4-1.0.0.jar` |
| 1.20.1    | >= 0.16.10    | 17   | `clientinteractions-1.20.1-1.0.0.jar` |
| 1.21.1    | >= 0.15.11    | 21   | `clientinteractions-1.21.1-1.0.0.jar` |
| 1.21.10   | >= 0.17.0     | 21   | `clientinteractions-1.21.10-1.0.0.jar` |
| 1.21.11   | >= 0.17.3     | 21   | `clientinteractions-1.21.11-1.0.0.jar` |
| 26.3      | >= 0.19.3     | 25   | `clientinteractions-26.3-1.0.0.jar` |

Each Minecraft version has its own jar. Use exactly one jar that matches your game version; the jar
declares the matching version in `fabric.mod.json`, so Fabric will refuse to load a mismatched one.

**Required:** [Fabric Loader](https://fabricmc.net/use/installer/) and
[Fabric API](https://modrinth.com/mod/fabric-api).
**Optional:** [Mod Menu](https://modrinth.com/mod/modmenu) for the config button in the mod list.

---

## Quick start

1. Put the jar for your Minecraft version into `.minecraft/mods`.
2. Start the game and open **Options 鈫?Controls 鈫?Key Binds**, category **Client Interactions**.
3. The two important keys are **Toggle attack automation** (default `R`) and
   **Toggle use automation** (default `V`).
4. Press `R` once. The HUD shows `Attack: Hold (continuous)` in the top left corner, and the game keeps
   attacking (or keeps mining the block you are looking at) until you press `R` again.
5. Open the config screen with `]` (default) or through Mod Menu, and pick a different mode or change the
   numbers. Changes apply immediately, even while a channel is running.

Everything is off by default. Nothing happens until you press one of the toggle keys.

---

## The five modes

Every mode exists twice: once for the attack channel and once for the use channel. Both channels can run
at the same time, with different modes and different numbers.

All intervals and durations are counted in **game ticks**. One tick is 50 ms, i.e. 20 ticks per second,
so `20t` means one second.

### 1. Hold

Press the toggle key and the button stays pressed until you press it again.

* Attack: the vanilla client keeps calling `continueAttack`, so a block you are looking at is mined
  normally and an entity is hit with the current attack cooldown, exactly like holding the real left
  button.
* Use: the vanilla client keeps using the item - eating, drinking, drawing a bow, holding a shield,
  right-clicking a block repeatedly - exactly like holding the real right button.

This is the mode to use for continuous mining or for holding a bow drawn.

### 2. Fast click

Injects **Per tick** clicks on **every** tick. The default is 1 click per tick, i.e. 20 clicks per
second; the range is 1 to 100 clicks per tick.

Some details matter here:

* A click is one call to the vanilla attack / use routine, which is what a real button press runs. All
  vanilla rules still apply inside that call.
* The vanilla client itself only processes one round of input per tick, so the mod injects the extra
  clicks directly instead of faking key presses. That is why more than one click per tick is possible
  at all, and why the limit is the vanilla state (attack cooldown, "already using an item", "hands
  busy", ...), not the tick loop.
* Because of that vanilla state, the *effective* rate can be lower than the configured one: attacking
  a block or missing sets Minecraft's ten tick "missed swing" timer, which silently drops attacks until
  it expires, and using an item that takes time (eating, drawing a bow) blocks further use until it
  finishes. The mod never bypasses those rules, it only asks more often.

### 3. Periodic click

Injects **Per tick** clicks once every **Interval** ticks. Default: one click every 20 ticks (once per
second). The first click happens immediately when the channel is switched on, then after every full
interval.

This is the mode for "click once per second" style automation, such as repeatedly using a block or
attacking on a rhythm.

### 4. Periodic hold

Holds the button for **Hold** ticks, then releases it, repeating every **Interval** ticks.

Example: interval 20, hold 10 gives half a second pressed, half a second released, forever. The hold
length is limited to the interval so a hold can never run into the next cycle; if you set it equal to the
interval the button is simply held without ever being released.

This mode is useful when an action needs to be *held* rather than clicked - for example charging a bow
part way and releasing, or mining in bursts.

### 5. Periodic burst

Fires **Burst** clicks, one tick apart, then waits until the **Interval** has fully elapsed and repeats.

Example: interval 40, burst 5 = five clicks in the first five ticks (a quarter second), then 35 ticks of
silence, then again. If the burst is longer than the interval it is clamped to the interval, so it fires
on every tick of the cycle.

### Comparison

| Mode            | Clicks per tick | Period            | Button held |
|-----------------|-----------------|-------------------|-------------|
| Hold            | none            | -                 | always      |
| Fast click      | `Per tick`      | every tick        | never       |
| Periodic click  | `Per tick`      | every `Interval`  | never       |
| Periodic hold   | none            | every `Interval`  | `Hold` ticks |
| Periodic burst  | 1               | every `Interval`  | never       |

---

## What the mod does *not* do

These limits are deliberate. They keep the mod on the vanilla side of what a player can physically do.

* **No reach or range changes.** Attacks and uses happen at the normal distance.
* **No target switching inside one tick.** The mod never changes what you are looking at. A click always
  goes to whatever the vanilla client has as its current crosshair target for that tick, so you cannot
  attack two different entities "in the same instant" the way a template-based mod might. One tick, one
  crosshair target.
* **No bypass of the attack cooldown.** Attacking with a sword still waits for the cooldown; attacking a
  block or missing still triggers the ten tick "missed swing" timer.
* **No bypass of the use delay.** A *held* use key respects the vanilla four tick delay between uses.
* **No bypass of "hands busy" / "already using".** While you are eating you cannot attack, and while you
  are mining you cannot place a block, exactly as in vanilla.
* **No packet sending, no server communication.** There is no custom packet, no command and no network
  code at all. The mod only runs client-side logic; the server sees the normal attack and use actions
  that those vanilla calls produce.
* **No automation while the world is not loaded.** Both channels stop automatically when you leave a
  world or a server.

This is also the main difference from Tweakeroo's similar features: Tweakeroo can drive several
"template" positions inside a single tick. Client Interactions does not do that. It accepts the vanilla
client's one-target-per-tick rule and only automates the *timing* of the two normal mouse actions.

---

## Behaviour while a GUI is open

You asked for the automation to keep going when a GUI or the chat is open, and that is what it does -
with one important distinction that comes straight from vanilla:

* **Timers never stop.** All counters keep advancing while any screen is open, so switching to your
  inventory for a moment does not reset a periodic cycle.
* **Attack clicks pause** while a screen or overlay is open. This is exactly what vanilla does: with a
  screen open Minecraft sets its `missTime` to 10 000 and stops processing the attack key, so a real
  mouse click is dropped too. The mod follows that rule instead of fighting it. When a **hold** is
  active, the mod clears only that 10 000 sentinel so the hold can resume immediately after the screen
  closes; the normal 10 tick missed-swing penalty is left alone.
* **Use keeps working** while a screen is open, because you asked for that explicitly. A held use
  channel keeps using the item with the vanilla four tick delay, and a click-based use channel keeps
  clicking. Be aware of the consequences: while your inventory is open, a running use channel can still
  right-click whatever is under the crosshair in the world.

If a screen swallows your keybind (for example the chat, where typing `r` should type an `r`), the
toggle simply does not fire - the mod uses normal key bindings, so all of vanilla's screen handling
applies.

---

## Configuration

Open the config screen:

* with the **Open Client Interactions config** key (default `]`), or
* from **Mod Menu 鈫?Client Interactions 鈫?Config**, or
* by editing the JSON file directly (see below).

The screen has two columns. The left column picks the **mode** of each channel, the right column shows
only the numbers that the chosen mode actually uses:

| Setting        | Range    | Used by |
|----------------|----------|---------|
| Interval       | 1 - 200  | Periodic click, periodic hold, periodic burst |
| Hold           | 1 - interval | Periodic hold |
| Burst          | 1 - 100  | Periodic burst |
| Per tick       | 1 - 100  | Fast click, periodic click |

There are also three global switches:

* **Automation** - the master switch. Turning it off releases both buttons immediately but keeps every
  timer running, so switching it back on continues the current cycle.
* **HUD** - whether the status lines are drawn.
* **Chat messages** - whether a short action bar message is shown when a channel is toggled.

All changes are written to disk when you close the screen.

---

## Keybinds

| Key (default) | Name in Controls   | Effect |
|---------------|--------------------|--------|
| `R`           | Toggle attack automation | Turns the attack channel on or off |
| `V`           | Toggle use automation    | Turns the use channel on or off |
| `]`           | Open Client Interactions config | Opens the config screen |

The toggle is a normal key binding press, so double-tapping quickly can toggle twice - that is vanilla
behaviour for key bindings. All three can be rebound to any key or mouse button in
**Options 鈫?Controls 鈫?Key Binds**.

---

## HUD

While at least one channel is active, a line per active channel is drawn in the top left corner, for
example:

```
Attack: Periodic hold (every 20t, held 10t)
Use: Fast click (x3 per tick)
```

The HUD lists only the settings the active mode uses. Turn it off with the **HUD** switch in the config
screen.

---

## Mod Menu integration

If Mod Menu is installed, the mod list shows a **Config** button for Client Interactions that opens the
same screen. Mod Menu is optional; the mod loads and works without it.

---

## Config file format

The file is `.minecraft/config/clientinteractions.json`. It is written on every change and read at
startup. Missing or invalid values fall back to the defaults instead of crashing.

```json
{
  "enabled": true,
  "hud": true,
  "messages": true,
  "attack": {
    "mode": "periodic_hold",
    "interval": 20,
    "hold": 10,
    "perTick": 1,
    "burst": 3
  },
  "use": {
    "mode": "hold",
    "interval": 20,
    "hold": 10,
    "perTick": 1,
    "burst": 3
  }
}
```

Valid `mode` values are `hold`, `fast_click`, `periodic_click`, `periodic_hold` and `periodic_burst`.
Because the format is identical on every Minecraft version, a config file can be copied between
versions.

---

## Building from source

Requirements:

* JDK 25 for Gradle. Every target is built with the newest Loom, and this mod pins that Loom to a
  version which needs Java 25 to run.
* The JVM the game itself runs on is selected automatically per target: Java 17 for 1.19.4 / 1.20.1,
  Java 21 for 1.21.x, Java 25 for 26.3. Installing those JDKs (or letting Gradle's toolchain support
  download them) is required for the `runClient` / `runClientGameTest` tasks.
* An internet connection the first time, to download Minecraft and the toolchain.

```bash
# one target
./gradlew :versions:1.21.11:build

# every supported version, then collect the jars into build/libs
./gradlew clean build collectJars

# in-game end to end checks (1.21.10, 1.21.11 and 26.3)
./gradlew :versions:1.21.11:runClientGameTest
```

The collected jars end up in `build/libs`.

Notes for maintainers:

* Every target subproject compiles `common/src/main/java` directly into its own jar, so the released
  file is a single jar with no nested jars and no extra dependencies.
* `common/` is the version independent part: the timing engine, the mode enum, the settings validation
  and the config model. It has no Minecraft imports at all.
* `versions/<mc>/` contains the version specific part: the input bridge, the config screen, the HUD and
  the entry point. `GameInput`, `ConfigScreen` and `HudOverlay` are the only files that meaningfully
  differ between versions, because Minecraft changed its GUI and input APIs several times.
* `26.3` is special: it ships unobfuscated, so no mappings are applied and the access widener uses the
  `official` namespace instead of `named`.

---

## How it works

The mod is intentionally small and boring.

1. Every client tick, `AutomationEngine` asks the `ChannelScheduler` of each active channel what to do.
2. `ChannelScheduler` is pure arithmetic on a tick counter: it decides "hold", "N clicks" or "nothing"
   for this tick. It has no Minecraft dependency and is fully unit tested.
3. `GameInput` turns that decision into vanilla calls:
   * a click calls `Minecraft#startAttack()` or `Minecraft#startUseItem()`, the exact methods a real
     button press runs;
   * a hold writes the `isDown` flag of the vanilla attack / use key mapping, so the vanilla input loop
     does the work.
4. The three internals used here are opened with an access widener, declared in
   `clientinteractions.accesswidener` and visible in the jar.

Two implementation details are worth knowing because they are the reason a few vanilla behaviours look
slightly different from other automation mods:

* **`isDown` is written directly**, not through `KeyMapping#set`. On 1.21.10 and newer the attack and
  use keys are `ToggleKeyMapping`s, whose `setDown` *flips* the state when the "toggle" accessibility
  option is on. Writing the flag directly gives an actual hold in both cases.
* **No key presses are faked.** Simulating a key press would fight the real keyboard state and would
  make the vanilla client consume a click on the next tick; calling the action methods directly is what
  makes "N clicks per tick" possible without lying to the input system.

---

## Testing

What is verified automatically:

* **28 unit tests** on the version independent engine (`./gradlew :common:test`). They cover every mode,
  the interval and burst edge cases, clamping, mode switching mid-cycle, the master switch, release
  behaviour and a 7 000 tick drift check.
* **Compilation and packaging of all six targets**, including access widener validation and the remap
  step, via `./gradlew build`.
* **Jar contents**: every jar is checked to contain the classes, the access widener, the two language
  files and a `fabric.mod.json` with the right Minecraft version.
* **In-game end to end tests** on 1.21.10, 1.21.11 and 26.3 (`runClientGameTest`). These boot a real
  client, create a real world and assert that: a hold really sets the vanilla attack key down and
  releases it again, a click mode never leaves the key held, the scheduler advances exactly one step per
  client tick, a periodic hold really alternates between pressed and released, a burst survives a full
  cycle, the master switch releases and restores the key, and opening a screen makes the mod follow
  vanilla by stopping attacks while the timers keep running.

What is *not* automatically verified: 1.19.4, 1.20.1 and 1.21.1 have no client gametest API, so for
those three versions the verification is compilation, packaging plus a manual start of the game to
confirm the mod loads and initialises on the client. The runtime code paths they use are the same ones
the three tested versions use.

---

## Troubleshooting

**The toggle key does nothing.** Two common causes: another mod uses the same key (rebind it), or the
channel is disabled by the master **Automation** switch.

**The automation stops when I open my inventory.** Expected for attack (see above). Timers keep running,
so it resumes when you close it.

**Fast click is slower than the number I configured.** Expected whenever vanilla drops the extra actions:
attacking a block, attacking air, or using an item that takes time. The mod asks more often, it does not
force the game to accept it.

**The HUD does not appear.** Check the **HUD** switch, and that at least one channel is actually on.

**The game says the mod is for a different Minecraft version.** You put the wrong jar in `mods`. Use the
jar whose file name matches your game version.

---

## Credits

This mod exists because of the work of others. Thank you to:

* **Tweakeroo** - <https://github.com/maruohon/tweakeroo> - the reference for what these features should
  feel like. Client Interactions takes the *idea* of hold / periodic hold / periodic click modes for the
  attack and use keys from Tweakeroo's `tweakHoldAttack`, `tweakPeriodicAttack`, `tweakPeriodicHoldAttack`
  and their "use" counterparts. It deliberately does **not** reproduce Tweakeroo's placement-template
  features, and it differs by keeping the vanilla one-target-per-tick rule instead of driving several
  template positions in one tick.
* **Fabric API** - <https://github.com/FabricMC/fabric> - the key binding, client tick, HUD and lifecycle
  APIs used here, and the access widener support that makes the vanilla code paths reachable.
* **Mod Menu** - <https://github.com/TerraformersMC/ModMenu> - the `ModMenuApi` entrypoint used for the
  config button.
* **guardian** - <https://github.com/Steve2001/minecraft-decompiled> - the decompiled Minecraft sources
  used to check the exact behaviour of `handleKeybinds`, `startAttack`, `startUseItem`, `missTime` and
  the GUI APIs on each supported version.
* **鍏湀銇洦** - for providing the language model resources that made writing and checking this
  documentation possible. Thank you.

If any of the projects above require attribution in a particular form, that requirement wins over the
wording here; please open an issue if something is missing.

---

## License

This mod is released into the public domain under the [Unlicense](LICENSE). Do whatever you want with it.

---

# 中文说明

**[English](#client-interactions)** | **中文**

> **阅读指引**
> 中文部分 10 533 字（不计空白），按正常技术阅读速度完整阅读大约需要 23 分钟；英文部分
> 3 209 词（不计空格约 16 500 字符），完整阅读大约需要 13 分钟，两部分都读完约需 36 分钟。
> 完整的 [`LICENSE`](LICENSE) 为 1 212 字符（197 词、24 行），一分钟内即可读完。
>
> 如果你只想用这个模组，读 **快速上手** 和 **五种模式** 就够了；其余内容用于排查问题、理解
> 为什么行为是这样，或者从源码构建。

一个客户端 Fabric 模组，用五种可配置模式自动执行 Minecraft 的两种鼠标操作 —— **攻击**（左键）
与**使用**（右键），可自定义按键，配置界面也可以从 Mod Menu 进入。

模组不会向服务器发送任何普通玩家做不到的东西：每一个动作都走原版真实鼠标点击所走的同一条代码路径。

---

## 目录

1. [支持的版本](#支持的版本)
2. [快速上手](#快速上手)
3. [五种模式](#五种模式)
4. [模组不会做的事](#模组不会做的事)
5. [打开 GUI 时的行为](#打开-gui-时的行为)
6. [配置](#配置)
7. [按键](#按键)
8. [HUD](#hud)
9. [Mod Menu 集成](#mod-menu-集成)
10. [配置文件格式](#配置文件格式)
11. [从源码构建](#从源码构建)
12. [工作原理](#工作原理)
13. [测试](#测试)
14. [疑难排查](#疑难排查)
15. [致谢](#致谢)
16. [许可](#许可)

---

## 支持的版本

| Minecraft | Fabric Loader | Java | Jar |
|-----------|---------------|------|-----|
| 1.19.4    | >= 0.14.21    | 17   | `clientinteractions-1.19.4-1.0.0.jar` |
| 1.20.1    | >= 0.16.10    | 17   | `clientinteractions-1.20.1-1.0.0.jar` |
| 1.21.1    | >= 0.15.11    | 21   | `clientinteractions-1.21.1-1.0.0.jar` |
| 1.21.10   | >= 0.17.0     | 21   | `clientinteractions-1.21.10-1.0.0.jar` |
| 1.21.11   | >= 0.17.3     | 21   | `clientinteractions-1.21.11-1.0.0.jar` |
| 26.3      | >= 0.19.3     | 25   | `clientinteractions-26.3-1.0.0.jar` |

每个 Minecraft 版本对应一个独立的 jar。请只放与你游戏版本匹配的那一个；jar 里的
`fabric.mod.json` 声明了对应版本，版本不匹配时 Fabric 会拒绝加载。

**必需：** [Fabric Loader](https://fabricmc.net/use/installer/) 与
[Fabric API](https://modrinth.com/mod/fabric-api)。
**可选：** [Mod Menu](https://modrinth.com/mod/modmenu)，用于在模组列表中显示配置按钮。

---

## 快速上手

1. 把对应你游戏版本的 jar 放进 `.minecraft/mods`。
2. 启动游戏，打开 **选项 → 控制 → 按键绑定**，分类 **Client Interactions**。
3. 两个最重要的键是 **切换攻击自动化**（默认 `R`）和 **切换使用自动化**（默认 `V`）。
4. 按一下 `R`。左上角 HUD 会显示 `Attack: Hold (continuous)`，游戏会持续攻击（或持续挖掘你
   正对着的方块），直到你再按一次 `R`。
5. 用 `]`（默认）或通过 Mod Menu 打开配置界面，换一个模式或改数值。改动立即生效，即使该通道
   正在运行。

所有功能默认关闭，按下切换键之前不会有任何动作。

---

## 五种模式

每种模式都存在两份：一份给攻击通道，一份给使用通道。两个通道可以同时运行，各用不同的模式和
不同的数值。

所有间隔和持续时间都以**游戏刻**计。1 刻 = 50 毫秒，即每秒 20 刻，所以 `20t` 就是一秒。

### 1. 长按（Hold）

按一下切换键，按钮就保持按下，直到你再按一次。

* 攻击：原版客户端会持续调用 `continueAttack`，所以正对着的方块会被正常挖掘，实体则会按当前
  攻击冷却被攻击，跟真的按住左键完全一样。
* 使用：原版客户端会持续使用物品 —— 吃东西、喝药水、拉弓、举盾、反复右键方块 —— 跟真的按住
  右键完全一样。

想连续挖掘、或者想一直拉着弓，就用这个模式。

### 2. 快速连点（Fast click）

**每一刻**都注入 **每刻次数** 次点击。默认每刻 1 次，即每秒 20 次；范围是每刻 1 到 100 次。

这里有几点很关键：

* 一次点击就是调用一次原版的攻击 / 使用例程，也就是真实按键所执行的代码。那次调用内部的所有
  原版规则照常生效。
* 原版客户端本身每刻只处理一轮输入，所以模组直接注入额外的点击，而不是伪造按键。这正是"每刻
  多次"能够成立的原因，也说明了真正的上限是原版状态（攻击冷却、"正在使用物品"、"双手忙碌"
  等），而不是刻循环。
* 正因为这些原版状态，**实际**频率可能低于设定值：攻击方块或打空会触发 Minecraft 十刻的
  "挥空"计时器，在它结束前攻击会被静默丢弃；使用需要时间的物品（吃东西、拉弓）在完成前会挡住
  后续使用。模组从不绕过这些规则，只是更频繁地请求。

### 3. 周期点击（Periodic click）

每隔 **间隔** 刻注入 **每刻次数** 次点击。默认每 20 刻一次（每秒一次）。通道开启时会立刻点
一次，之后每满一个间隔再点。

适合"每秒点一次"这类自动化，比如反复使用某个方块，或者按节奏攻击。

### 4. 周期长按（Periodic hold）

按住 **按住** 刻，然后松开，每隔 **间隔** 刻重复。

例如间隔 20、按住 10，就是按半秒、松半秒，无限循环。按住时长被限制在间隔之内，所以不会串到
下一个周期；如果设成等于间隔，就相当于一直按住不松。

这个模式适合需要**按住**而不是点击的动作 —— 比如把弓拉到一半再放，或者一阵一阵地挖。

### 5. 周期连点（Periodic burst）

连发 **连发** 次点击，每次间隔一刻，然后等到 **间隔** 全部走完再重复。

例如间隔 40、连发 5，就是前五刻点五下（四分之一秒），然后静默 35 刻，再来一轮。如果连发数
超过间隔，会被压到间隔，于是每一刻都点。

### 模式对照

| 模式     | 每刻点击   | 周期        | 按钮按住 |
|----------|------------|-------------|----------|
| 长按     | 无         | -           | 一直     |
| 快速连点 | 每刻次数   | 每一刻      | 从不     |
| 周期点击 | 每刻次数   | 每 间隔 刻  | 从不     |
| 周期长按 | 无         | 每 间隔 刻  | 按住 刻  |
| 周期连点 | 1          | 每 间隔 刻  | 从不     |

---

## 模组不会做的事

这些限制是有意为之，目的是让模组停留在"玩家物理上能做到"的原版一侧。

* **不改变触及距离或范围。** 攻击与使用都发生在正常距离。
* **不在一刻内切换目标。** 模组从不改变你看的方向。一次点击总是落在那一刻原版客户端当前的
  准星目标上，所以你无法像基于模板的模组那样"在同一瞬间"攻击两个不同的实体。一刻，一个
  准星目标。
* **不绕过攻击冷却。** 用剑攻击仍要等冷却；攻击方块或打空仍会触发十刻的挥空计时器。
* **不绕过使用延迟。** **按住** 使用键时，两次使用之间会遵守原版的四刻延迟。
* **不绕过"双手忙碌" / "正在使用"。** 吃东西时不能攻击，挖掘时不能放方块，与原版完全一致。
* **不发送封包、不与服务端通信。** 没有任何自定义封包、命令或网络代码。模组只运行客户端逻辑；
  服务器看到的只是那些原版调用产生的正常攻击与使用动作。
* **世界未加载时不自动操作。** 退出世界或服务器时两个通道都会自动停止。

这也是与 Tweakeroo 类似功能最主要的区别：Tweakeroo 能在一刻内驱动多个"模板"位置，而
Client Interactions 不做这件事。它接受原版客户端"一刻一个目标"的规则，只自动化两种正常鼠标
动作的**时机**。

---

## 打开 GUI 时的行为

你要求 GUI 或聊天栏打开时自动化继续运行，模组确实如此 —— 但有一个直接来自原版的重要区别：

* **计时从不停下。** 任何界面打开时所有计数器都继续前进，所以中途打开背包不会重置周期。
* **攻击点击在界面打开时暂停。** 这完全是原版行为：界面打开时 Minecraft 会把 `missTime` 设为
  10 000 并停止处理攻击键，所以真实的鼠标点击同样会被丢弃。模组遵循这条规则而不是去对抗它。
  当**长按**生效时，模组只清除那个 10 000 哨兵值，好让界面关闭后长按立刻恢复；正常的十刻挥空
  惩罚不作改动。
* **使用在界面打开时继续工作**，因为你明确要求了这一点。按住式的使用通道会按原版四刻延迟继续
  使用物品，点击式的使用通道会继续点击。请注意后果：背包打开时，正在运行的使用通道仍会右键
  世界中准星所指的位置。

如果某个界面吃掉了你的按键（例如聊天栏里打 `r` 应该打出字母 r），切换键就不会触发 ——
模组使用普通按键绑定，所以原版对界面的所有处理都照常生效。

---

## 配置

打开配置界面：

* 按 **打开客户端交互配置** 键（默认 `]`），或
* 从 **Mod Menu → Client Interactions → Config** 进入，或
* 直接编辑 JSON 文件（见下文）。

界面分两栏。左栏选择每个通道的**模式**，右栏只显示所选模式真正用到的数值：

| 设置     | 范围       | 用于 |
|----------|------------|------|
| 间隔     | 1 - 200    | 周期点击、周期长按、周期连点 |
| 按住     | 1 - 间隔   | 周期长按 |
| 连发     | 1 - 100    | 周期连点 |
| 每刻次数 | 1 - 100    | 快速连点、周期点击 |

另外还有三个全局开关：

* **自动化** —— 总开关。关掉会立刻释放两个按钮，但所有计时继续运行，所以再打开时会接着当前
  周期继续。
* **HUD** —— 是否绘制状态文字。
* **聊天消息** —— 切换通道时是否在物品栏上方显示一行短消息。

所有改动在关闭界面时写入磁盘。

---

## 按键

| 默认键 | 控制中的名称         | 作用 |
|--------|----------------------|------|
| `R`    | 切换攻击自动化       | 打开或关闭攻击通道 |
| `V`    | 切换使用自动化       | 打开或关闭使用通道 |
| `]`    | 打开客户端交互配置   | 打开配置界面 |

切换走的是普通按键绑定，所以快速连按两下会切换两次 —— 这就是原版按键绑定的行为。三个键都可以
在 **选项 → 控制 → 按键绑定** 中改成任意键或鼠标键。

---

## HUD

只要至少一个通道处于活动状态，左上角就会为每个活动通道画一行，例如：

```
Attack: Periodic hold (every 20t, held 10t)
Use: Fast click (x3 per tick)
```

HUD 只列出当前模式用到的设置。可以在配置界面用 **HUD** 开关关掉。

---

## Mod Menu 集成

安装 Mod Menu 后，模组列表里会为 Client Interactions 显示 **Config** 按钮，打开同一个界面。
Mod Menu 是可选的，没有它模组也能正常加载和运行。

---

## 配置文件格式

文件位于 `.minecraft/config/clientinteractions.json`，每次改动都会写入，启动时读取。缺失或
非法的值会回退到默认值，而不是崩溃。

```json
{
  "enabled": true,
  "hud": true,
  "messages": true,
  "attack": {
    "mode": "periodic_hold",
    "interval": 20,
    "hold": 10,
    "perTick": 1,
    "burst": 3
  },
  "use": {
    "mode": "hold",
    "interval": 20,
    "hold": 10,
    "perTick": 1,
    "burst": 3
  }
}
```

`mode` 的合法值是 `hold`、`fast_click`、`periodic_click`、`periodic_hold`、
`periodic_burst`。由于每个 Minecraft 版本上的格式完全相同，配置文件可以跨版本复制。

---

## 从源码构建

需要：

* JDK 25 用于运行 Gradle。所有目标都用最新的 Loom 构建，而本项目把 Loom 固定在一个需要 Java 25
  才能运行的版本。
* 游戏本身使用的 JVM 会按目标自动选择：1.19.4 / 1.20.1 用 Java 17，1.21.x 用 Java 21，26.3 用
  Java 25。运行 `runClient` / `runClientGameTest` 任务前需要装好这些 JDK（或允许 Gradle 的
  toolchain 自动下载）。
* 首次构建需要联网，以下载 Minecraft 与工具链。

```bash
# 单个目标
./gradlew :versions:1.21.11:build

# 全部受支持版本，然后把 jar 收集到 build/libs
./gradlew clean build collectJars

# 游戏内端到端测试（1.21.10、1.21.11、26.3）
./gradlew :versions:1.21.11:runClientGameTest
```

收集好的 jar 位于 `build/libs`。

给维护者的说明：

* 每个目标子项目都把 `common/src/main/java` 直接编译进自己的 jar，所以发布出去的是单个 jar，
  没有嵌套 jar，也没有额外依赖。
* `common/` 是与版本无关的部分：计时引擎、模式枚举、设置校验和配置模型。它完全不 import
  Minecraft。
* `versions/<mc>/` 是与版本相关的部分：输入桥接、配置界面、HUD 和入口点。真正在版本之间有
  差异的只有 `GameInput`、`ConfigScreen` 和 `HudOverlay`，因为 Minecraft 多次改动过它的 GUI
  与输入 API。
* `26.3` 比较特殊：它不混淆，所以不套用任何映射，access widener 使用 `official` 命名空间而
  不是 `named`。

---

## 工作原理

模组刻意做得小而直白。

1. 每个客户端刻，`AutomationEngine` 询问每个活动通道的 `ChannelScheduler` 该做什么。
2. `ChannelScheduler` 就是对刻计数器的纯算术：它决定这一刻是"按住"、"N 次点击"还是"什么
   都不做"。它不依赖 Minecraft，并且有完整的单元测试。
3. `GameInput` 把这个决定变成原版调用：
   * 点击会调用 `Minecraft#startAttack()` 或 `Minecraft#startUseItem()`，也就是真实按键执行的
     同一批方法；
   * 按住会写入原版攻击 / 使用按键映射的 `isDown` 标志，让原版输入循环自己去工作。
4. 这里用到的内部成员通过 access widener 开放，声明在
   `clientinteractions.accesswidener` 中，并在 jar 里可见。

有两个实现细节值得了解，因为它们正是某些原版行为看起来与其他自动化模组不同的原因：

* **`isDown` 是直接写入的**，而不是通过 `KeyMapping#set`。在 1.21.10 及更新版本中，攻击与使用
  键是 `ToggleKeyMapping`，当"切换"辅助选项开启时它的 `setDown` 会**翻转**状态。直接写标志
  在两种情况下都能得到真正的按住。
* **不伪造按键。** 模拟按键会和真实的键盘状态打架，还会让原版客户端在下一刻消费掉一次点击；
  直接调用动作方法才是"每刻 N 次点击"能够成立而不欺骗输入系统的原因。

---

## 测试

自动验证的内容：

* 版本无关引擎上的 **28 个单元测试**（`./gradlew :common:test`）。覆盖每一种模式、间隔与连发的
  边界、数值钳制、周期中途切换模式、总开关、释放行为和一次 7 000 刻的漂移检查。
* **全部六个目标的编译与打包**，包括 access widener 校验和 remap 步骤，通过
  `./gradlew build`。
* **jar 内容**：每个 jar 都检查了是否包含类文件、access widener、两个语言文件，以及
  `fabric.mod.json` 中的 Minecraft 版本是否正确。
* **游戏内端到端测试**，覆盖 1.21.10、1.21.11、26.3（`runClientGameTest`）。它会启动真实客户端、
  创建一个真实世界，并断言：长按确实把原版攻击键设为按下并在停止后释放；点击模式不会让按键
  残留按下；调度器每客户端刻恰好前进一步；周期长按确实在按下与松开之间交替；连发能撑过完整
  一个周期；总开关会释放并恢复按键；打开界面时模组与原版一致地停止攻击，而计时继续运行。

**没有**自动验证的：1.19.4、1.20.1 与 1.21.1 没有客户端 gametest API，所以这三个版本的验证是
编译、打包，加上手动启动游戏确认模组在客户端正常加载与初始化。它们使用的运行期代码路径与那三个
已测版本相同。

---

## 疑难排查

**切换键没反应。** 常见原因有两个：别的模组占用了同一个键（改绑即可），或者通道被总开关
**自动化** 关掉了。

**打开背包后自动化停了。** 对攻击而言这是预期行为（见上文）。计时仍在运行，关闭后会接着继续。

**快速连点比设定的数值慢。** 只要原版丢弃了多余动作就会这样：攻击方块、攻击空气，或者使用
需要时间的物品。模组只是更频繁地请求，并不会强迫游戏接受。

**HUD 不显示。** 检查 **HUD** 开关，并确认至少有一个通道确实开着。

**游戏提示模组版本不匹配。** `mods` 里放错 jar 了。请使用文件名与你游戏版本对应的那个。

---

## 致谢

这个模组之所以存在，是因为别人的工作。感谢：

* **Tweakeroo** - <https://github.com/maruohon/tweakeroo> - 这些功能"手感"的参考。Client
  Interactions 从 Tweakeroo 的 `tweakHoldAttack`、`tweakPeriodicAttack`、
  `tweakPeriodicHoldAttack` 及其"使用"对应功能中借鉴了攻击与使用键的 长按 / 周期长按 /
  周期点击 模式**思路**。它刻意**不**复刻 Tweakeroo 的放置模板功能，区别在于保留原版
  "一刻一个目标"的规则，而不是在一刻内驱动多个模板位置。
* **Fabric API** - <https://github.com/FabricMC/fabric> - 这里使用的按键绑定、客户端刻、HUD
  与生命周期 API，以及让原版代码路径可达的 access widener 支持。
* **Mod Menu** - <https://github.com/TerraformersMC/ModMenu> - 用于配置按钮的
  `ModMenuApi` 入口点。
* **guardian** - <https://github.com/Steve2001/minecraft-decompiled> - 用于在各受支持版本上
  核对 `handleKeybinds`、`startAttack`、`startUseItem`、`missTime` 和 GUI API 确切行为的
  Minecraft 反编译源码。
* **六月の雨** - 感谢提供语言模型资源，使编写与校对本文档成为可能。

如果上述项目要求特定形式的署名，以它们的要求为准；若有遗漏，请提 issue。

---

## 许可

本模组以 [Unlicense](LICENSE) 释入公有领域。你想怎么用就怎么用。
