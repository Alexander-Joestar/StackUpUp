# Changelog

中文在前，英文在后，每个版本内分块；英文块以 `### English` 标题引出，其内部子节使用四级标题。

## 0.2.5.1

这是一版修复：上一版「让容器自己报出真实容量」的做法漏掉了把 `64` 写在接口默认方法与抽象基类里的那一类容器，它们仍然卡在 64；同一条路径上还修掉了一处「扩容看着生效、实际会把超量部分截断丢掉」的问题。

### 核心变化

- 现在大多数模组容器都能接受超过 64 的物品堆叠，包括 RFTools 的 Modular Storage、Storage Scanner 与各类机器。
- 原版运输矿车（箱子矿车、漏斗矿车）现在也能突破 64：它们的容量写在抽象基类里，此前没有生效。
- 修复 RFTools Storage Scanner 输入槽的吞物品问题：当扫描仪没有可路由的存储时，放入超过 64 的物品会在下一帧被截断回 64，超出的部分直接消失。
- 物品本身的堆叠上限仍由规则文件决定，item 级粒度不变，规则求值没有改动。
- 对外显示的容量仍然不会超过对应写入路径的真实容量，所以这次放开不会让容器放入多吞物品（唯一的例外见下方黑名单说明）。

### 兼容

- RFTools 只新增一个安全补丁，打在它唯一会主动截断余量的位置上；其余容器靠公共的容量读侧改写自动覆盖，没有逐类登记。
- 少数确实冲突的模组仍可用 `compat.inventoryStackLimitBlacklist.modIds` 应急绕行；开关与名单的行为不变，改变后仍需重启。
- 黑名单只让容量扩展跳过所列模组，不会关闭它们的其他兼容配置，也不会全局关掉其他模组。
- 容量「广告面」的一个已知边界：`getSlotLimit` 只影响显示与余量判断，真实写入读的是库存自己的上限。若某个 wrapper 与它包装的库存分属两个 Mod（McJtyLib 与 RFTools 就是这种情况），只把库存所在的 Mod 加进黑名单会让 wrapper 继续广告扩容后的值，此时显示的余量大于实际能放进去的数量——不会丢物，只是多几次无用的插入尝试。需要时把两个 Mod 都加进名单。

### 内部清理

- 删掉了 Mystical Agriculture 精华再处理器的那条专用补丁：它的目标基类本来就由通用容量补丁覆盖，那条补丁只是重复登记。**6 台机器的行为不变**（0.2.5 里的扩容能力保留），此处不涉及任何功能增减。

### English

This is a fix release: the previous "let containers report their own real capacity" approach missed the containers that write `64` inside an interface default method or an abstract base class, so those still clamped at 64; the same path also hid a truncation bug where the expansion looked active but the leftover was cut off and lost.

#### Core changes

- Most mod containers now accept stacks larger than 64, including RFTools Modular Storage, the Storage Scanner and its machines.
- Vanilla container minecarts (chest and hopper variants) can now go beyond 64 as well: their capacity is declared in an abstract base class and previously had no effect.
- Fixed item loss in the RFTools Storage Scanner input slot: with no routable storage available, a stack above 64 was truncated back to 64 on the next tick and the excess disappeared.
- Item stack limits are still decided by the rule files, item-level granularity is unchanged, and rule evaluation was not touched.
- The advertised capacity still never exceeds the real capacity of the matching write path, so this widening does not make containers swallow leftover items (the one exception is described under Compatibility).

#### Compatibility

- RFTools gets exactly one safety patch, at the only place where it actively truncates the leftover; every other container follows from the shared capacity-read-side rewrite, with no per-class registration.
- A mod that genuinely conflicts can still be added to `compat.inventoryStackLimitBlacklist.modIds` as an emergency bypass; the switch and the list behave as before and still require a restart.
- The blacklist only makes capacity expansion skip the listed mods; it does not disable their other compatibility configs and does not switch off other mods globally.
- One known edge on the advertised side: `getSlotLimit` only affects display and free-space checks, while real writes read the inventory's own limit. When a wrapper and the inventory it wraps belong to two different mods (McJtyLib and RFTools are such a pair), adding only the inventory's mod to the blacklist leaves the wrapper advertising the expanded value, so the reported free space is larger than what actually fits — no items are lost, it just costs a few pointless insert attempts. Add both mods to the list when that matters.

