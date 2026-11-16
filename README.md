# Client Interactions

**English** | [中文](#中文说明)

> **Reading guide**
> This English section is 2 493 words (12 365 characters without spaces) and takes about 9 minutes.
> The Chinese section is 5 983 characters and takes about 11 minutes. The full [`LICENSE`](LICENSE) is
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

Use `clientinteractions-<mc version>-1.3.0.jar`. Requires
[Fabric API](https://modrinth.com/mod/fabric-api); [Mod Menu](https://modrinth.com/mod/modmenu) is
optional and only adds the config button.

## The eight features

Four behaviours for each button. Every feature is separate: it has its own switch, its own key binding
(unbound by default) and its own numbers.

| Feature | Behaviour | Acts when | Values |
|---------|-----------|-----------|--------|
| Hold | Keeps the button pressed until switched off | on its own | none |
| Fast click | Every click of that button becomes `Clicks` clicks, in the same tick | alongside the other features | clicks |
| Periodic click | Fires `Clicks` clicks over consecutive ticks every `Interval` | on its own | interval, clicks |
| Periodic hold | Holds for `Hold` ticks every `Interval` | on its own | interval, hold |

Grouped per button: **Attack** and **Use**. Times are game ticks (1 tick = 50 ms, 20 per second).

### Fast click is a multiplier, not a timer

Fast click never acts on its own. It multiplies the clicks its channel produces: every click that
channel makes in a tick becomes `Clicks` clicks, all in that same tick.

* **Your own clicks.** One physical press becomes `Clicks` clicks. Vanilla handles your press, the mod
  adds the remaining `Clicks - 1` in the same tick.
* **The other features of the same button.** It is *not* exclusive with them, because it does not drive
  the button itself - it only scales what they ask for. Periodic click configured for one click a tick
  performs `Clicks` clicks per tick while fast click is on.
* **Nothing at all** when the channel would not have clicked anyway, so it is not a clock.

What each click achieves is whatever vanilla grants it; see Vanilla limits. Several clicks in one tick
do not add up to several full-strength hits: the attack cooldown scales the damage of every hit after
the first in that tick.

### Why periodic click fires its clicks on separate ticks

The clicks are spread one per tick, not all in one tick. One tick has one crosshair target and one input
pass, so a same-tick burst cannot act as that many separate clicks: the use channel would place or
restart the same use twice, and the attack channel would swing into a cooldown the first click just
reset. Each click then achieves whatever vanilla grants it; see Vanilla limits.

## Mutual exclusion

Hold, periodic click and periodic hold each drive the button themselves, so activating one switches off
the other two of the same button - they would fight over it. Fast click is exempt in both directions: it
never switches anything off and is never switched off, because it only scales clicks. Attack and Use are
independent, so one of each can run at the same time.

## Defaults

All eight features start **off** and **unbound**. Their on/off state is not saved, so a restart never
starts clicking. Bind a key per feature in the config screen.

### A hold behaves like a real press-and-hold

A real mouse press is two things: the button goes down, and one click is queued. The queued click is what
produces the arm swing and starts a block breaking, so turning it on also emits that first press. Without
it a hold would set the flag and do nothing visible whenever the crosshair was on air or on a mob.

## Vanilla limits

The mod calls the same code paths a real click runs, so vanilla rules still apply:

* **Attack** is refused while the missed-swing timer is running: in survival, swinging at air sets it to
  10 ticks, so air swings really run at one per 10 ticks; creative never sets it. The attack cooldown
  never refuses a hit - it scales each hit's damage, so rapid attacks land at the full rate with reduced
  damage, exactly what a player clicking that fast gets.
* **Use** arms the vanilla 4 tick use delay on every attempt. A held use button therefore attempts once
  every 4 ticks; discrete clicks are processed like the real clicks they imitate, and vanilla has no
  per-click delay for those. A use attempt is refused while mining, and block breaking pauses while an
  item is being used.
* "Hands busy" - paddling a boat - blocks both buttons.
* One tick has one crosshair target, so a tick cannot act on two different things.

The mod asks; it does not bypass these checks.

## With a screen open

Timers keep running in every feature, and the use channel has always kept acting with a screen open: a
running use feature still acts on the world while your inventory is open.

The attack channel follows vanilla by default: a screen makes vanilla discard attack input, so attack
clicks are dropped and a hold resumes when the screen closes. The config screen has a **Continue in
GUI** switch for the other behaviour. With it on, all four attack behaviours keep running while any
screen is open - chat, the inventory, a container:

* **Clicks** (fast click, periodic click) run through the same entry points a real click uses.
* **Holds** (hold, periodic hold) keep the button down. Vanilla clears every key flag when a screen
  opens and its input loop does not run to set them again, so the mod re-asserts the flag and drives the
  work itself: it pumps the destroy loop for the block under the crosshair, exactly as a held button
  does in the world.

A screen interrupts the *player's* input, not the automation. Nothing here makes a hotkey or a mouse
press work while a GUI is up - a running feature keeps running, it does not start from inside a GUI.

The switch is **on** by default, so a running feature does not stop when you open your inventory or the
chat bar. Turn it off to restore the vanilla rule. While it is on the crosshair target does keep
receiving attacks while you browse your inventory.

## Config screen

Open it from Mod Menu, or bind a key for it with the **Key** button on the config screen (no key is bound
by default). There is one page per button, and the
page switch is the top right button, so the four features on screen are exactly the ones that exclude
each other.

Each row is one feature: the name toggles it, the key button next to it rebinds it, and the labelled
fields hold its numbers. The row widths come from one width budget, so nothing can run past the edge, and
the row spacing shrinks on a short window so the last row never sits under Done.

Numbers are typed into the field. The field keeps what you typed and the stored value is clamped to the
engine's range; if clamping one number also changed its partner (a hold cannot be longer than its
interval), the partner's field is updated to what is actually stored.

### Combinations and mouse buttons

A hotkey is a **set of keys held together**. Any keyboard key or any mouse button (left, right, middle,
4-8) may take part, in any mix: `Ctrl+R`, `Ctrl+Shift+Alt+R`, `A+C` and `Ctrl+Left Click` are all the
same kind of binding. Modifiers are not special, they are simply keys.

Open the key button and press the keys in any order: the capture starts empty, and **Done** stores
exactly what was pressed, replacing the saved binding rather than extending it. Nothing is committed
until Done, so multi-key combinations like `A+C` can be built; **Undo** removes the last key and
**Clear** empties the set. The screen shows the saved binding and the one being built. **Enter** also
confirms; **Esc** clears the keys captured so far, and with nothing captured it confirms - so pressing
it on a freshly opened screen clears the binding. Use **Cancel** to leave without changing anything.

Matching rules:

* Every key of the binding must be held.
* Holding a Ctrl/Shift/Alt/Win that is **not** part of the binding blocks it, so `R` does not fire while
  Shift is down.
* Other ordinary keys are ignored, since you are always holding something (W, space, ...).

### Priority between overlapping bindings

If one feature owns `A+C` and another owns plain `C`, pressing both keys runs **only** the `A+C`
feature. The longer combination takes precedence, and the shorter one works again once the longer is
released. This is what keeps a single key usable both alone and as part of a combination.

The config-opening combination follows the same rule, and wins an exact tie against a feature, so one
press never both opens the screen and toggles something.

### A bound key stops doing its ordinary job

While a combination is held down, its keys no longer perform their normal game action. Binding `Shift+1`
runs the feature and does **not** switch to hotbar slot 1; the same holds for `Q`, `E`, `F` and the rest.
Pressing `1` on its own still selects the slot, because only a complete combination is claimed.

This also holds for a press that is released again before the next tick: no tick ever saw the key as
down, and the press completes the combination from its queued click, which is then drained instead of
acting. What cannot be undone is order: pressing `1` first and `Shift` a tick later means the `1` acted
on its own - the mod cannot know `Shift` was coming.

The same applies to the mouse buttons. With `Shift+Left Click` bound, pressing that combination switches
the feature and does **not** also punch; a plain left click still attacks.

Every key of the combination loses its ordinary job for as long as the combination is held -
movement, sneak and sprint included: `Shift+W` stops walking while it is held, and walks again the
moment it breaks. Keys used on their own keep working: `W` alone still walks, `Shift` alone still
sneaks, `1` alone still selects the slot. The attack and use buttons are only left standing while the
mod is driving them itself, which is what lets a feature bound to `Shift+Left Click` keep holding the
button after it is switched on.

A rebind takes effect immediately, without restarting the game.

The mod adds its own key entries to the controls screen so the bindings have names, but those entries stay
**unbound**: the combination is matched from the keys themselves. Writing a key into them would take that
key away from whatever vanilla action already owns it, so a hotkey on `W` would stop you walking.

### Master switch

The master switch suppresses every action. Pressing a hotkey while it is off is therefore refused and says
so, instead of switching the feature on and doing nothing.

### Numbers

Each number is a text field: click it and type the value, instead of stepping through presets. Only digits
are accepted and out-of-range input is clamped to the same limits the engine enforces.

Config lives in `config/clientinteractions.json`, one block per feature plus its key. Missing or invalid
values fall back to defaults. The format is identical on every supported version.

```json
{
  "enabled": true, "hud": true, "messages": true,
  "continueOnScreen": true,
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

* 66 unit tests: every behaviour, clamping, mutual exclusion per button, channel independence, the
  master switch, starting with the master switch off, combination matching (sets of keys, mouse buttons,
  priority between overlapping bindings, the config key winning a tie, an extra modifier blocking a
  binding), which keys vanilla loses, fast click being a paced multiplier, the periodic-click tick spread,
  and a 7000 tick drift check.
* 1.21.10, 1.21.11 and 26.3 have in-game tests that boot a real client, create a world, and assert
  holds, clicks, one-tick-per-tick timing, mutual exclusion, the master switch, screen behaviour,
  combination matching, that a multi-key combination is captured whole, that it fires on a real press
  without toggling itself back off, that an extra modifier blocks a plain binding, that a longer
  combination blocks a shorter one it contains, that a
  bound key no longer performs its ordinary game action while the key alone still does, that a mouse-button
  combination does not also attack, that a hotkey is refused while the master switch is off, that a forced hold swings the arm like a real press, that a hold
  resumes after a screen closes, that a press released before the next tick still completes its
  combination without the queued click reaching vanilla, that a movement key bound into a combination
  stops walking while it is held and walks again once it breaks, that attack clicks, a held attack and
  the chat bar keep acting with a screen open while the continue-on-screen option is on, that a rebind fires without
  a restart, that a mouse button binds too, that
  a typed number reaches the engine and is clamped, that fast click injects the extra clicks for a real
  press and stops after them, and that both screens draw visible text with no widget overlapping another or
  leaving the screen at a small gui.
* 1.19.4, 1.20.1 and 1.21.1 have no client gametest API; they are verified by compiling, packaging and
  starting the game.

## Troubleshooting

* **A feature does nothing** - its key is unbound, the **Automation** master switch is off (a hotkey is
  refused and says so), or another feature of the same button is running (they exclude each other).
* **A bound key does nothing** - holding a Ctrl/Shift/Alt/Win that is not part of the binding blocks it,
  deliberately.
* **Stops when I open my inventory** - expected for attack while **Continue in GUI** is off; see above.
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
> 中文部分 5 983 字（不计空白），大约需要 11 分钟；英文部分 2 493 词（不计空格 12 365 字符），
> 大约需要 9 分钟。完整的 [`LICENSE`](LICENSE) 为 1 212 字符（197 词）。

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

请使用 `clientinteractions-<游戏版本>-1.3.0.jar`。需要
[Fabric API](https://modrinth.com/mod/fabric-api)；[Mod Menu](https://modrinth.com/mod/modmenu) 可选，
只用于添加配置按钮。

## 八个功能

每个按键四种行为，共八个独立功能。

| 功能 | 行为 | 触发时机 | 参数 |
|------|------|----------|------|
| 长按 | 保持按下直到关闭 | 自动 | 无 |
| 快速连点 | 该键的每次点击都变成 `次数` 次，且在同一刻内 | 与同按键的其它功能并行 | 次数 |
| 周期点击 | 每隔 `间隔` 刻，在连续刻上打出 `次数` 次点击 | 自动 | 间隔、次数 |
| 周期长按 | 每隔 `间隔` 刻按住 `按住` 刻 | 自动 | 间隔、按住 |

按按键分为两组：**攻击**与**使用**。时间单位为游戏刻（1 刻 = 50 毫秒，每秒 20 刻）。

### 快速连点是倍率，不是定时器

快速连点从不自行动作。它放大该通道产生的点击：该通道在某一刻发出的每次点击都变成 `次数` 次，
全部落在同一刻内。

* **你自己的点击**：一次物理按下变成 `次数` 次点击。你的按下由原版处理，模组在同一刻补上其余
  `次数 - 1` 次。
* **同一按键的其它功能**：它与其他功能**不互斥**，因为它不驱动按键本身，只放大对方请求的次数。
  周期点击配置为每刻 1 次，开启快速连点后每刻执行 `次数` 次。
* **什么都不做**：该通道本来这一刻不点击时它也不点击，所以它不是时钟。

每次点击实际达成多少由原版规则决定，见「原版限制」。同一刻内的多次点击并不等于多次满伤害命中：
攻击冷却会缩放同一刻内第一次之后的每次攻击伤害。

### 周期点击为什么把点击分散到不同刻

这些点击是一刻一次、连续打出的，而不是全挤在同一刻。一刻只有一个准星目标、一次输入处理，
同一刻的连发不可能成为多次独立的点击：使用通道会再放置一个方块或重启同一次使用，攻击通道会
挥进第一次点击刚重置的攻击冷却。每次点击实际达成多少，由原版规则决定，见「原版限制」。

## 互斥

长按、周期点击、周期长按各自驱动按键本身，所以开启其中一个会关闭同一按键的另外两个——它们会
争抢同一个按键。快速连点在两个方向上都是例外：它不会关闭任何功能，也不会被其它功能关闭，因为
它只放大点击次数。攻击与使用互相独立，各自可以同时开一个。

## 默认状态

八个功能默认**全部关闭**且**全部无快捷键**。功能的开关状态不会被保存，所以重启游戏不会自动开始
点击。快捷键请在配置界面里逐个绑定。

### 长按和真实按下保持一致

真实的一次鼠标按下包含两件事：按键落下，以及排入一次点击。产生手臂挥动、开始挖掘方块的是那次
点击，所以开启长按时也会补上这一次按下。否则长按只是把按键标记设为按下，当准星对着空气或生物时
完全看不到任何反应。

## 原版限制

模组调用的是真实点击所走的同一套代码路径，所以原版规则照常生效：

* **攻击**在挥空计时运行期间会被拒绝：生存模式下向空气挥击会把计时设为十刻，所以挥空的真实速率
  是每十刻一次；创造模式不会设置它。攻击冷却不会拒绝任何一次攻击——它只缩放每次攻击的伤害，
  所以快速攻击以完整速率命中、伤害打折，与你亲手快速点击的效果一致。
* **使用**每次尝试都会装填原版的四刻使用延迟。按住使用键因此每四刻尝试一次；离散点击按真实点击
  处理，原版对它们没有每次点击的延迟。挖掘期间会拒绝使用尝试，使用物品期间挖掘暂停。
* 「双手被占用」（划船）会屏蔽两个按键。
* 一刻只有一个准星目标，无法在同一刻作用于两个不同目标。

模组只是发出请求，不会绕过这些检查。

## 界面打开时

所有功能的计时都继续。使用通道一直会在界面打开时继续作用于世界：背包打开时，正在运行的使用
功能仍会生效。

攻击通道默认与原版一致：界面打开时原版会丢弃攻击输入，所以攻击点击被丢弃，长按在界面关闭后
恢复。配置界面里的 **Continue in GUI** 开关提供另一种行为：开启后四种攻击功能在任意界面
（聊天栏、背包、容器）打开期间都继续运行：

* **点击类**（快速连点、周期点击）走真实点击所用的同一入口。
* **长按类**（长按、周期长按）保持按键按下。原版在界面打开时会清空所有按键状态，而它的输入循环
  在该状态下不运行、不会重新置位，所以模组自己重新置位并驱动实际动作：为准星下的方块泵动破坏
  循环，与世界中按住按键完全一致。

界面打断的是**玩家**的输入，不是自动化。这里不会让快捷键或鼠标按在界面里生效——已在运行的功能
继续运行，但功能不会从界面内部启动。

该开关**默认开启**，所以打开背包或聊天栏时已在运行的功能不会停。关掉它即可回到原版规则。开启
期间，浏览背包时准星目标仍会持续受到攻击。

## 配置界面

从 Mod Menu 打开，或用配置界面上的 **Key** 按钮给它绑一个键（默认不绑定）。每个按键一页，
翻页按钮在右上角，所以
屏幕上的四个功能正好就是互斥的那四个。

每一行是一个功能：点名字切换开关，点它旁边的按键按钮重新绑定，带标签的输入框填写
数值。各列宽度由同一份宽度预算分配，所以不会跑出屏幕；窗口矮时行距会收紧，
最后一行不会压在 Done 上。

数值直接在输入框里输入。输入框保留你键入的内容，实际保存的值会被钳制到引擎的上下限；如果
钳制某个数值同时改变了同一行的另一个数值（按住不能长于间隔），那一格会更新为真正保存的值。

### 组合键与鼠标键

快捷键是一组**同时按住**的键。任意键盘键或任意鼠标键（左键、右键、中键、4 到 8）
都能参与，可以随意混搭：`Ctrl+R`、`Ctrl+Shift+Alt+R`、`A+C`、`Ctrl+鼠标左键`
都是同一类绑定。修饰键并不特殊，它们只是普通的键。

点开按键按钮，按任意顺序按下这些键：录入从空白开始，按下 **Done** 时会按已按下的键整体
替换已保存的绑定，而不是在原绑定上追加。按下 Done 之前不会提交，所以 `A+C` 这类多键组合
可以完整录入；**Undo** 删掉最后一个键，**Clear** 清空。界面会同时显示已保存的绑定和正在
录入的绑定。**Enter** 同样确认；**Esc** 在已录入按键时清空这些按键，未录入任何按键时为
确认——所以在刚打开的界面上按 Esc 会解除绑定。不想改动时请用 **Cancel** 退出。

匹配规则：

* 绑定里的每个键都必须按住。
* 按住不属于该绑定的 Ctrl/Shift/Alt/Win 会使其失效，所以按着 Shift 时 `R` 不会触发。
* 其它普通键会被忽略，因为你手上总按着点什么（W、空格等）。

### 重叠绑定之间的优先级

如果一个功能占用了 `A+C`，另一个占用了单独的 `C`，那么同时按下这两个键时**只会**执行
`A+C` 的功能。长组合优先，松开后短组合又能正常工作。这样同一个键既能单独用，也能作为
组合的一部分。

打开配置界面的组合遵循同样的规则，并且在长度相同时优先于功能快捷键，所以一次按键不会既
打开界面又切换功能。

### 绑定的键不再执行原版动作

组合键按住期间，它的键不再执行原本的游戏动作。绑定 `Shift+1` 会执行功能，**不会**切换到第 1 格
快捷栏；`Q`、`E`、`F` 等键同理。单独按 `1` 仍然会切换快捷栏，因为只有完整组合才会被接管。

在下一刻开始前就已松开的短按也一样：没有任何一刻看到该键处于按下状态，这次按下会凭其排入队列
的点击完成组合，队列点击被排空而不会执行原版动作。无法挽回的是顺序：先按 `1`、一刻之后才按
`Shift`，那么 `1` 已经单独生效——模组不可能知道 `Shift` 随后就会按下。

鼠标键同理：绑定 `Shift+左键` 时，按下这个组合会切换功能，**不会**顺带打出一拳；单独按左键仍然
正常攻击。

组合包含的每个键在按住期间都停止本职——移动、潜行、疾跑也不例外：`Shift+W` 按住期间不再前进，
组合断开后立即恢复行走。单独使用时一切照旧：单独 `W` 仍然行走，单独 `Shift` 仍然潜行，单独 `1`
仍然选择快捷栏。当模组自己在驱动攻击键或使用键时，不会去清它们的状态——正因如此，绑定到
`Shift+左键` 的功能开启后才能继续按住按钮。

改键无需重启即刻生效。

模组会在按键设置里加入自己的条目，让绑定有名字，但这些条目**保持未绑定**：组合键是由键本身
匹配的。把键写进原版映射会从本来使用该键的原版动作手里抢走它——例如快捷键绑到 `W` 就会让你
无法行走。

### 总开关

总开关会压制所有动作。因此在它关闭时按快捷键会被拒绝并说明原因，而不是把功能打开却什么都不做。

### 数值

每个数值都是输入框：点进去直接输入，不必在预设档位之间循环。只接受数字，超出范围
的输入会被钳制到引擎同一套上下限。

配置文件位于 `config/clientinteractions.json`，每个功能一个区块，连同它的快捷键。缺失或非法值
会回退到默认值。各受支持版本的格式完全相同。

```json
{
  "enabled": true, "hud": true, "messages": true,
  "continueOnScreen": true,
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

* 66 个单元测试：每种行为、数值钳制、同按键互斥、双通道独立、总开关、以关闭总开关启动、
  组合键匹配（键集合、鼠标键、重叠绑定之间的优先级、配置键在长度相同时优先、多余的修饰键会
  屏蔽绑定）、哪些键会被接管、快速连点的同刻倍率语义与并行运行、周期点击的
  跨刻分布，以及 7000 刻的漂移检查。
* 1.21.10、1.21.11、26.3 有游戏内测试：启动真实客户端、创建世界，断言长按、点击、每刻一步的
  计时、互斥、总开关、界面行为、组合键匹配、多键组合被完整捕获、按下后不会自我抵消、
  多余的修饰键会屏蔽普通绑定、
  长组合会屏蔽包含的短绑定、绑定的组合键不再触发原版动作而单键仍可单独使用、组合中的移动键
  在按住期间停止前进且组合断开后恢复、含鼠标键的组合
  不会顺带攻击、总开关关闭时
  快捷键会被拒绝、长按会像真实按下一样挥动手臂、界面关闭后长按会恢复、在下一刻前松开的短按仍能
  完成组合且其队列点击不会漏给原版、界面内继续操作开启时攻击点击、长按与聊天栏在界面打开期间
  持续生效、改键无需重启即生效、
  鼠标键可绑定、输入的数值会进入引擎并被钳制、快速连点的同一刻内倍率（玩家的一次真实按下与
  周期点击的一刻各自注入完整的次数），
  以及在较小 GUI 下两个界面都能画出可见文字、且没有按钮互相重叠或跑出屏幕。
* 1.19.4、1.20.1、1.21.1 没有客户端 gametest API，通过编译、打包和启动游戏验证。

## 疑难排查

* **某个功能没反应** — 快捷键未绑定，或 **自动化** 总开关关闭（此时按快捷键会被拒绝并说明），
  或同一按键上已有另一个**驱动按键**的功能在运行（长按、周期点击、周期长按互斥；快速连点不在此列）。
* **绑了键却没反应** — 按住了不属于该绑定的 Ctrl/Shift/Alt/Win 会使其失效，这是有意的。
* **打开背包后停了** — 攻击功能在 **Continue in GUI** 关闭时的预期行为，见上文。
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
