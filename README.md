# Client Interactions

**English** | [中文](#中文说明)

> **Reading guide**
> This English section is 1 679 words (8 557 characters without spaces) and takes about 6 minutes.
> The Chinese section is 4 431 characters and takes about 8 minutes. The full [`LICENSE`](LICENSE) is
> 1 212 characters (197 words).

A client-side Fabric mod with eight independent automation features for Minecraft's two mouse buttons,
**attack** (left) and **use** (right). Each feature has its own switch, its own key binding and its own
timing values.

## Versions

| Minecraft | Fabric Loader | Java |
|-----------|---------------|------|
| 1.19.4    | >= 0.14.21    | 17   |
| 1.20.1    | >= 0.16.10    | 17   |
| 1.21.1    | >= 0.15.11    | 21   |
| 1.21.10   | >= 0.17.0     | 21   |
| 1.21.11   | >= 0.17.3     | 21   |
| 26.3      | >= 0.19.3     | 25   |

Use `clientinteractions-<mc version>-1.0.0.jar`. Requires
[Fabric API](https://modrinth.com/mod/fabric-api); [Mod Menu](https://modrinth.com/mod/modmenu) is
optional and only adds the config button.

## The eight features

Four behaviours for each button. Every feature is separate: it has its own switch, its own key binding
(unbound by default) and its own numbers.

| Feature | Behaviour | Acts when | Values |
|---------|-----------|-----------|--------|
| Hold | Keeps the button pressed until switched off | on its own | none |
| Fast click | Turns one of **your** clicks into `Clicks` clicks | only when you click | clicks |
| Periodic click | Fires `Clicks` clicks over consecutive ticks every `Interval` | on its own | interval, clicks |
| Periodic hold | Holds for `Hold` ticks every `Interval` | on its own | interval, hold |

Grouped per button: **Attack** and **Use**. Times are game ticks (1 tick = 50 ms, 20 per second).

### Fast click is a multiplier, not a timer

Fast click is the only feature that does nothing by itself. Turn it on and the next time **you** click
that mouse button, the mod repeats the click for you, so one physical click becomes `Clicks` clicks.
Every other feature acts on its own once switched on.

### Why periodic click fires its clicks on separate ticks

The clicks are spread one per tick, not all in one tick. Vanilla resets the attack strength on the first
click, so extra clicks in the same tick would be swallowed and a "burst" would really be a single hit.
Separate ticks is what makes `Clicks` actually mean that many hits.

## Mutual exclusion

Activating a feature switches off the other features of the same button, because they drive the same
mouse button and would fight over it. Attack and Use are independent, so one of each can run at the same
time.

## Defaults

All eight features start **off** and **unbound**. Their on/off state is not saved, so a restart never
starts clicking. Bind a key per feature in the config screen.

## Vanilla limits

The mod calls the same code paths a real click runs, so vanilla rules still apply:

* Attack respects the attack cooldown and the 10 tick missed-swing timer.
* A held use key respects the 4 tick use delay.
* You cannot attack while using an item, or use one while mining.
* One tick has one crosshair target, so a tick cannot hit two different entities.

The real rate can therefore be lower than the configured rate. The mod asks more often; it does not
bypass the checks.

## With a screen open

Timers keep running in every feature.

* **Attack** pauses, as in vanilla: a screen makes vanilla discard attack input. A hold resumes when the
  screen closes; it is deliberately not forced while a GUI is open, since that would inject attacks into
  the inventory.
* **Use** keeps working, because you asked for that. A running use feature can still act on the world
  while your inventory is open.

## Config screen

Open it from Mod Menu, or bind a key for it with the **Key** button on the config screen (no key is bound
by default). There is one page per button, and the
page switch is the top right button, so the five features on screen are exactly the ones that exclude
each other.

Each row is one feature: the name toggles it, the key button next to it rebinds it, and the labelled
fields hold its numbers. The row widths come from one width budget, so nothing can run past the edge, and
the row spacing shrinks on a short window so the last row never sits under Done.

### Combinations and mouse buttons

A hotkey is a **set of keys held together**. Any keyboard key or any mouse button (left, right, middle,
4-8) may take part, in any mix: `Ctrl+R`, `Ctrl+Shift+Alt+R`, `A+C` and `Ctrl+Left Click` are all the
same kind of binding. Modifiers are not special, they are simply keys.

Open the key button, press the keys in any order, then press **Done**. Nothing is committed until Done,
so multi-key combinations like `A+C` can be built; **Undo** removes the last key and **Clear** empties
the set. The screen shows the saved binding and the one being built.

Matching rules:

* Every key of the binding must be held.
* Holding a Ctrl/Shift/Alt/Win that is **not** part of the binding blocks it, so `R` does not fire while
  Shift is down.
* Other ordinary keys are ignored, since you are always holding something (W, space, ...).

### Priority between overlapping bindings

If one feature owns `A+C` and another owns plain `C`, pressing both keys runs **only** the `A+C`
feature. The longer combination takes precedence, and the shorter one works again once the longer is
released. This is what keeps a single key usable both alone and as part of a combination.

A rebind takes effect immediately, without restarting the game.

### Numbers

Each number is a text field: click it and type the value, instead of stepping through presets. Only digits
are accepted and out-of-range input is clamped to the same limits the engine enforces.

Config lives in `config/clientinteractions.json`, one block per feature plus its key. Missing or invalid
values fall back to defaults. The format is identical on every supported version.

```json
{
  "enabled": true, "hud": true, "messages": true,
  "attack_hold":          { "interval": 20, "duration": 10, "count": 3,
                            "key": "key.keyboard.r", "modifiers": "ctrl+shift" },
  "attack_periodic_hold": { "interval": 20, "duration": 10, "count": 3,
                            "key": "key.mouse.4", "modifiers": "win" },
  "use_fast_click":       { "interval": 20, "duration": 10, "count": 4 }
}
```

Feature ids: `attack_*` and `use_*` combined with `hold`, `fast_click`, `periodic_click`,
`periodic_hold`. A config from an older build that still has `perTick` or `burst` is read as
`count` (the larger of the two), so nothing is lost.

## Building

JDK 25 runs Gradle; the game is launched with the Java version its Minecraft release needs (17 / 21 /
25), selected per target, so those JDKs must be installed.

```bash
./gradlew build collectJars                    # all six targets -> build/libs
./gradlew :versions:1.21.11:build             # one target
./gradlew :versions:1.21.11:runClientGameTest # in-game tests (1.21.10, 1.21.11, 26.3)
```

`common/` holds the version independent engine, feature definitions and validation, with no Minecraft
imports. Each `versions/<mc>/` compiles it into its own jar, so releases are single jars. `26.3` is
unobfuscated, so it uses no mappings and an `official` access widener.

`build` also runs `verifyReleaseJar`, which inspects the packaged jar rather than the development
output: the jar must declare its access widener in `fabric.mod.json`, contain the file, and use the
namespace the game runs with. Development runs hide this because Loom applies the widener to the
classpath regardless of the declaration.

## Tests

* 51 unit tests: every behaviour, clamping, mutual exclusion per button, channel independence, the
  master switch, starting with the master switch off, combination matching (sets of keys, mouse buttons,
  priority between overlapping bindings), fast click being a multiplier, the periodic-click tick spread,
  and a 7000 tick drift check.
* 1.21.10, 1.21.11 and 26.3 have in-game tests that boot a real client, create a world, and assert
  holds, clicks, one-tick-per-tick timing, mutual exclusion, the master switch, screen behaviour,
  combination matching, that a multi-key combination is captured whole, that it fires on a real press
  without toggling itself back off, that a longer combination blocks a shorter one it contains, that a
  hold resumes after a screen closes, that a rebind fires without a restart, that a mouse button binds
  too, that a typed number reaches the engine and is clamped, that fast click injects the extra clicks
  for a real press, and that both screens draw visible text with no widget overlapping another or
  leaving the screen at a small gui.
* 1.19.4, 1.20.1 and 1.21.1 have no client gametest API; they are verified by compiling, packaging and
  starting the game.

## Troubleshooting

* **A feature does nothing** - its key is unbound, the **Automation** master switch is off, or another
  feature of the same button is running (they exclude each other).
* **Stops when I open my inventory** - expected for attack; see above.
* **Slower than configured** - vanilla is dropping the extra actions; see Vanilla limits.
* **Wrong version error** - the jar does not match your game version.
* **Game crashes while starting, only after turning Automation off** - fixed in this build. The switch
  state is saved, and starting with it off used to crash the client before the window appeared. If an
  older build does this, delete `config/clientinteractions.json`.

## Credits

* **Tweakeroo** - <https://github.com/maruohon/tweakeroo> - the reference for hold and periodic modes.
  This mod does not copy its placement template features and keeps the one-target-per-tick rule.
* **Fabric API** - <https://github.com/FabricMC/fabric> - key bindings, client ticks, HUD and lifecycle
  events, plus access wideners.
* **Mod Menu** - <https://github.com/TerraformersMC/ModMenu> - the config button entrypoint.
* **guardian** - <https://github.com/Steve2001/minecraft-decompiled> - decompiled sources used to check
  `handleKeybinds`, `startAttack`, `startUseItem`, `missTime` and the GUI APIs per version.
* **六月の雨** - for providing the language model resources used to write and check this documentation.

If any project above requires a specific attribution form, that requirement wins; please open an issue
if something is missing.

## License

[Unlicense](LICENSE).

---

# 中文说明

**[English](#client-interactions)** | **中文**

> **阅读指引**
> 中文部分 4 431 字（不计空白），大约需要 8 分钟；英文部分 1 679 词（不计空格 8 557 字符），
> 大约需要 6 分钟。完整的 [`LICENSE`](LICENSE) 为 1 212 字符（197 词）。

一个客户端 Fabric 模组，为 Minecraft 的两个鼠标键——**攻击**（左键）与**使用**（右键）——提供
**八个互相独立**的自动化功能。每个功能都有自己的开关、自己的快捷键和自己的参数。

## 支持的版本

| Minecraft | Fabric Loader | Java |
|-----------|---------------|------|
| 1.19.4    | >= 0.14.21    | 17   |
| 1.20.1    | >= 0.16.10    | 17   |
| 1.21.1    | >= 0.15.11    | 21   |
| 1.21.10   | >= 0.17.0     | 21   |
| 1.21.11   | >= 0.17.3     | 21   |
| 26.3      | >= 0.19.3     | 25   |

请使用 `clientinteractions-<游戏版本>-1.0.0.jar`。需要
[Fabric API](https://modrinth.com/mod/fabric-api)；[Mod Menu](https://modrinth.com/mod/modmenu) 可选，
只用于添加配置按钮。

## 八个功能

每个按键四种行为，共八个独立功能。

| 功能 | 行为 | 触发时机 | 参数 |
|------|------|----------|------|
| 长按 | 保持按下直到关闭 | 自动 | 无 |
| 快速连点 | 把**你自己**的一次点击变成 `次数` 次 | 仅在你点击时 | 次数 |
| 周期点击 | 每隔 `间隔` 刻，在连续刻上打出 `次数` 次点击 | 自动 | 间隔、次数 |
| 周期长按 | 每隔 `间隔` 刻按住 `按住` 刻 | 自动 | 间隔、按住 |

按按键分为两组：**攻击**与**使用**。时间单位为游戏刻（1 刻 = 50 毫秒，每秒 20 刻）。

### 快速连点是倍率，不是定时器

快速连点是唯一不会自行动作的功能。开启后，**你**下一次按下该鼠标键时，模组替你把这个点击
重复若干次，于是一次物理点击变成 `次数` 次点击。其余功能一旦开启都会自动执行。

### 周期点击为什么把点击分散到不同刻

这些点击是一刻一次、连续打出的，而不是全挤在同一刻。原版在第一次点击时就会重置攻击蓄力，
同一刻的后续点击会被吞掉，所谓「连发」实际只有一下。分散到不同刻，`次数` 才真的等于打了几下。

## 互斥

同一按键上开启一个功能时，会自动关闭该按键的其它功能——它们驱动同一个鼠标键，同时运行会互相
抢夺。攻击与使用互相独立，各自可以同时开一个。

## 默认状态

八个功能默认**全部关闭**且**全部无快捷键**。功能的开关状态不会被保存，所以重启游戏不会自动开始
点击。快捷键请在配置界面里逐个绑定。

## 原版限制

模组调用的是真实点击所走的同一套代码路径，所以原版规则照常生效：

* 攻击受攻击冷却与十刻挥空计时限制。
* 按住使用键时受四刻使用延迟限制。
* 使用物品时不能攻击，挖掘时不能使用物品。
* 一刻只有一个准星目标，无法在同一刻攻击两个不同实体。

因此实际频率可能低于设定值：模组只是更频繁地请求，不会绕过这些检查。

## 界面打开时

所有功能的计时都继续。

* **攻击**会暂停，与原版一致：界面打开时原版会丢弃攻击输入。界面关闭后长按会恢复；
  界面打开期间刻意不强行按键，否则会把攻击注入到背包界面里。
* **使用**会继续工作（按你的要求）。背包打开时，正在运行的使用功能仍会作用于世界。

## 配置界面

从 Mod Menu 打开，或用配置界面上的 **Key** 按钮给它绑一个键（默认不绑定）。每个按键一页，
翻页按钮在右上角，所以
屏幕上的五个功能正好就是互斥的那五个。

每一行是一个功能：点名字切换开关，点它旁边的按键按钮重新绑定，带标签的输入框填写
数值。各列宽度由同一份宽度预算分配，所以不会跑出屏幕；窗口矮时行距会收紧，
最后一行不会压在 Done 上。

### 组合键与鼠标键

快捷键是一组**同时按住**的键。任意键盘键或任意鼠标键（左键、右键、中键、4 到 8）
都能参与，可以随意混搭：`Ctrl+R`、`Ctrl+Shift+Alt+R`、`A+C`、`Ctrl+鼠标左键`
都是同一类绑定。修饰键并不特殊，它们只是普通的键。

点开按键按钮，按任意顺序按下这些键，然后按 **Done**。按下 Done 之前不会提交，所以
`A+C` 这类多键组合可以完整录入；**Undo** 删掉最后一个键，**Clear** 清空。界面会同时
显示已保存的绑定和正在录入的绑定。

匹配规则：

* 绑定里的每个键都必须按住。
* 按住不属于该绑定的 Ctrl/Shift/Alt/Win 会使其失效，所以按着 Shift 时 `R` 不会触发。
* 其它普通键会被忽略，因为你手上总按着点什么（W、空格等）。

### 重叠绑定之间的优先级

如果一个功能占用了 `A+C`，另一个占用了单独的 `C`，那么同时按下这两个键时**只会**执行
`A+C` 的功能。长组合优先，松开后短组合又能正常工作。这样同一个键既能单独用，也能作为
组合的一部分。

### 数值

每个数值都是输入框：点进去直接输入，不必在预设档位之间循环。只接受数字，超出范围
的输入会被钳制到引擎同一套上下限。

配置文件位于 `config/clientinteractions.json`，每个功能一个区块，连同它的快捷键。缺失或非法值
会回退到默认值。各受支持版本的格式完全相同。

```json
{
  "enabled": true, "hud": true, "messages": true,
  "attack_hold":          { "interval": 20, "duration": 10, "count": 3,
                            "key": "key.keyboard.r", "modifiers": "ctrl+shift" },
  "attack_periodic_hold": { "interval": 20, "duration": 10, "count": 3,
                            "key": "key.mouse.4", "modifiers": "win" },
  "use_fast_click":       { "interval": 20, "duration": 10, "count": 4 }
}
```

功能 id 为 `attack_*` / `use_*` 加上 `hold`、`fast_click`、`periodic_click`、`periodic_hold`。
旧版本配置里若仍有 `perTick` 或 `burst`，会被当作 `count` 读取（取两者较大值），不会丢失。

## 构建

Gradle 用 JDK 25 运行；游戏本身用其 Minecraft 版本所需的 Java 启动（17 / 21 / 25），按目标自动
选择，所以需要装好这些 JDK。

```bash
./gradlew build collectJars                    # 全部六个目标 -> build/libs
./gradlew :versions:1.21.11:build              # 单个目标
./gradlew :versions:1.21.11:runClientGameTest  # 游戏内测试（1.21.10、1.21.11、26.3）
```

`common/` 是与版本无关的引擎、功能定义与校验，不 import Minecraft。每个 `versions/<mc>/` 把它
编译进自己的 jar，因此发布的是单个 jar。`26.3` 不混淆，所以不使用映射，access widener 使用
`official` 命名空间。

`build` 还会运行 `verifyReleaseJar`，检查的是打包后的 jar 而不是开发产物：jar 必须在
`fabric.mod.json` 中声明 access widener、包含该文件，并使用游戏运行时所用的命名空间。开发环境会
掩盖这个问题，因为 Loom 不论是否声明都会把 widener 应用到 classpath 上。

## 测试

* 51 个单元测试：每种行为、数值钳制、同按键互斥、双通道独立、总开关、以关闭总开关启动、
  组合键匹配（键集合、鼠标键、重叠绑定之间的优先级）、快速连点的倍率语义、周期点击的
  跨刻分布，以及 7000 刻的漂移检查。
* 1.21.10、1.21.11、26.3 有游戏内测试：启动真实客户端、创建世界，断言长按、点击、每刻一步的
  计时、互斥、总开关、界面行为、组合键匹配、多键组合被完整捕获、按下后不会自我抵消、
  长组合会屏蔽包含的短绑定、界面关闭后长按会恢复、改键无需重启即生效、鼠标键可绑定、
  输入的数值会进入引擎并被钳制、快速连点确实为一次真实按下注入了额外点击，以及在较小
  GUI 下两个界面都能画出可见文字、且没有按钮互相重叠或跑出屏幕。
  数值会进入引擎并被钳制、快速连点确实为一次真实按下注入了额外点击、三修饰键组合被
  完整捕获，以及在较小 GUI 下两个界面都能画出可见文字、且没有按钮互相重叠或跑出屏幕。
* 1.19.4、1.20.1、1.21.1 没有客户端 gametest API，通过编译、打包和启动游戏验证。

## 疑难排查

* **某个功能没反应** — 快捷键未绑定，或 **自动化** 总开关关闭，或同一按键上已有其它功能在运行
  （它们互斥）。
* **打开背包后停了** — 攻击功能的预期行为，见上文。
* **比设定值慢** — 原版丢弃了多余动作，见「原版限制」。
* **版本不匹配** — jar 与游戏版本不对应。
* **关掉「自动化」后游戏下次启动崩溃** — 本版本已修复。该开关状态会被保存，而旧版本在关闭状态下
  启动会在窗口出现前崩溃。如果旧版本出现此问题，删除 `config/clientinteractions.json` 即可。

## 致谢

* **Tweakeroo** — <https://github.com/maruohon/tweakeroo> — 长按与周期模式的参考。本模组未复制
  其放置模板功能，并保留「一刻一个目标」的规则。
* **Fabric API** — <https://github.com/FabricMC/fabric> — 按键绑定、客户端刻、HUD 与生命周期
  事件，以及 access widener 支持。
* **Mod Menu** — <https://github.com/TerraformersMC/ModMenu> — 配置按钮的入口点。
* **guardian** — <https://github.com/Steve2001/minecraft-decompiled> — 用于核对各版本的
  `handleKeybinds`、`startAttack`、`startUseItem`、`missTime` 与 GUI API 的反编译源码。
* **六月の雨** — 感谢提供语言模型资源，用于编写与校对本文档。

如果上述项目要求特定形式的署名，以它们的要求为准；若有遗漏请提 issue。

## 许可

[Unlicense](LICENSE)。