#### Internal cleanup

- Removed the dedicated patch for the Mystical Agriculture essence reprocessors: their base class is already covered by the general capacity patch, so that entry only registered the same target twice. **The six machines behave exactly as before** — the expanded capacity from 0.2.5 is kept, and nothing is gained or lost here.

## 0.2.5

这一版改变了「让模组容器接受超过 64 的堆叠」的做法：不再为每个模组单独写补丁，改由容器自己报出真实容量，因此原版箱子等普通容器、多数模组机器和库存都能自然接受更大的堆叠。物品本身的堆叠上限仍然完全由规则文件决定，item 级粒度不变。

### 核心变化

- 现在大多数模组容器都能接受超过 64 的物品堆叠，包括原版箱子等普通容器、各类模组机器和库存。
- 不再需要为每个模组单独写兼容补丁：容器自己报出真实容量，此前没有专门适配过的模组容器也一并生效。
- 物品本身的堆叠上限仍由规则文件决定，item 级粒度不变，规则求值没有改动。
- 对外显示的容量不会超过对应写入路径的真实容量，因此容器放入不会吞掉多余物品。
- Farmer's Delight 的容器（篮子、橱柜、锅、煎锅）现在能正常超过 64。
- 新增 Mystical Agriculture 精华再处理器支持，6 台机器都能超过 64；维护者已实机测试这 6 台，未复现放入超量物品的问题。实机测试只覆盖这条路径，不构成对其他环境的兼容承诺。

### 兼容

- 少数确实冲突的模组可以加进 `compat.inventoryStackLimitBlacklist.modIds` 应急绕行，列表为空时不做任何事；改变开关或名单需要重启。
- 黑名单只让容量扩展跳过所列模组，不会关闭它们的其他兼容配置，也不会全局关掉其他模组。

### 配置

- `compat.inventoryExpansion.enabled`（默认 `true`）：容器容量扩展的总开关。显式设为 `false` 并重启后生效，只回退容量扩展，不影响物品规则、NBT、网络与防吞机制。
- `compat.inventoryStackLimitBlacklist.modIds`（默认空）：冲突模组的应急名单，只接受已加载模组的 Mod ID，未加载的 ID 不产生效果。
- `debug.mergeLimitTrace`（默认 `false`）：新增诊断开关，复现单个问题时逐次记录容器合并时的槽位上限解析，不改变解析结果。
- 物品规则结果仍受 `general.maxStackSize` 夹取，工作台槽位仍由 `compat.vanilla.craftingSlotLimit` 管理，行为与之前一致。

### English

This release changes how modded containers are allowed to hold more than 64 items: instead of a per-mod patch, containers now report their real capacity themselves, so vanilla chests and other ordinary containers, most mod machines and inventories accept larger stacks without any per-mod work. Item stack limits themselves are still decided entirely by the rule files, with item-level granularity unchanged.

#### Core changes

- Most mod containers now accept stacks larger than 64, including vanilla chests and other ordinary containers, mod machines and their inventories.
- No more per-mod compatibility patches: containers report their real capacity themselves, and containers from mods that were never specifically adapted follow as well.
- Item stack limits are still decided by the rule files, item-level granularity is unchanged, and rule evaluation was not touched.
- The advertised capacity never exceeds the real capacity of the matching write path, so inserting into containers does not swallow leftover items.
- Farmer's Delight containers (Basket, Cabinet, Cooking Pot, Skillet) now hold more than 64 normally.
- Added Mystical Agriculture Essence Reprocessor support: all six machines can hold more than 64. The maintainer tested those six machines in a running game and did not reproduce the oversized-insert issue; that test covers this path only and is not a compatibility promise for other environments.

#### Compatibility

- A mod that genuinely conflicts can be added to `compat.inventoryStackLimitBlacklist.modIds` as an emergency bypass; an empty list does nothing, and changing the switch or the list requires a restart.
- The blacklist only makes capacity expansion skip the listed mods; it does not disable their other compatibility configs and does not switch off other mods globally.

#### Configuration

