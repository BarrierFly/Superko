# Superko (禁全同)

**English** | [中文](#中文)

A server-side Fabric mod that stops instantaneous block-update loops ("global sameness" loops) instead of letting them crash the server (pre-1.19) or silently break contraptions (1.19+).

## The problem

Block changes in Minecraft propagate through instantaneous neighbor updates. A contraption whose update chain reaches a configuration it has seen before in the same chain — with no randomness involved — repeats forever:

- Before 1.19 the recursion ends in a `StackOverflowError` crash.
- Since 1.19 the vanilla manual update stack silently **skips** all further updates once 512 chained updates are reached — the machine just quietly stops working.

## What this mod does

Named after the *superko* (禁全同, "forbidden to repeat the whole board position") rule of Go. During an instantaneous update chain, whenever a block tries to change state, the mod computes the post-change configuration of **every block the chain has touched so far**. The change is rejected — before it is written to the world, so none of its updates are ever emitted — if and only if:

1. that configuration is **exactly identical** to an earlier moment of the same chain (same set of touched blocks and same states — never compared on the intersection), **and**
2. the very **same action** (same block, same update context — self / neighbor / shape-face — same flags, same resulting state) produced both configurations.

After a rejection, further identical attempts from that block in the same chain are rejected outright. Other blocks and other actions of the same block keep working. Everything is forgotten when the chain ends.

**One chain each:** per handled player packet · per executed scheduled tick (`tickBlock`/`tickFluid`) · per executed block event · per block entity per tick · per entity per tick (passenger ticks nest inside their vehicle's chain).

**Not touched:** anything with a delay — scheduled tick queues, block event queues (pistons, observers), clocks spread over multiple ticks. Delayed loops are normal redstone behavior and stay exactly vanilla.

## How it works (internals)

- **Judgment** happens at the HEAD of `Level.setBlock(BlockPos, BlockState, int, int)` and cancels the call there — a rejected change is written to no chunk and therefore emits no updates at all.
- **Recording** happens at the `getBlockState(pos)` call vanilla performs right after the chunk write on the success path (before any update dispatch), using a plain composable `@Inject` — no redirects, no conflicts with other mods' `setBlock` hooks. Rejected calls never reach it.
- **Update contexts** (self / neighbor / shape-face) are tagged on the vanilla updater classes — both the 1.19+ manual stack (`CollectingNeighborUpdater`) and the instant/recursive one (`InstantNeighborUpdater`, also used client-side and by TIS-Addition's `instantBlockUpdaterReintroduced`).
- **Snapshots** are compared via a Zobrist-style rolling hash first, and every recorded moment is indexed by its hash, so a candidate is matched in O(1) rather than by scanning the chain history. A moment's configuration is reconstructed on demand from per-position write histories, so recording a change costs a few words instead of a full configuration copy. Moments store the action that produced them, which is what makes "same configuration, same action" decidable.

## Known limitations (read this)

- **Strict rule.** Like in Go, a chain that *ever* revisits a configuration gets clipped, even if it would have diverged one beat later. In rare cases this can stop a contraption that vanilla would have let converge.
- **Performance caps.** 65536 touched blocks and 65536 recorded moments per chain, plus a budget for full configuration comparisons; beyond any of them the rest of the chain is passed through unjudged (with a console warning, and `/superko status` counts "chains given up"). Very large loop structures may still not be protected, and update-storm-scale chains are beyond this mod.
- A rejected `setBlock` returns `false`, exactly like a failed placement.
- Only block states are compared; entity changes are not part of the snapshot.
- `setBlock` calls with no update flags (e.g. structure placement, flags `2|16`) are recorded but never rejected — they cannot cascade.

## Commands & config

`/superko` (permission level 2):

| Command | Effect |
|---|---|
| `/superko on` / `off` | enable / disable |
| `/superko status` | current state, plus counters: chains started / judged / recorded / rejected setBlocks, and the last ended chain's touched/snapshot counts (they grow while a chain records) |
| `/superko log <none\|console\|broadcast\|debug>` | rejected actions go to the server log (default `console`); `broadcast` also sends them to online operators; `debug` additionally traces every judged and recorded `setBlock` |
| `/superko exempt add\|remove <block>` / `list` | blocks that are never judged |

Settings persist in `config/superko.json`. Default: **enabled**, console logging, no exempt blocks.

### Reading the log

A rejection looks like this:

```
[Superko] Rejected setBlock at (-88, 56, 213) [minecraft:overworld] during player packet chain:
ctx=self, flags=11, newStateId=4272 — would recreate the configuration of moment #37 (same action);
superko (global sameness) detected.
```

At the `debug` level every judged change is traced (`setBlock (x,y,z) old->new ctx=... flags=... during ... chain: judge: pass / judge: reject (superko, moment #N) / judge: record-only (no update flags)`) together with the corresponding `record (x,y,z) -> stateId ...` line. Quick check that the mod sees your contraption at all: `/superko status` and watch `judged/recorded` grow while it runs.

## Compatibility

- Server-side only. Clients do not need it; single player (integrated server) works.
- **Carpet-TIS-Addition**: fully compatible with `instantBlockUpdaterReintroduced` — enabling it is *recommended*, since superko judges at `Level.setBlock` (which both the vanilla manual stack and the instant updater use) and tags update contexts on both generations of updater. `yeetUpdateSuppressionCrash` and similar update-suppression fixes are orthogonal and can be combined freely.
- **carpet**: coexists with carpet's own `Level.setBlock` mixins (e.g. `fillUpdates`) — superko only uses injectable hook points, never redirects.
- No dependency on Fabric API or carpet.

## Building

Requires Gradle 9+ (the wrapper is included; its distribution URL points at the Tencent mirror — switch `gradle/wrapper/gradle-wrapper.properties` back to `services.gradle.org` if you build elsewhere). No JDK 17 toolchain needed for building; the mod targets Java 17.

```
gradlew build     # jar: versions/1.19.4/build/libs/superko-<version>.jar
gradlew test      # pure-JVM unit tests of the judgment core
```

Version management is set up with [Stonecutter](https://stonecutter.kikugie.dev/); 1.19.4 is the active node, with 1.16.5/1.17.1/1.18.2 reserved (the core is version-independent; only the mixins are version-specific).

## License

[WTFPL](LICENSE) — do what the fuck you want to.

---

# 中文

一个服务端 Fabric Mod：不再让瞬时方块更新中的"全局同型"（全同）死循环崩服（1.19 之前）或悄悄毁掉装置（1.19 起），而是直接掐断。

## 问题背景

方块变化会通过邻居更新瞬时级联传播。如果更新链走到某一步时，链内所有方块的状态与链中先前某一时刻完全一致，而瞬时行为又没有随机性，装置就会以完全相同的模式无限循环：

- 1.19 之前：递归调用栈溢出，`StackOverflowError` 崩服；
- 1.19 起：原版手工更新栈在累计 512 次链式更新后**静默跳略**后续更新，装置默默坏掉。

## 工作原理

名字来自围棋的"禁全同"规则。在一个瞬时更新链内，每当某方块尝试改变状态，Mod 计算**改动后链内已触及的所有方块**的配置；当且仅当满足以下两条时拒绝执行（在写入世界之前拦截，因此其引发的更新天然不会放出）：

1. 该配置与链中**先前某一时刻完全相同**（触及方块集合与各状态都一致——绝不在交集上比较），**且**
2. 两次得到相同配置的是**同一个动作**（同一个方块、同样的更新上下文——自身逻辑/邻居更新/形状更新（含面）——同样的 flags、同样的目标状态）。

拒绝之后，链内该方块的相同动作直接拒绝；其他方块、该方块的其他动作照常执行。链结束即丢弃全部记录。

**链的划分（每类各一条链）：** 每个玩家包的处理 · 每条计划刻执行（`tickBlock`/`tickFluid`）· 每条方块事件执行 · 每个方块实体每刻 tick · 每个实体每刻 tick（乘客实体嵌套在载具链内）。

**不干预：** 一切跨刻的延迟行为——计划刻队列、方块事件队列（活塞、观察者）、跨多个游戏刻的红石钟。有延时的循环是正常现象，与原版完全一致。

## 内部实现

- **判定**挂在 `Level.setBlock(BlockPos, BlockState, int, int)` 的 HEAD 并在此取消——被拒绝的改动不会写入任何区块，因此其更新天然不会放出。
- **记录**挂在原版在区块写入成功之后、更新派发之前的那次 `getBlockState(pos)` 调用上，用的是可叠加的普通 `@Inject`——不用 @Redirect，不会和其它 mod 的 setBlock 钩子冲突；被拒绝的调用根本走不到这里。
- **更新上下文**（自身逻辑/邻居更新/形状更新含面）打标在原版更新器类上——1.19+ 的手工栈（`CollectingNeighborUpdater`）与即时递归更新器（`InstantNeighborUpdater`，客户端以及 TIS-Addition 的 `instantBlockUpdaterReintroduced` 都在用）两代全覆盖。
- **快照比较**先用 Zobrist 风格滚动哈希排除，且每个时刻都按哈希建索引——候选构型 O(1) 命中，不再线性扫描链内历史；命中后用**每方块写入历史**按需重建该时刻的构型，因此记录一次改动只需几个字，而不是整份构型拷贝。每个时刻都记录产生它的动作，"同构型 + 同动作"因此可判定。

## 已知限制（务必阅读）

- **严格判定。** 与围棋规则一样：链内只要出现过全同即拒绝该次动作，即使之后本可发散。极端情况下可能干预原版本可正常收敛的装置。
- **性能上限。** 每条链最多记录 65536 个触及方块与 65536 个时刻，另有全量构型比对的预算；任一超限后本链放行不再判定（控制台警告一次，`/superko status` 里的 "chains given up" 计数可见）。超大死循环结构仍可能防不住，更新量极大时本 Mod 自身也可能先出问题。
- 被拒绝的 `setBlock` 返回 `false`，与放置失败的表现一致。
- 只比较方块状态；实体的创建/移除/更改不进快照。
- 不带更新 flag 的 `setBlock`（如结构放置，flags `2|16`）只记录、不拒绝——它们无法形成瞬时循环。

## 命令与配置

`/superko`（权限等级 2）：

| 命令 | 作用 |
|---|---|
| `/superko on` / `off` | 开启 / 关闭 |
| `/superko status` | 当前状态与计数：链数 / 判定数 / 落盘数 / 拒绝数，以及上一条链的触及方块与快照数（链在记录时它们会增长） |
| `/superko log <none\|console\|broadcast\|debug>` | 拦截记录写入服务器日志（默认 `console` 仅控制台）；`broadcast` 同时发给在线 OP；`debug` 会把每个判定与落盘的 `setBlock` 逐条跟踪 |
| `/superko exempt add\|remove <方块>` / `list` | 永不判定的方块白名单 |

配置保存在 `config/superko.json`。默认：**开启**、仅控制台日志、白名单为空。

### 怎么读日志

一次拦截长这样：

```
[Superko] Rejected setBlock at (-88, 56, 213) [minecraft:overworld] during player packet chain:
ctx=self, flags=11, newStateId=4272 — would recreate the configuration of moment #37 (same action);
superko (global sameness) detected.
```

`debug` 级别下，每个判定的改动都会逐条跟踪（`setBlock (x,y,z) old->new ctx=... flags=... during ... chain: judge: pass / judge: reject (superko, moment #N) / judge: record-only (no update flags)`），并伴随对应的 `record (x,y,z) -> stateId ...` 行。想快速确认 mod 是否"看见"了你的装置：跑 `/superko status`，看 `judged/recorded` 是否随装置运行增长。

## 兼容性

- 仅服务端逻辑，客户端无需安装；单机（集成服务器）同样生效。
- **Carpet-TIS-Addition**：与 `instantBlockUpdaterReintroduced` 完全兼容，并且**推荐配合开启**——本 Mod 在 `Level.setBlock`（两代更新器的共同汇点）判定，并为两代更新器都打更新上下文标记。`yeetUpdateSuppressionCrash` 等更新抑制修复与本 Mod 目标正交，可随意叠加。
- **carpet**：与 carpet 自身对 `Level.setBlock` 的 mixin（如 `fillUpdates`）共存——本 Mod 只用可叠加的注入点，从不用 @Redirect。
- 不依赖 Fabric API 与 carpet。

## 构建

需要 Gradle 9+（已带 wrapper；发行包地址用的是腾讯镜像——在其它网络环境构建时把 `gradle/wrapper/gradle-wrapper.properties` 换回 `services.gradle.org` 即可）。构建无需 JDK 17 工具链，mod 目标为 Java 17。

```
gradlew build     # 产物：versions/1.19.4/build/libs/superko-<version>.jar
gradlew test      # 判定核心的纯 JVM 单元测试
```

多版本管理基于 [Stonecutter](https://stonecutter.kikugie.dev/)：当前启用 1.19.4 节点，1.16.5/1.17.1/1.18.2 为预留（core 与版本无关，只有 mixin 是版本相关的）。

## 许可证

[WTFPL](LICENSE)——想怎么用就怎么用。
