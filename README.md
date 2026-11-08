# Client Interactions

**English** | [中文](#中文说明)

> **Reading guide**
> This English section is 1 045 words (5 496 characters without spaces) and takes about 4 minutes.
> The Chinese section is 3 137 characters and takes about 7 minutes, so both together are about
> 11 minutes. The full [`LICENSE`](LICENSE) is 1 212 characters (197 words, 24 lines).
>
> To use the mod, read **Usage** and **Modes**. The rest covers the vanilla limits, building and
> troubleshooting.

A client-side Fabric mod that automates Minecraft's two mouse actions, **attack** (left) and
**use** (right), with five modes and a config screen reachable from Mod Menu.

## Versions

| Minecraft | Fabric Loader | Java |
|-----------|---------------|------|
| 1.19.4    | >= 0.14.21    | 17   |
| 1.20.1    | >= 0.16.10    | 17   |
| 1.21.1    | >= 0.15.11    | 21   |
| 1.21.10   | >= 0.17.0     | 21   |
| 1.21.11   | >= 0.17.3     | 21   |
| 26.3      | >= 0.19.3     | 25   |

Use the jar matching your game version: `clientinteractions-<mc version>-1.0.0.jar`.
Requires [Fabric API](https://modrinth.com/mod/fabric-api); [Mod Menu](https://modrinth.com/mod/modmenu)
is optional and only adds the config button.

## Usage

Keys (rebindable under Controls):

| Default | Action |
|---------|--------|
| `R` | Toggle attack automation |
| `V` | Toggle use automation |
| `]` | Open the config screen |

Everything is off until you press a toggle key. The active channel is shown in the top-left
corner; turn that off with the **HUD** switch.

## Modes

Times are in game ticks (1 tick = 50 ms, 20 per second). Each channel has its own mode and
values.

| Mode | Behaviour |
|------|-----------|
| Hold | Button stays pressed until toggled off. Mines blocks and keeps using items as if held. |
| Fast click | `Per tick` clicks every tick (1-100, default 1). |
| Periodic click | `Per tick` clicks once every `Interval` ticks. |
| Periodic hold | Holds for `Hold` ticks every `Interval` ticks. |
| Periodic burst | One click per tick for `Burst` ticks every `Interval` ticks. |

## Vanilla limits

The mod drives the normal click code paths, so vanilla rules still apply:

* Attack respects the attack cooldown and the 10 tick missed-swing timer.
* A held use key respects the 4 tick use delay.
* You cannot attack while using an item, or use one while mining.
* One tick has one crosshair target, so a tick cannot hit two different entities.

Because of these, the real rate can be lower than the configured rate. The mod asks more often;
it does not bypass the checks.

## With a screen open

Timers keep running in every mode.

* **Attack** pauses, as in vanilla: a screen makes vanilla discard attack input. A hold clears
  only the "screen is open" marker so it resumes when the screen closes.
* **Use** keeps working, because you asked for that. This means a running use channel can still
  act on the world while your inventory is open.

## Config screen

Open it with `]` or from Mod Menu. Left column picks the mode per channel; the right column shows
only the values that mode uses. Three switches: **Automation** (master, releases the buttons but
keeps timers running), **HUD**, **Chat messages**.

Values: `Interval` 1-200, `Hold` 1-interval, `Burst` 1-100, `Per tick` 1-100.

Config lives in `config/clientinteractions.json` and is written on every change. The format is the
same on every supported version, so the file can be copied between them. Missing or invalid values
fall back to defaults.

```json
{
  "enabled": true, "hud": true, "messages": true,
  "attack": { "mode": "periodic_hold", "interval": 20, "hold": 10, "perTick": 1, "burst": 3 },
  "use":    { "mode": "hold",          "interval": 20, "hold": 10, "perTick": 1, "burst": 3 }
}
```

Modes: `hold`, `fast_click`, `periodic_click`, `periodic_hold`, `periodic_burst`.

## Building

JDK 25 runs Gradle. The game itself is launched with the Java version its Minecraft release needs
(17 / 21 / 25), selected automatically per target, so those JDKs must be installed.

```bash
./gradlew build collectJars                 # all six targets -> build/libs
./gradlew :versions:1.21.11:build          # one target
./gradlew :versions:1.21.11:runClientGameTest  # in-game tests (1.21.10, 1.21.11, 26.3)
```

`common/` is the version independent timing engine and has no Minecraft imports. Each
`versions/<mc>/` compiles it directly into its jar, so releases are single jars with no nested
jars. `26.3` is unobfuscated, so it uses no mappings and an `official` access widener.

`build` also runs `verifyReleaseJar`, which checks the packaged jar rather than the development
output: the jar must declare its access widener in `fabric.mod.json`, contain the file, and use the
namespace the game runs with. Development runs hide this because Loom applies the widener to the
classpath regardless of the declaration, so a missing declaration only fails for players.

## Tests

* 28 unit tests on the timing engine (`./gradlew :common:test`): every mode, clamping, mode
  switching mid-cycle, the master switch, release behaviour, and a 7000 tick drift check.
* 1.21.10, 1.21.11 and 26.3 have in-game tests that boot a real client, create a world, and assert
  holds, clicks, one-tick-per-tick timing, the master switch, screen behaviour and that the config
  screen renders.
* 1.19.4, 1.20.1 and 1.21.1 have no client gametest API; they are verified by compiling, packaging
  and starting the game.

## Troubleshooting

* **Toggle key does nothing** - another mod uses the key, or the **Automation** master switch is off.
* **Stops when I open my inventory** - expected for attack; see above.
* **Slower than configured** - vanilla is dropping the extra actions; see Vanilla limits.
* **Wrong version error** - the jar does not match your game version.

## Credits

* **Tweakeroo** - <https://github.com/maruohon/tweakeroo> - the reference for hold and periodic
  modes. This mod does not copy its placement template features and keeps the one-target-per-tick
  rule.
* **Fabric API** - <https://github.com/FabricMC/fabric> - key bindings, client ticks, HUD and
  lifecycle events, plus access wideners.
* **Mod Menu** - <https://github.com/TerraformersMC/ModMenu> - the config button entrypoint.
* **guardian** - <https://github.com/Steve2001/minecraft-decompiled> - decompiled sources used to
  check `handleKeybinds`, `startAttack`, `startUseItem`, `missTime` and the GUI APIs per version.
* **六月の雨** - for providing the language model resources used to write and check this
  documentation.

If any project above requires a specific attribution form, that requirement wins; please open an
issue if something is missing.

## License

[Unlicense](LICENSE).

---

# 中文说明

**[English](#client-interactions)** | **中文**

> **阅读指引**
> 中文部分 3 137 字（不计空白），大约需要 7 分钟；英文部分 1 045 词（不计空格 5 496 字符），
> 大约需要 4 分钟，两部分合计约 11 分钟。完整的 [`LICENSE`](LICENSE) 为 1 212 字符
> （197 词、24 行）。
>
> 只想用模组的话，读 **使用** 和 **模式** 就够了；其余部分涵盖原版限制、构建与疑难排查。

一个客户端 Fabric 模组，用五种模式自动执行 Minecraft 的两种鼠标操作：**攻击**（左键）与
**使用**（右键），配置界面可从 Mod Menu 进入。

## 支持的版本

| Minecraft | Fabric Loader | Java |
|-----------|---------------|------|
| 1.19.4    | >= 0.14.21    | 17   |
| 1.20.1    | >= 0.16.10    | 17   |
| 1.21.1    | >= 0.15.11    | 21   |
| 1.21.10   | >= 0.17.0     | 21   |
| 1.21.11   | >= 0.17.3     | 21   |
| 26.3      | >= 0.19.3     | 25   |

请使用与游戏版本对应的 jar：`clientinteractions-<游戏版本>-1.0.0.jar`。
需要 [Fabric API](https://modrinth.com/mod/fabric-api)；[Mod Menu](https://modrinth.com/mod/modmenu)
是可选的，只用于添加配置按钮。

## 使用

按键（可在「控制」中改绑）：

| 默认键 | 作用 |
|--------|------|
| `R` | 切换攻击自动化 |
| `V` | 切换使用自动化 |
| `]` | 打开配置界面 |

按下切换键之前不会执行任何动作。活动中的通道显示在左上角，可用 **HUD** 开关隐藏。

## 模式

时间单位为游戏刻（1 刻 = 50 毫秒，每秒 20 刻）。两个通道各自独立设置模式与数值。

| 模式 | 行为 |
|------|------|
| 长按 | 保持按下直到再次切换关闭。连续挖掘、持续使用物品与原版按住一致。 |
| 快速连点 | 每刻注入 `每刻次数` 次点击（1-100，默认 1）。 |
| 周期点击 | 每隔 `间隔` 刻注入 `每刻次数` 次点击。 |
| 周期长按 | 每隔 `间隔` 刻按住 `按住` 刻。 |
| 周期连点 | 每隔 `间隔` 刻连发 `连发` 次点击，每次间隔一刻。 |

## 原版限制

模组走的是正常点击的代码路径，因此原版规则照常生效：

* 攻击受攻击冷却与十刻挥空计时限制。
* 按住使用键时受四刻使用延迟限制。
* 使用物品时不能攻击，挖掘时不能使用物品。
* 一刻只有一个准星目标，无法在同一刻攻击两个不同实体。

因此实际频率可能低于设定值：模组只是更频繁地请求，不会绕过这些检查。

## 界面打开时

所有模式下计时都继续。

* **攻击**会暂停，与原版一致：界面打开时原版会丢弃攻击输入。长按时模组只清除「界面已打开」
  这个标记，界面关闭后即可恢复。
* **使用**会继续工作（按你的要求）。这意味着背包打开时，正在运行的使用通道仍会作用于世界。

## 配置界面

用 `]` 或从 Mod Menu 打开。左栏选择每个通道的模式，右栏只显示该模式用到的数值。三个开关：
**自动化**（总开关，会释放按钮但保留计时）、**HUD**、**聊天消息**。

数值范围：`间隔` 1-200，`按住` 1-间隔，`连发` 1-100，`每刻次数` 1-100。

配置文件位于 `config/clientinteractions.json`，每次改动即写入。各受支持版本的格式相同，可互相
复制。缺失或非法的值会回退到默认值。

```json
{
  "enabled": true, "hud": true, "messages": true,
  "attack": { "mode": "periodic_hold", "interval": 20, "hold": 10, "perTick": 1, "burst": 3 },
  "use":    { "mode": "hold",          "interval": 20, "hold": 10, "perTick": 1, "burst": 3 }
}
```

模式值：`hold`、`fast_click`、`periodic_click`、`periodic_hold`、`periodic_burst`。

## 构建

Gradle 用 JDK 25 运行。游戏本身用其 Minecraft 版本所需的 Java 启动（17 / 21 / 25），按目标自动
选择，所以需要装好这些 JDK。

```bash
./gradlew build collectJars                    # 全部六个目标 -> build/libs
./gradlew :versions:1.21.11:build              # 单个目标
./gradlew :versions:1.21.11:runClientGameTest  # 游戏内测试（1.21.10、1.21.11、26.3）
```

`common/` 是与版本无关的计时引擎，不 import Minecraft。每个 `versions/<mc>/` 把它直接编译进
自己的 jar，因此发布的是单个 jar，没有嵌套 jar。`26.3` 不混淆，所以不使用映射，access widener
使用 `official` 命名空间。

`build` 还会运行 `verifyReleaseJar`，检查的是打包后的 jar 而不是开发产物：jar 必须在
`fabric.mod.json` 中声明 access widener、包含该文件，并使用游戏运行时所用的命名空间。开发环境
会掩盖这个问题，因为 Loom 不论是否声明都会把 widener 应用到 classpath 上，所以漏声明只会在玩家
那边失败。

## 测试

* 计时引擎上的 28 个单元测试（`./gradlew :common:test`）：各模式、数值钳制、周期中途切换、
  总开关、释放行为，以及 7000 刻的漂移检查。
* 1.21.10、1.21.11、26.3 有游戏内测试：启动真实客户端、创建世界，并断言长按、点击、每刻一步的
  计时、总开关、界面行为，以及配置界面能否正常渲染。
* 1.19.4、1.20.1、1.21.1 没有客户端 gametest API，通过编译、打包和启动游戏验证。

## 疑难排查

* **切换键没反应** — 按键被其他模组占用，或 **自动化** 总开关是关的。
* **打开背包后停了** — 攻击通道的预期行为，见上文。
* **比设定值慢** — 原版丢弃了多余动作，见「原版限制」。
* **版本不匹配** — jar 与游戏版本不对应。

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