- `compat.inventoryExpansion.enabled` (default `true`): master switch for container capacity expansion. Setting it explicitly to `false` and restarting rolls back capacity expansion only; item rules, NBT, networking and the no-drop safeguards are unaffected.
- `compat.inventoryStackLimitBlacklist.modIds` (default empty): emergency list for conflicting mods; only Mod IDs of loaded mods take effect, unloaded IDs do nothing.
- `debug.mergeLimitTrace` (default `false`): new diagnostic switch that logs each container-merge slot-limit resolution while reproducing a single issue, without changing the resolved value.
- Rule results are still clamped by `general.maxStackSize`, and workbench slots are still managed by `compat.vanilla.craftingSlotLimit`; both behave as before.

## 0.2.4

### 稳定性与修复

- 修复生产/Notch 混淆环境下 `func_70297_j_` 方法脱节及 Forge Loader 初始化引发的启动崩溃。
- 修复 [AE2 Supergiant](https://github.com/CleanroomMC/Applied-Energistics-2) 接口校验时触发 `InvalidMixinException` 的启动崩溃。
- 修复 [NuclearCraft: Overhauled](https://github.com/tomdodd4598/NuclearCraft)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/nuclearcraft-overhauled)）机器输出槽硬编码 64 截断吞物问题。
- 修复 [TechReborn](https://github.com/TechReborn/TechReborn)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/techreborn)）/ [RebornCore](https://github.com/TechReborn/RebornCore) 机器达到 64 产物后误判满仓卡死问题。
- 完善 AE2 兼容：同时支持传统 [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2)）与新版 AE2 Supergiant。
- 容器放入防吞安全护栏：Shift 快捷存入与鼠标存入对齐，未适配模组容器安全回退原版 64 限制。

### 配置与特性

- 新增 `compat.vanilla.craftingSlotLimit` 配置，可限制工作台槽位上限，防止按 Shift 批量合成超大堆叠造成卡顿。
- 新增 `client.alwaysCompactNumbers` 配置，支持在槽位上显示紧凑缩略数字（如 `1.5K`、`0.1M`）。
- 开放 [NuclearCraft: Overhauled](https://github.com/tomdodd4598/NuclearCraft) 速度与能量升级槽的堆叠上限配置。
- 兼容模组全面更新与分包重构，移除冗余伪开关，统一采用 MixinBooter 官方黑名单机制。
- 兼容目标覆盖 [Ender IO](https://github.com/SleepyTrousers/EnderIO-1.5-1.12)、[GregTech CEu](https://github.com/GregTechCEu/GregTech)、[ProjectE](https://github.com/sinkillerj/ProjectE)、[IC2](https://www.curseforge.com/minecraft/mc-mods/industrial-craft)、[Mantle](https://github.com/SlimeKnights/Mantle)、[Refined Storage](https://github.com/refinedmods/refinedstorage)、[Integrated Dynamics](https://github.com/CyclopsMC/IntegratedDynamics)、[Immersive Engineering](https://github.com/BluSunrize/ImmersiveEngineering) 等。

### English

#### Stability and fixes

- Fixed startup crashes caused by the `func_70297_j_` method mismatch under production/Notch-obfuscated environments and by Forge Loader initialization.
- Fixed a startup crash where [AE2 Supergiant](https://github.com/CleanroomMC/Applied-Energistics-2) interface validation raised `InvalidMixinException`.
- Fixed [NuclearCraft: Overhauled](https://github.com/tomdodd4598/NuclearCraft) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/nuclearcraft-overhauled)) output slots clamping at 64 and swallowing items.
- Fixed [TechReborn](https://github.com/TechReborn/TechReborn) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/techreborn)) / [RebornCore](https://github.com/TechReborn/RebornCore) machines wrongly treating the inventory as full and stalling after reaching 64 output.
- Improved AE2 compatibility: both legacy [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2)) and the newer AE2 Supergiant are supported.
- Container-insert safety guard: Shift quick-store now aligns with mouse insert, and modded containers that are not adapted safely fall back to the vanilla 64 limit.

#### Configuration and features

- Added the `compat.vanilla.craftingSlotLimit` config, which can limit workbench slot capacity to prevent stutter when batch-crafting very large stacks with Shift.
- Added the `client.alwaysCompactNumbers` config, which can show compact abbreviated counts such as `1.5K` and `0.1M` in slots.
- Opened stack-limit configuration for [NuclearCraft: Overhauled](https://github.com/tomdodd4598/NuclearCraft) speed and energy upgrade slots.
- Broad compatibility update and package restructuring: redundant pseudo-switches were removed and MixinBooter's official blacklist mechanism is used throughout.
- Compatibility targets cover [Ender IO](https://github.com/SleepyTrousers/EnderIO-1.5-1.12), [GregTech CEu](https://github.com/GregTechCEu/GregTech), [ProjectE](https://github.com/sinkillerj/ProjectE), [IC2](https://www.curseforge.com/minecraft/mc-mods/industrial-craft), [Mantle](https://github.com/SlimeKnights/Mantle), [Refined Storage](https://github.com/refinedmods/refinedstorage), [Integrated Dynamics](https://github.com/CyclopsMC/IntegratedDynamics), [Immersive Engineering](https://github.com/BluSunrize/ImmersiveEngineering), and others.

## 0.2.3

- 移除 1.12.2 下不再需要的 `category` 字段
- 修复规则可能重复执行的问题
- 优化代码结构

### English

- Removed the `category` field, which is no longer needed on 1.12.2
- Fixed rules possibly being evaluated more than once
- Optimized the code structure

## 0.2.2

- 修复 ModularUI/RSB 背包 GUI 点击时 `BackpackContainer cannot be cast to ContainerAccessor` 崩溃
- 修复 `ContainerState` 在 mixin-owned 包导致的 `IllegalClassLoadError`
- 修复 `@WrapOperation` 包装 `setItemStack` 和 `dropItem` 时缺少实例方法 receiver 的注入失败
- 简化 `build.gradle.kts` 中重复的 IDEA run configuration 和 auto test task 样板
- 简化 `EarlyMixinConfigTest`，合并同类 IO 读取测试
- 新增 `item = *` 通配符，匹配所有可堆叠物品（排除 baseSize=1 的工具、盔甲等）
- 新增 `tab` 字段，按创造模式标签页匹配（如 `tab = buildingBlocks`）
- 新增 `category` 字段（后续版本已移除）
- 新增 `meta` 范围写法（`100 < meta < 300`）
- `RuleField` 简化：去掉冗余 `id` 构造参数，`matchers`/`byName` 懒加载，`fromIdentifier` 只做一次 uppercase 查表
- 引入 `FieldType` 枚举，编译器按类型分发，加新字段只需 enum 一行
- 移除 `RuleStepAst`/`RuleActionAst` 空壳，parser 直接输出 `RuleStep`/`RuleAction`
- 移除 `RuleReloadWarning`/`RuleComplexityWarning` 空 typealias
- 简化 `RuleComplexityAnalyzer.analyze()` 直接返回 `List<LocalizedMessage>`

### English

- Fixed a crash on clicking the ModularUI/RSB backpack GUI: `BackpackContainer cannot be cast to ContainerAccessor`
- Fixed an `IllegalClassLoadError` caused by `ContainerState` living in a mixin-owned package
- Fixed injection failures when `@WrapOperation` wraps `setItemStack` and `dropItem` without an instance-method receiver
- Simplified the duplicated IDEA run configuration and auto test task boilerplate in `build.gradle.kts`
- Simplified `EarlyMixinConfigTest` by merging similar IO reading tests
- Added the `item = *` wildcard, matching every stackable item (excluding tools, armor, and others with `baseSize=1`)
- Added the `tab` field for matching by creative tab (e.g. `tab = buildingBlocks`)
- Added the `category` field (removed in a later version)
- Added the `meta` range syntax (`100 < meta < 300`)
- `RuleField` simplification: dropped the redundant `id` constructor parameter, made `matchers`/`byName` lazy, and made `fromIdentifier` do a single uppercase lookup
- Introduced the `FieldType` enum, with the compiler dispatching by type so a new field only needs one enum line
- Removed the empty `RuleStepAst`/`RuleActionAst` shells; the parser now emits `RuleStep`/`RuleAction` directly
- Removed the empty `RuleReloadWarning`/`RuleComplexityWarning` typealiases
- Simplified `RuleComplexityAnalyzer.analyze()` to return `List<LocalizedMessage>` directly

## 0.2.1

- 修复拾取时退回原始堆叠上限

### English

- Fixed falling back to the original stack limit on pickup

## 0.2.0

- 修正部分本地化错误

### English

- Corrected several localization errors
