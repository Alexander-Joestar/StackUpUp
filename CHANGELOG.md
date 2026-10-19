# Changelog

## 0.2.5

这一版改变了「让模组容器接受超过 64 的堆叠」的做法：不再为每个模组单独写补丁，而是在类加载时按继承关系识别具体的容器类，把容量 getter 里写死的 `64` 换成一个动态查询，因此原版箱子、多数模组机器和其他 `TileEntity` 库存只要容量 getter 是字面量 64，就会自然接受更大的堆叠。物品本身的堆叠上限仍然完全由规则文件决定，item 级粒度不变，规则求值路径也没有改动。少数确实冲突的模组可以临时加进 `compat.inventoryStackLimitBlacklist.modIds` 绕行，列表为空时不做任何事。安全边界同样没变：对外显示的容量不会超过对应写入路径的真实容量。

### 架构

- 物品规则仍是主路径，这一版没有改动它：`ItemMixin` 与 `ItemStackMixin` 每次都把求值交给当前规则快照（`StackLimitHooks.applyDynamicStackLimit`），结果再受 `general.maxStackSize` 夹取（`src/main/java/io/alexjoest/stackupup/mixin/early/ItemMixin.java:12-24`、`ItemStackMixin.java:17-18`、`src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt:188-205`）。下面说的容量补丁只解决库存 getter 里写死的 64，不参与规则求值。
- 容量 getter 改为结构化的字节码补丁：`InventoryStackLimitTransformer` 在类加载时按继承关系识别具体的容器类（实现了 `IInventory`、实现了 `IItemHandler`，或继承 `Slot` 的类，不限包名，原版类同样命中），只把容量方法里的字面量 `64`（`BIPUSH 64`）换成一个动态查询调用，效果与 StackUp 的 literal 64 替换等价（`src/main/java/io/alexjoest/stackupup/core/InventoryStackLimitTransformer.java:72-104,160-188`）。挑目标不看类名白名单，也不改写写入方法。
- 被替换的只有容量 getter：`getInventoryStackLimit()I`/`func_70297_j_`、`getSlotLimit(I)I`、`getSlotStackLimit()I`/`func_75219_a`、`getItemStackLimit(ItemStack)`/`func_178170_b`（`InventoryStackLimitTransformer.java:127-151`）。写入侧仍由 `InventoryWriteLimitMixin` 包裹的 `TileEntityLockableLoot#setInventorySlotContents` 负责（`src/main/java/io/alexjoest/stackupup/mixin/early/InventoryWriteLimitMixin.java:16-29`）；这个 Mixin 覆盖不到的原版子类写在该 transformer 的类注释里，属于已知限制，不是本版新增的保证。
- 判定放在调用时，而不是类加载时：补丁调用是无条件注入的，真正的判断（扩展开关、策略是否就绪、黑名单、兼容上限）由 `ClassLoadPolicy.shouldPatch` 在 getter 运行时读取当时的值。所以在启动策略发布之前就完成加载的类，不会整局停留在「没打补丁」的状态，而是在策略就绪之前按原版 64 返回（`InventoryStackLimitTransformer.java:26-28,168-186`、`InventoryStackLimitHook.java:126-137`）。
- 排除表只收配置托管的容量：`InventoryCrafting` 与 `InventoryCraftResult` 由 `compat.vanilla.craftingSlotLimit` 和 `VanillaInventoryLimitMixin` 管理，transformer 跳过它们；接口、抽象类和本 Mod 自己的包同样跳过（`InventoryStackLimitTransformer.java:46-55,86-95,113-125`）。

### 兼容

