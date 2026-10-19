# StackUpUp

**让 Minecraft 1.12.2 的物品突破 64 堆叠上限。**

[![CurseForge](https://img.shields.io/badge/CurseForge-StackUpUp-orange)](https://www.curseforge.com/minecraft/mc-mods/stackupup)

StackUpUp 是面向 Minecraft 1.12.2 整合包作者和服主的堆叠上限模组，用文本规则控制物品堆叠数量，优先覆盖原版库存路径、`metadata` 子物品、矿物辞典匹配，以及常见模组里写死 `64` 的兼容点。

English documentation: [README.en.md](README.en.md)

## 当前状态

- 目标版本：Minecraft **1.12.2** + Forge **14.23.5.2847**
- 当前版本：**0.2.5.1**
- 规则系统：DSL v2，支持 `.su` 与带 `state` / `gate` 的 `.su.md`
- 兼容层：[MixinBooter](https://github.com/CleanroomMC/MixinBooter) **11.17**（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/mixin-booter)）+ [CleanMix](https://github.com/CleanroomMC/CleanMix) **0.7.2**；`IMixinConnector` 注册常驻 early/baseline 与按 mod presence 选择的普通 late Mixin 配置，并在库存容量扩展开启时加入独立 capacity 配置；`StackUpUpCore` 仅在启动开关开启且无冲突时注册窄范围 `InventoryStackLimitTransformer`，在类加载时把声明容量 getter 的实现者（含接口 default 方法与抽象基类的具体方法体）里的字面量 `64` 换成对调用时策略的查询；10.7 与旧 loader 仅为历史基线

## 下载

从 [CurseForge 下载 StackUpUp](https://www.curseforge.com/minecraft/mc-mods/stackupup)。

## 安装

1. 安装 Minecraft **1.12.2** 与 Forge **14.23.5.2847**。
2. 安装依赖：
   - [MixinBooter](https://github.com/CleanroomMC/MixinBooter) **11.17**（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/mixin-booter)）
   - [Forgelin-Continuous](https://github.com/CleanroomMC/Forgelin-Continuous) **2.3.0.0**（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/forgelin-continuous)）
   当前项目构建验证使用上述版本；其他版本未由本仓库验证。
3. 将 StackUpUp 的 jar 放入 `mods/` 文件夹。
4. 启动一次游戏或服务端，让模组生成配置与规则目录。

## 相关项目

- 基础设施：[Cleanroom Loader](https://github.com/CleanroomMC/Cleanroom)、[MixinBooter](https://github.com/CleanroomMC/MixinBooter)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/mixin-booter)）、[CleanMix](https://github.com/CleanroomMC/CleanMix)、[Forgelin-Continuous](https://github.com/CleanroomMC/Forgelin-Continuous)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/forgelin-continuous)）和 [RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle)。
- 存储与自动化：[Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2)）、[AE2 Supergiant](https://github.com/CleanroomMC/Applied-Energistics-2)、[Refined Storage](https://github.com/refinedmods/refinedstorage)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/refined-storage)）、[Simple Storage Network](https://github.com/Lothrazar/StorageNetwork)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/simple-storage-network)）和 [Integrated Dynamics](https://github.com/CyclopsMC/IntegratedDynamics)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/integrated-dynamics)）。
- 工业与科技：[NuclearCraft: Overhauled](https://github.com/tomdodd4598/NuclearCraft)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/nuclearcraft-overhauled)）、[TechReborn](https://github.com/TechReborn/TechReborn)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/techreborn)）、[RebornCore](https://github.com/TechReborn/RebornCore)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/reborncore)）、[Ender IO](https://github.com/SleepyTrousers/EnderIO-1.5-1.12)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/ender-io)）、[GregTech CEu](https://github.com/GregTechCEu/GregTech)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/gregtech-ce-unofficial)）、[IndustrialCraft 2](https://www.curseforge.com/minecraft/mc-mods/industrial-craft)、[Mystical Agriculture](https://github.com/BlakeBr0/MysticalAgriculture)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/mystical-agriculture)）和 [ProjectE](https://github.com/sinkillerj/ProjectE)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/projecte)）。
- 建筑与库存：[Mantle](https://github.com/SlimeKnights/Mantle)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/mantle)）、[Tinkers' Construct](https://www.curseforge.com/minecraft/mc-mods/tinkers-construct)、[LimeLib](https://www.curseforge.com/minecraft/mc-mods/limelib)、[Immersive Engineering](https://github.com/BluSunrize/ImmersiveEngineering)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/immersive-engineering)）、[Colossal Chests](https://github.com/CyclopsMC/ColossalChests)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/colossal-chests)）、[Actually Additions](https://github.com/Ellpeck/ActuallyAdditions)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/actually-additions)）和 [Brandon's Core](https://github.com/bradleyq/BrandonsCore)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/brandons-core)）。

## 规则文件

规则目录为 `config/stackupup/`。主入口推荐 `config/stackupup/main.su`；需要写说明、状态开关或按条件启用规则时，改用 Markdown 容器 `config/stackupup/main.su.md`。

规则按序求值：后命中的规则在先前结果上继续覆盖或叠加。当前有效评估顺序是：

1. `<save>/data/stackupup/main.su.md`。
2. `config/stackupup/*.su.md`，按文件名排序，排除示例文件。
3. 旧兼容文件 `config/stackupup-rules.su`，仅在 `main.su` 不存在时加载。
4. `<save>/data/stackupup/world.su`。
5. `config/stackupup/*.su`，按文件名排序，排除 `user.su` 和示例文件；`main.su` 也属于这一步。
6. `config/stackupup/user.su`。

详细写法见 [docs/DSL-v2-规则示例.md](docs/DSL-v2-%E8%A7%84%E5%88%99%E7%A4%BA%E4%BE%8B.md)。

## 命令

命令：`/stackupup reload`、`/stackupup edit`、`/stackupup state get <name>`、`/stackupup state set <name> <value>`、`/stackupup inspect [slot N]`、`/stackupup inspect block <x> <y> <z> <none|down|up|north|south|west|east> [slot N]`。

- `inspect` 只读检查服务端玩家当前打开的容器；`inspect slot N` 仅检查指定的容器槽位。控制台/命令方块可使用已加载区块中的 `inspect block x y z none|down|up|north|south|west|east [slot N]`，对该位置询问普通库存或指定朝向的 item-handler capability。
- 默认最多列出 8 个槽位；报告显示 Container、Slot、库存/handler 对象的完整类名和本次报告内的对象编号，并尽可能展示由 Forge `ownedPackages` 推导的 modId 候选。对象编号只在该报告中有效；多个候选标为歧义，未知归属显示 UNKNOWN。类名仅为诊断证据，当前不能用类名配置黑名单。`InvWrapper.getInv()` 是受支持的公开 delegate 访问器；其他 handler 的不可见 delegate 不反射读取，明确标为 UNKNOWN。
- 输出可复制的 `compat.inventoryStackLimitBlacklist.modIds` 配置片段（只有唯一、可证明的 modId 候选才建议）；命令不修改配置。物品注册 mod 不等于库存所属 mod，因此不会从槽内物品推断库存 modId。检查不调用插入/提取、不做模拟写入，也不证明真实容量或守恒。modId 黑名单可能作用于该 mod 的其他库存类/继承类；只加入复核后的必要条目。

- `reload`：重新加载规则。
- `edit`：打开主规则文件，仅客户端可用。
- `state get <name>`：读取当前存档 `<save>/data/stackupup/main.su.md` 中的 state；其他 `.su.md` 不共享这些 state。
- `state set <name> <value>`：修改同一文件中的 state；`<value>` 不区分大小写，接受 `true` / `false` 以及 `1` / `0`、`yes` / `no`、`on` / `off`；仅当该写入使任一 gate 的求值结果实际变化时才触发 `reload`。

若 `config/stackupup/main.su` 尚不存在而旧 `config/stackupup-rules.su` 存在，首次 DSL `reload` 会把旧文件纳入 legacy fallback 并创建主文件；此后 `reload` 可能不再加载旧文件，其中的规则可能不再生效，迁移行为仍未定型。

## DSL 示例

```su
item = minecraft:egg -> 256
item = * -> 128
mod = thermal -> 1024
ore = ingotSteel -> 2048
material = steel -> 2048
item in [minecraft:egg, minecraft:snowball] -> 128
item = gregtech:gt.metaitem.01 && meta = 11305 -> 512
item = gregtech:gt.metaitem.01@11305 -> 512
item = minecraft:wool:14 -> 512
2 < size < 64 -> 256
tab = buildingBlocks -> 256
```

动作可串联，在已有结果上继续调整，例如 `item = gregtech:gt.metaitem.01@11305 && ore = ingotSteel -> 1024`、`material = steel && mod = gregtech -> 1024`、`ore = ingotSteel -> 512 -> *2`。

`material` 是可选匹配字段，仅在 GregTech 已加载且物品可解析出 GT material 时有值；GT 未加载、解析失败或非 GT 材料物品时，所有 `material` 条件（含 `!=` 与列表匹配）都不命中。请使用 material registry name：GT 原生材料可写 `steel` 这类名称，需跨 mod 精确区分时用 `modid:name` 格式；不在示例中列未经验证的具体材料。它不表示支持所有 GT 物品。

`category` 字段曾短暂出现，但 1.12.2 下不再需要，建议不要使用；例如 `category = potion -> 32`、`category = enchanted_book -> 16`。

## 客户端显示

大堆叠数量会在物品槽位里自动缩放或缩写；0.2.4 新增 `alwaysCompactNumbers` 配置项，可在适配槽位和字体缩放前强制使用短文本，例如 `1-999`、`1K-99K`、`0.1M-99M`、`0.1B-2.1B`。

若槽位中的短数字不够清楚，可在配置里开启 Tooltip 堆叠显示，以 `数量/上限` 展示当前数量和最大堆叠上限。

## 配置

启动一次游戏或服务端后，配置文件为 `config/stackupup.cfg`；配置 GUI 和文件中的分组结构一致。`/stackupup reload` 不会重新读取启动期的库存容量开关或黑名单；修改这两项后必须重启。

- `general.maxStackSize`：全局兼容最大堆叠上限，默认 `64`；规则结果和兼容入口都不会超过这个值。
- `compat.vanilla.craftingSlotLimit`：工作台网格与合成结果槽上限，默认 `64`。`0` 保持原版/默认行为，大于 `0` 使用指定上限。这主要是性能（Performance）设置：按住 Shift 批量合成极大堆叠可能造成严重卡顿，不是平衡开关。实际上限还会受 `general.maxStackSize` 与物品/配方限制收紧。
- `compat.nuclearcraft.speedUpgradeLimit` 与 `compat.nuclearcraft.energyUpgradeLimit`：NuclearCraft 速度/能量升级的堆叠上限，默认 `0`（保持 NuclearCraft 默认行为）；大于 `0` 时使用自定义上限。NuclearCraft 未加载时，这些设置不生效。
- `compat.inventoryExpansion.enabled`：启动期库存容量扩展开关，默认 `true`（新/缺失配置采用 broad-default；已有配置中的显式 `false` 保持关闭）；显式 `false` 仅作为重启时的紧急回退。开启时注册结构化容量 getter transformer（命中声明 `IInventory` / `IItemHandler` / `Slot` 容量 getter 的实现者，含接口 default 方法与抽象基类的具体方法体）并加入可选 capacity Mixin 配置；关闭时跳过这些可选容量扩展。它不会关闭 ItemStack 规则、常驻 early/baseline 配置、普通 late 配置，或 `mixins.stackupup.baseline.early.json` 中调用 `resolveInventoryWriteLimit` 的专用 `InventoryWriteLimitMixin` 写入安全路径；`modIds` 也不会停用该 Mixin。
- `debug.mergeLimitTrace`：诊断开关，默认 `false`。只在复现单个问题时逐次记录容器合并的槽位上限解析，不改变解析结果，日常游玩保持关闭。
- `compat.inventoryStackLimitBlacklist.modIds`：仅对列出的 Mod 做选择性容量屏蔽，并跳过它的 capacity late 配置；空列表是 no-op。可用 `/stackupup inspect` 输出的候选作复核起点，但不是安全证明。

黑名单只依据 Forge `ownedPackages` 的精确包边界匹配实际接收端或受支持的公开 owner；仅继承被列入的父类或实现其接口，不会自动排除未列出的 addon。共享实例无公开 owner、私有 delegate 与 receiverless 构造路径仍可能是 UNKNOWN。已加载但没有可归属包的 Mod 会使启动检查失败；未加载的 Mod ID 记录无效果。不要把它当作整 Mod 关闭开关。MixinBooter 的 `general.blacklistedConfigs` 则会停用整个所列配置（其中可能含安全 Mixin），不是 capacity-only 黑名单。

**`getSlotLimit` 的粒度注记：** `getSlotLimit(int)` 只影响广告面，真实写入读的是库存自己的 `getInventoryStackLimit()`。常见情况下两者同值（同一个兼容上限被改写到两条 lane），但黑名单按 owned package 匹配，wrapper 与它承载的库存可能分属不同 Mod——例如 McJtyLib 的 `NullSidedInvWrapper` / `FacedSidedInvWrapper` 与 RFTools 的 `ItemFilterInvWrapper` 写死 `return 64;` 且零转发，承载它们的库存也在 `mcjty.rftools.*` 下同样写着字面 64。**此时只拉黑库存所在的 Mod（`rftools`）而不拉黑 wrapper 所在的 Mod（`mcjtylib`），会出现「广告大于真实」**：余量显示偏高、多几次白跑的插入尝试，但不丢物。要避免就同时拉黑两个 Mod。这条来自源码、生产 jar 字节码与调用方代码，不是运行时验证结论。

## 兼容性

StackUpUp 对遵循原版堆叠语义的模组通常直接生效；对自行写死 `64`、绕过 `ItemStack#getMaxStackSize()` 或有特殊库存逻辑的模组，可能需要额外补丁。

核心安全原则：**对外广告容量不能大于真实写入容量。** 未知 `IItemHandler` 实现不做动态扩容；即使其 `getSlotLimit()` 字面返回 64，也不能据此证明真实写入容量可以扩大，否则 vanilla 可能投入超出库存真实承受能力的物品，触发截断、吞物品或与模组溢出逻辑冲突。

当前架构包含窄范围 coremod getter transformer 与分层 Mixin。`StackUpUpCore` 仅在 `compat.inventoryExpansion.enabled=true` 且无冲突时注册 `InventoryStackLimitTransformer`：它按继承关系命中声明 `IInventory` / `IItemHandler` / `Slot` 容量 getter 的实现者（不限命名空间，原版具体类同样命中），只把这些类容量 getter 里的字面量 `64` 换成对调用时策略的查询——**接口 default 方法与抽象基类里的具体方法体同样会被改写**，未覆写该 getter 的实现者/子类由此自动继承扩容量；方法级跳过 abstract/native/static 声明，另外排除本 Mod 包与由 `compat.vanilla.craftingSlotLimit` 托管的 `InventoryCrafting` / `InventoryCraftResult`；这不是已移除的广泛 dynamic ASM 替换层，也不改写写入方法。`StackUpUpMixinConnector` 经 `IMixinConnector` 始终加载 ordinary early / baseline early 配置，按 Mod presence 加载 ordinary late 配置；仅容量开关开启时才加入 `mixins.stackupup.capacity.early.json` 与 `mixins.stackupup.capacity.late.*.json`，`modIds` 只跳过所列 Mod 的 capacity late 配置。

以下是 Connector 中已登记的部分 late 模块（ordinary 与 capacity 两类配置分开登记）；目标 Mod 存在时才排队，注册不等于真实写入路径已验证：

- [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2)）
- [Applied Energistics 2 Supergiant](https://github.com/CleanroomMC/Applied-Energistics-2)
- [Actually Additions](https://github.com/Ellpeck/ActuallyAdditions)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/actually-additions)）
- [Brandon's Core](https://github.com/bradleyq/BrandonsCore)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/brandons-core)）
- [Colossal Chests](https://github.com/CyclopsMC/ColossalChests)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/colossal-chests)）
- CyclopsCore
- [Ender IO](https://github.com/SleepyTrousers/EnderIO-1.5-1.12)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/ender-io)）
- [GregTech CEu](https://github.com/GregTechCEu/GregTech)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/gregtech-ce-unofficial)）
- [IndustrialCraft 2](https://www.curseforge.com/minecraft/mc-mods/industrial-craft)
- [Mantle](https://github.com/SlimeKnights/Mantle)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/mantle)）
- [NuclearCraft: Overhauled](https://github.com/tomdodd4598/NuclearCraft)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/nuclearcraft-overhauled)）
- [TechReborn](https://github.com/TechReborn/TechReborn)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/techreborn)），经 [RebornCore](https://github.com/TechReborn/RebornCore)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/reborncore)）
- [Refined Storage](https://github.com/refinedmods/refinedstorage)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/refined-storage)）
- [Simple Storage Network](https://github.com/Lothrazar/StorageNetwork)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/simple-storage-network)）
- [Integrated Dynamics](https://github.com/CyclopsMC/IntegratedDynamics)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/integrated-dynamics)）
- [LimeLib](https://www.curseforge.com/minecraft/mc-mods/limelib)
- [Immersive Engineering](https://github.com/BluSunrize/ImmersiveEngineering)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/immersive-engineering)）
- [RFTools](https://github.com/McJtyMods/RFTools)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/rftools)）

- IC2 Classic：`mixins.stackupup.late.ic2classic.json` 是 Mod 在场时加载的 ordinary 配置，其中 `SlotBaseMixin` 将报告的槽位上限收敛到原生 64；它不受容量开关或 `modIds` 中 `ic2` 的影响。capacity 表里与 IC2 相关的只有 `mixins.stackupup.capacity.late.ic2.json`（登记 modId `ic2`），并不存在 `mixins.stackupup.capacity.late.ic2classic.json` 这个文件。
- Mystical Agriculture：**0.2.5.1 起没有独立的 capacity 配置**——`mixins.stackupup.capacity.late.mysticalagriculture.json` 与 `MysticalAgricultureInventoryLimitMixin` 已删除，配置表里也没有 ordinary 配置。原来的目标是抽象基类 `TileEssenceReprocessor`：它 `implements ISidedInventory`，getter 体是纯字面量 `bipush 64`，6 个具体再处理器子类（Inferium/Intermedium/Prudentium/Superium/Supremium/Ultimate）都不重写它，所以结构化 transformer 本来就已经覆盖这条路径，那条 Mixin 只是重复登记。删除后容量改由 transformer 提供，**行为不变**；写入侧 `setInventorySlotContents` 落盘前仍用同一个 getter 夹取，未改动也无需改动。维护者此前已在实机测试 6 台精华再处理器、未复现放入超量物品的问题，该实机结论仍只覆盖这条路径。
- RFTools（0.2.5.1 新增）：只有一条 capacity 配置 `mixins.stackupup.capacity.late.rftools.json`（登记 modId `rftools`），没有 ordinary 配置。**容量的读侧主要靠公共改写**：Modular Storage、Storage Scanner 等容器的容量来自 `mcjty.lib.container.DefaultSidedInventory` 的接口 default `getInventoryStackLimit()`（字面 `return 64;`），transformer 现在会改写接口 default 方法体，实现者自动继承扩容量，因此没有逐类登记它们的容器类。**唯一的显式补丁是安全性的**：`StorageScannerTileEntity#update` 把未注入完的余量写回输入槽时，`InventoryHelper.setInventorySlotContents(stackLimit, index, stack)` 的第一个参数是上限而非槽位下标，`McJtyLib` 会就地把 `stack` 截断到该上限，于是没有可路由存储时放入 >64 会在下一帧被截断并丢掉超出部分。`RftoolsStorageScannerLimitMixin` 用 `@ModifyArg(index = 0)` 把该参数换成 receiver 自己的 `getInventoryStackLimit()`，不改写任何写入方法。**该修复已由维护者实机确认**，实机只覆盖这一条路径。
- Farmer's Delight Legacy：没有独立 Mixin 配置。其 Basket、Cabinet、Cooking Pot、Skillet 等具体 `IInventory` 实现只返回字面量 `64`，由上面的结构化 transformer 在类加载时改写；这不表示覆盖该 Mod 的所有库存或证明其真实写入路径。

以上是源码中的注册和目标范围，不是第三方运行时验证或全面兼容承诺。

### MixinBooter 的配置级黑名单

`compat.inventoryStackLimitBlacklist.modIds` 是选择性容量屏蔽；若改用 MixinBooter 黑名单，则会整份停用一个 Mixin 配置，可能同时关闭其中的安全行为。仅在确实要停用整份配置时，才编辑 `config/mixinbooter.cfg`，把配置文件名加进 `general` 段的 `blacklistedConfigs`：

```
general {
    # Mixin configurations that should never be loaded.
    S:blacklistedConfigs <
        mixins.stackupup.late.enderio.json
     >
}
```

- 填的是**配置文件名**（含 `.json`），不是开关名。MixinBooter 对黑名单做整串精确匹配，写 `enderio` 或 `EnderIO` 都不会命中。
- 改完需要**重启游戏或服务端**：MixinBooter 在 coremod 阶段就读这份配置并拉黑，`/stackupup reload` 这类运行期重载不会让它生效，也不会把它重新打开。
- 被拉黑的只是指定配置；同一 Mod 的 ordinary 与 capacity 配置相互独立，不会因为屏蔽其中一份而自动屏蔽另一份。改动后须重启。不要拉黑 `mixins.stackupup.early.json` 或 `mixins.stackupup.baseline.early.json`，否则会停用基础或专用写入安全路径。

当前源码注册 5 个 ordinary late 配置（模块表 `modules` 4 条 + `classicModules` 1 条），它们不受容量开关与 `modIds` 影响：

```
mixins.stackupup.late.ae2.json
mixins.stackupup.late.enderio.json
mixins.stackupup.late.gregtech.json
mixins.stackupup.late.ic2classic.json
mixins.stackupup.late.nuclearcraft.json
```

另注册 16 个 capacity late 配置（`capacityModules` 16 条）。两套表分开登记、不是一一对应，且 capacity 配置仅在容量扩展开启时加入：

```
mixins.stackupup.capacity.late.actuallyadditions.json
mixins.stackupup.capacity.late.ae2.json
mixins.stackupup.capacity.late.ae2supergiant.json
mixins.stackupup.capacity.late.brandonscore.json
mixins.stackupup.capacity.late.colossalchests.json
mixins.stackupup.capacity.late.cyclopscore.json
mixins.stackupup.capacity.late.enderio.json
mixins.stackupup.capacity.late.ic2.json
mixins.stackupup.capacity.late.immersiveengineering.json
mixins.stackupup.capacity.late.integrateddynamics.json
mixins.stackupup.capacity.late.limelib.json
mixins.stackupup.capacity.late.mantle.json
mixins.stackupup.capacity.late.nuclearcraft.json
mixins.stackupup.capacity.late.refinedstorage.json
mixins.stackupup.capacity.late.rftools.json
mixins.stackupup.capacity.late.techreborn.json
```

更完整的实现说明见 [docs/StackUpUp-实现与兼容性说明.md](docs/StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md)。

## 和原版 StackUp 的区别

与 StackUp 相比，StackUpUp 锁定 Minecraft **1.12.2**，规则升级为 **DSL v2**（按物品、metadata、矿物辞典、创造标签页分层配置），兼容层采用 **MixinBooter + Mixin**，客户端数量显示增加缩放、缩写和 Tooltip 辅助显示。

## 开发与验证

**构建需要 JDK 25 和 Gradle 9.2+。** 本项目用 RetroFuturaGradle（RFG）**2.0.2** 接入 Minecraft；该版本的插件类编译为 class file 69（Java 25），且自身要求在 Gradle >= 9.2 时运行。用 JDK 21 或更低版本跑 Gradle 会在配置阶段就失败（`UnsupportedClassVersionError: com/gtnewhorizons/retrofuturagradle/UserDevPlugin ... class file version 69.0`），构建不会开始。仓库自带的 wrapper 是 Gradle 9.4.0，满足下限，无需改动。

这只约束**构建所用的 Gradle JVM**：项目 Java toolchain 仍固定为 8（`build-logic/convention/src/main/kotlin/jvm.gradle.kts:9`），开发客户端也由该 Java 8 toolchain 启动；模组运行期要求未因此改动。

常用验证命令（`JAVA_HOME` 指向 JDK 25）：

```powershell
.\gradlew.bat test
.\gradlew.bat compileTestKotlin
.\gradlew.bat spotlessCheck
```

仓库还提供本地开发自动验收任务，在服务端或客户端启动后验证规则是否生效；详见 [docs/runServer-自动化回归.md](docs/runServer-%E8%87%AA%E5%8A%A8%E5%8C%96%E5%9B%9E%E5%BD%92.md)。

## 依赖升级备忘

这些是备忘，不是待办：

- Gradle wrapper 还是 9.4.0；升级影响整个构建，要改再说。
- `cursegradle` 插件还挂在 `build.gradle.kts:17`；移除会影响发布流程。
- `idea-ext` 还是 1.1.7，能升但收益不大。
- `asm`/`guava`/`gson` 三个 annotationProcessor（`build.gradle.kts:57-59`）**别删**——CleanMix 0.7.2 是 MixinBooter 11.17 生成 refmap 必需的，删了 refmap 就没了。

## 来源

StackUpUp 脱胎于 [StackUp](https://github.com/asiekierka/StackUp)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/stackup)，LGPLv3）。
