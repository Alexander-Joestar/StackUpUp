# Changelog

## Unreleased

### 兼容性

- `compat.inventoryExpansion.enabled` 是默认启用的启动期库存容量开关（新/缺失配置采用 broad-default；已有配置中的显式 `false` 保持关闭，作为重启时的紧急回退）：控制窄范围 IInventory getter transformer 与可选 capacity Mixin 配置；不关闭 ItemStack 规则、ordinary/baseline 配置或专用 `InventoryWriteLimitMixin` 写入安全路径。
- `compat.inventoryStackLimitBlacklist.modIds` 仅按可归属的 Forge owned-package 接收端/受支持 owner 选择性绕过容量扩展，并跳过对应 Mod 的 capacity late 配置；不关闭其 ordinary/safety 配置，也不证明共享 ownerless 或私有 delegate 路径安全。
- 普通与 capacity Mixin 配置分开登记：IC2 Classic 的原生 64 槽位保护留在 ordinary 配置，Farmer's Delight 的容量 getter 补丁仅为可选 capacity 配置目标；注册范围不是运行时兼容性验证。

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

## 0.2.3

- 移除 1.12.2 下不再需要的 `category` 字段
- 修复规则可能重复执行的问题
- 优化代码结构

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

## 0.2.1

- 修复拾取时退回原始堆叠上限

## 0.2.0

- 修正部分本地化错误