- 新增 [Mystical Agriculture](https://github.com/BlakeBr0/MysticalAgriculture)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/mystical-agriculture)）精华再处理器（Essence Reprocessor）族的容量支持：新增只做容量的配置 `mixins.stackupup.capacity.late.mysticalagriculture.json`，里面只登记一个 `@Pseudo` 目标——抽象基类 `TileEssenceReprocessor`，用 `@ModifyReturnValue` 拦截 `func_70297_j_()I` 与 `getInventoryStackLimit()I`（`src/main/java/io/alexjoest/stackupup/mixin/late/mysticalagriculture/MysticalAgricultureInventoryLimitMixin.java:25-35`、`src/main/resources/mixins.stackupup.capacity.late.mysticalagriculture.json:1-8`、注册处 `src/main/kotlin/io/alexjoest/stackupup/bootstrap/StackUpUpMixinConnector.kt:324`）。
- 只打抽象基类、不逐个子类登记的原因：`InventoryStackLimitTransformer` 在类级按 `ACC_ABSTRACT` 跳过抽象类（`InventoryStackLimitTransformer.java:86-95`），而 `TileEssenceReprocessor` 是 `public abstract class`，其 getter 体就是字面量 `bipush 64; ireturn`；6 个具体子类（`TileInferiumReprocessor`、`TileIntermediumReprocessor`、`TilePrudentiumReprocessor`、`TileSuperiumReprocessor`、`TileSupremiumReprocessor`、`TileUltimateReprocessor`）都不重写这个 getter，所以改基类一处即可覆盖 6 台机器（`javap` 读 `MysticalAgriculture-1.12.2-1.7.5.jar`）。同类做法的先例是 EnderIO 抽象基类 `AbstractInventoryMachineEntity`（`src/main/java/io/alexjoest/stackupup/mixin/late/enderio/EnderIOMachineInventoryLimitMixin.java:21-34`）。
- 写入侧没有改动，也不需要改：`TileEssenceReprocessor.func_70299_a`（`setInventorySlotContents`）在落盘前会调用同一个 `func_70297_j_()`，并用 `ItemStack.func_190920_e` 把超量夹回该值（同一 jar 字节码，`func_70299_a` 偏移 57-71），因此抬高 getter 就等于抬高真实的落库上限。该结论来自 jar 字节码与仓库内结构测试（`src/test/kotlin/io/alexjoest/stackupup/mixin/MysticalAgricultureMixinSourceTest.kt:12-103`）；此外维护者已在实机测试 6 台精华再处理器，未复现放入超量物品的问题。实机测试只覆盖这条路径，未做矩阵式全量验证，不构成对其他环境或其他 Mod 的运行期兼容承诺。

### 清理

- 删除 Farmer's Delight Legacy 的容量 Mixin：它的 Basket / Cabinet / Cooking Pot / Skillet 都是具体的 `IInventory` 实现，`getInventoryStackLimit()` 直接 `return 64;`，已经被上面说的结构化 transformer 覆盖，因此 `FarmersDelightInventoryLimitMixin` 与两份 `farmersdelight` 配置一并删除（提交 `925fda5e7ca7`）。
- 删除 AE2 Supergiant 里从未生效的 `InternalInventoryMixin`：它指向的 `ae2.core.inventory.BaseInternalInventory` 在 Supergiant 源码树中不存在（实际路径是 `ae2/api/inventories/BaseInternalInventory.java`），`@Pseudo` + `require=0` 让这个错误一直静默失效；该配置保留 `AppEngInternalInventoryMixin` 与 `ContainerAdapterMixin`（提交 `191beb94560a`，`src/main/resources/mixins.stackupup.capacity.late.ae2supergiant.json:8-10`）。
- 删除 EnderIO `TileEnchanter` 目标和 Immersive Engineering 的 `IEInventoryHandler#getSlotLimit` handler：两者都是具体类上的字面量 64，transformer 在类加载时已经改写；保留 EnderIO 抽象基类 `AbstractInventoryMachineEntity` 的 handler，以及 IE `insertItem` 内对 `IIEInventory#getSlotLimit` 的包裹（`EnderIOMachineInventoryLimitMixin.java:9-34`、`IEInventoryHandlerMixin.java:9-33`，提交 `8a68e83e7000`）。
- `compat.inventoryStackLimitBlacklist.modIds` 只作为应急绕行手段：空列表什么都不做；非空时按 Forge owned packages 让实际 receiver 或受支持的公开 owner 绕过容量扩展，并跳过所列 Mod 的 capacity late 配置，但不会关闭它们的 ordinary / 安全配置（`src/main/kotlin/io/alexjoest/stackupup/bootstrap/StackUpUpMixinConnector.kt:117-159,315-332`）。它能绕过容量，不能用来证明共享的 ownerless 实例或私有 delegate 路径是安全的。

### 其他

- 新增诊断开关 `debug.mergeLimitTrace`（默认 `false`）：只在复现单个问题时逐次记录容器合并时的槽位上限解析，不改变解析结果（`src/main/kotlin/io/alexjoest/stackupup/StackUpUpConfig.kt:162-169`）。
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
