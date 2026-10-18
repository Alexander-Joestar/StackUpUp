# Changelog

## 0.2.5.dev1

### 架构

- ItemStack 级规则仍是主路径：`ItemMixin` 与 `ItemStackMixin` 通过 `StackLimitHooks.applyDynamicStackLimit` 交给当前规则快照求值，结果受 `general.maxStackSize` 约束（`src/main/java/io/alexjoest/stackupup/mixin/early/ItemMixin.java:12-24`、`ItemStackMixin.java:17-18`、`src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt:188-205`）。下面的容量补丁只解决库存 getter 里的字面量 64，不参与规则求值。
- 容量 getter 改为结构化字节码补丁：`InventoryStackLimitTransformer` 在类加载时按继承关系识别具体类（实现了 `IInventory`、实现了 `IItemHandler` 或继承 `Slot` 的类，不限命名空间，原版类同样命中），只把容量方法里的字面量 `64`（`BIPUSH 64`）替换为对 `InventoryStackLimitHook` 的调用，等价于 StackUp 的 literal 64 替换（`src/main/java/io/alexjoest/stackupup/core/InventoryStackLimitTransformer.java:72-104,160-188`）。不按类名白名单挑选目标，也不改写写入方法。
- 被替换的只有容量 getter：`getInventoryStackLimit()I`/`func_70297_j_`、`getSlotLimit(I)I`、`getSlotStackLimit()I`/`func_75219_a`、`getItemStackLimit(ItemStack)`/`func_178170_b`（`InventoryStackLimitTransformer.java:127-151`）。写侧仍由 `InventoryWriteLimitMixin` 包裹的 `TileEntityLockableLoot#setInventorySlotContents` 承担（`src/main/java/io/alexjoest/stackupup/mixin/early/InventoryWriteLimitMixin.java:16-29`）；该 Mixin 覆盖不到的原版子类列在 transformer 的类注释里，属于已知限制，不是本次新增的保证。
- 判定移到调用时：注入的 hook 调用无条件发出，由 `ClassLoadPolicy.shouldPatch` 在 getter 运行时读取当时的扩展开关、策略是否就绪、黑名单和兼容上限；因此在启动策略发布前完成变换的类不会整局停留在未补丁状态，而是在策略就绪前解析为原版 64（`InventoryStackLimitTransformer.java:26-28,168-186`、`InventoryStackLimitHook.java:126-137`）。
- 排除表只收配置托管的容量：`InventoryCrafting` 与 `InventoryCraftResult` 由 `compat.vanilla.craftingSlotLimit` 和 `VanillaInventoryLimitMixin` 管理，transformer 跳过它们；接口、抽象类与本 Mod 包同样跳过（`InventoryStackLimitTransformer.java:46-55,86-95,113-125`）。

### 清理

- 删除 Farmer's Delight Legacy 的容量 Mixin：其 Basket/Cabinet/Cooking Pot/Skillet 是具体 `IInventory` 实现，`getInventoryStackLimit()` 直接 `return 64;`，已由结构化 transformer 覆盖，`FarmersDelightInventoryLimitMixin` 与两份 `farmersdelight` 配置一并删除（提交 `925fda5e7ca7`）。
- 删除 AE2 Supergiant 里从未生效的 `InternalInventoryMixin`：它指向的 `ae2.core.inventory.BaseInternalInventory` 在 Supergiant 源码树中不存在（实际路径为 `ae2/api/inventories/BaseInternalInventory.java`），`@Pseudo` + `require=0` 使其静默失效；该配置保留 `AppEngInternalInventoryMixin` 与 `ContainerAdapterMixin`（提交 `191beb94560a`，`src/main/resources/mixins.stackupup.capacity.late.ae2supergiant.json:8-10`）。
- 删除 EnderIO `TileEnchanter` 目标与 Immersive Engineering 的 `IEInventoryHandler#getSlotLimit` handler：两者都是具体类上的字面量 64，transformer 已在类加载时改写；保留 EnderIO 抽象基类 `AbstractInventoryMachineEntity` 的 handler 和 IE `insertItem` 内对 `IIEInventory#getSlotLimit` 的包裹（`EnderIOMachineInventoryLimitMixin.java:9-34`、`IEInventoryHandlerMixin.java:9-33`，提交 `8a68e83e7000`）。
- `compat.inventoryStackLimitBlacklist.modIds` 只作为应急绕行：空列表是 no-op；非空时按 Forge owned packages 让实际 receiver/受支持公开 owner 绕过容量扩展，并跳过所列 Mod 的 capacity late 配置，不关闭其 ordinary/安全配置（`src/main/kotlin/io/alexjoest/stackupup/bootstrap/StackUpUpMixinConnector.kt:117-159,329-349`）。它能绕过容量，不能证明共享 ownerless 或私有 delegate 路径安全。

### 其他

- 新增诊断开关 `debug.mergeLimitTrace`（默认 `false`）：只在复现单个问题时逐次记录容器合并的槽位上限解析，不改变解析结果（`src/main/kotlin/io/alexjoest/stackupup/StackUpUpConfig.kt:162-169`）。
- 补充 `item = * -> *N` 通配乘算与大箱子合并的仓库内回归测试。这些是仓库内测试证据，不构成第三方运行期验证或全面兼容承诺。

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
