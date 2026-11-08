# Client Interactions

**English** | [中文](#中文说明)

> **Reading guide**
> This English section is 1 264 words (6 601 characters without spaces) and takes about 5 minutes.
> The Chinese section is 3 611 characters and takes about 8 minutes. The full [`LICENSE`](LICENSE) is
> 1 212 characters (197 words).

A client-side Fabric mod with ten independent automation features for Minecraft's two mouse buttons,
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

## The ten features

Five behaviours for each button. Every feature is separate: it has its own switch, its own key binding
(unbound by default) and its own numbers.

| Feature | Behaviour | Values |
|---------|-----------|--------|
| Hold | Keeps the button pressed until switched off | none |
| Fast click | `Per tick` clicks every tick | per tick |
| Periodic click | `Per tick` clicks every `Interval` | interval, per tick |
| Periodic hold | Holds for `Hold` ticks every `Interval` | interval, hold |
| Periodic burst | One click per tick for `Burst` ticks every `Interval` | interval, burst |

Grouped per button: **Attack** and **Use**. Times are game ticks (1 tick = 50 ms, 20 per second).

## Mutual exclusion

Activating a feature switches off the other features of the same button, because they drive the same
mouse button and would fight over it. Attack and Use are independent, so one of each can run at the same
time.

## Defaults

All ten features start **off** and **unbound**. Their on/off state is not saved, so a restart never
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

* **Attack** pauses, as in vanilla: a screen makes vanilla discard attack input. A hold clears only the
  "screen is open" marker so it resumes when the screen closes.
* **Use** keeps working, because you asked for that. A running use feature can still act on the world
  while your inventory is open.

## Config screen

Open it with the config key (default `]`) or from Mod Menu. There is one page per button, and the
page switch is the top right button, so the five features on screen are exactly the ones that exclude
each other.

Each row is one feature: the name toggles it, the key button next to it rebinds it, and the value
buttons cycle through sensible steps. The row widths come from one width budget, so nothing can run past
the edge, and the row spacing shrinks on a short window so the last row never sits under Done.

### Combinations

A hotkey can be a combination such as `Ctrl+Shift+R`, like Tweakeroo's. Click the key button, then hold
the modifiers and press the main key. The screen shows what is bound now, which modifiers are held at
that instant, and what a modifier pressed on its own means.

Matching is exact: `R` does not fire while Shift is held, and `Shift+R` does not fire without it, so one
key press can never trigger two features. `Esc` or the `Clear` button leaves the feature unbound.

Config lives in `config/clientinteractions.json`, one block per feature plus its key. Missing or invalid
values fall back to defaults. The format is identical on every supported version.

```json
{
  "enabled": true, "hud": true, "messages": true,
  "attack_hold":          { "interval": 20, "duration": 10, "perTick": 1, "burst": 3,
                            "key": "key.keyboard.r", "modifiers": "ctrl+shift" },
  "attack_periodic_hold": { "interval": 20, "duration": 10, "perTick": 1, "burst": 3, "key": "key.keyboard.h" },
  "use_fast_click":       { "interval": 20, "duration": 10, "perTick": 4, "burst": 3 }
}
```

Feature ids: `attack_*` and `use_*` combined with `hold`, `fast_click`, `periodic_click`,
`periodic_hold`, `periodic_burst`.

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

* 36 unit tests: every behaviour, clamping, mutual exclusion per button, channel independence, the
  master switch, starting with the master switch off, combination matching, and a 7000 tick drift check.
* 1.21.10, 1.21.11 and 26.3 have in-game tests that boot a real client, create a world, and assert
  holds, clicks, one-tick-per-tick timing, mutual exclusion, the master switch, screen behaviour,
  combination matching, and that both screens draw visible text with no widget overlapping another or
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
> 中文部分 3 611 字（不计空白），大约需要 8 分钟；英文部分 1 264 词（不计空格 6 601 字符），
> 大约需要 5 分钟。完整的 [`LICENSE`](LICENSE) 为 1 212 字符（197 词）。

一个客户端 Fabric 模组，为 Minecraft 的两个鼠标键——**攻击**（左键）与**使用**（右键）——提供
**十个互相独立**的自动化功能。每个功能都有自己的开关、自己的快捷键和自己的参数。

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

## 十个功能

每个按键五种行为，共十个独立功能。

| 功能 | 行为 | 参数 |
|------|------|------|
| 长按 | 保持按下直到关闭 | 无 |
| 快速连点 | 每刻注入 `每刻次数` 次点击 | 每刻次数 |
| 周期点击 | 每隔 `间隔` 刻注入 `每刻次数` 次点击 | 间隔、每刻次数 |
| 周期长按 | 每隔 `间隔` 刻按住 `按住` 刻 | 间隔、按住 |
| 周期连点 | 每隔 `间隔` 刻连发 `连发` 次点击 | 间隔、连发 |

按按键分为两组：**攻击**与**使用**。时间单位为游戏刻（1 刻 = 50 毫秒，每秒 20 刻）。

## 互斥

同一按键上开启一个功能时，会自动关闭该按键的其它功能——它们驱动同一个鼠标键，同时运行会互相
抢夺。攻击与使用互相独立，各自可以同时开一个。

## 默认状态

十个功能默认**全部关闭**且**全部无快捷键**。功能的开关状态不会被保存，所以重启游戏不会自动开始
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

* **攻击**会暂停，与原版一致：界面打开时原版会丢弃攻击输入。长按时模组只清除「界面已打开」
  这个标记，界面关闭后即可恢复。
* **使用**会继续工作（按你的要求）。背包打开时，正在运行的使用功能仍会作用于世界。

## 配置界面

用配置键（默认 `]`）或从 Mod Menu 打开。每个按键一页，翻页按钮在右上角，所以
屏幕上的五个功能正好就是互斥的那五个。

每一行是一个功能：点名字切换开关，点它旁边的按键按钮重新绑定，点数值按钮在合理
档位之间循环。各列宽度由同一份宽度预算分配，所以不会跑出屏幕；窗口矮时行距会
收紧，最后一行不会压在 Done 上。

### 组合键

快捷键可以是 `Ctrl+Shift+R` 这样的组合键，和 Tweakeroo 一样。点一下按键按钮，
然后按住修饰键再按主键。界面会显示当前绑定、此刻按住的修饰键，以及单独按下修饰
键时的提示。

匹配是严格的：按住 Shift 不会触发 `R`，不按 Shift 也不会触发 `Shift+R`，所以一次
按键绝不会同时触发两个功能。Escape 或 `Clear` 按钮可取消绑定。

配置文件位于 `config/clientinteractions.json`，每个功能一个区块，连同它的快捷键。缺失或非法值
会回退到默认值。各受支持版本的格式完全相同。

```json
{
  "enabled": true, "hud": true, "messages": true,
  "attack_hold":          { "interval": 20, "duration": 10, "perTick": 1, "burst": 3,
                            "key": "key.keyboard.r", "modifiers": "ctrl+shift" },
  "attack_periodic_hold": { "interval": 20, "duration": 10, "perTick": 1, "burst": 3, "key": "key.keyboard.h" },
  "use_fast_click":       { "interval": 20, "duration": 10, "perTick": 4, "burst": 3 }
}
```

功能 id 为 `attack_*` / `use_*` 加上 `hold`、`fast_click`、`periodic_click`、`periodic_hold`、
`periodic_burst`。

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

* 36 个单元测试：每种行为、数值钳制、同按键互斥、双通道独立、总开关、以关闭总开关启动、
  组合键匹配，以及 7000 刻的漂移检查。
* 1.21.10、1.21.11、26.3 有游戏内测试：启动真实客户端、创建世界，断言长按、点击、每刻一步的
  计时、互斥、总开关、界面行为、组合键匹配，以及在较小 GUI 下两个界面都能画出可见文字、
  且没有按钮互相重叠或跑出屏幕。
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
