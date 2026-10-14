# StackUpUp 实现与兼容性说明

本文是 StackUpUp 的中央架构说明，描述以当前仓库源码和 Forge 1.12.2 源码为准。文中明确区分：

- **当前实现**：代码已经存在的路径，不等于已经完成安全审计。
- **已知限制**：源码能证明的缺口，或因缺少第三方源码而无法判定的边界。
- **规划事项**：后续任务定义，不表示对应生产代码已经落地。

相关记录： [兼容决策记录](agent/compatibility-decision-record.md) · [重构任务清单](agent/%E9%87%8D%E6%9E%84%E4%BB%BB%E5%8A%A1%E6%B8%85%E5%8D%95.md)。

## 当前实现

### 规则结果与 `ItemStack`

规则热路径由 `StackLimitHooks` 统一接入：

1. `ItemMixin` 修改 `Item#getItemStackLimit(ItemStack)` 的返回值。
2. `ItemStackMixin` 修改 `ItemStack#getMaxStackSize()`；如果没有可消费的解析标记，则重新解析当前栈。
3. `StackLimitHooks.applyDynamicStackLimit` 先取得原版基线，再由 `StackContextResolver` 构造上下文，交给
   `RuleRuntime.limitService()` 求值。
4. `StackLimitService` 按当前 `RuleSnapshot` 顺序执行命中的动作，并将结果限制在 `1..activeMaxStackSize`。

原版基线查询通过 `originalBaselineBypassDepth` 避免递归进入动态规则。规则快照和矿辞索引由 `RuleRuntime.replaceRuntime`
以一个运行态引用替换；规则重载由 `RuleRuntimeCoordinator` 负责，报告中的解析错误由 `RuleReloadPipeline` 收集。

源码入口：`src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt`、
`src/main/kotlin/io/alexjoest/stackupup/limit/StackLimitService.kt`、
`src/main/kotlin/io/alexjoest/stackupup/limit/RuleRuntime.kt`、
`src/main/kotlin/io/alexjoest/stackupup/RuleRuntimeCoordinator.kt`。

### 配置结构

`StackUpUpConfig` 的配置分为 `general`、`compat` 和 `client` 三组。`general.maxStackSize` 是全局兼容上限，默认值为
`64`；`compat.vanilla.craftingSlotLimit` 是工作台网格与合成结果槽的性能上限，默认值为 `64`，`0` 保持原版/默认行为，
而不是平衡设置。NuclearCraft 兼容配置位于 `compat.nuclearcraft`：`speedUpgradeLimit` 和 `energyUpgradeLimit` 默认值为
`0`，分别表示保留 NuclearCraft 的默认升级堆叠上限；正值才覆盖对应升级类型。配置重载由
`StackUpUpConfig.applyReloadControlledValues()` 统一应用。

启动容量策略由 `InventoryExpansionStartupConfig` 在 coremod 阶段从 `config/stackupup.cfg` 读取并缓存，修改后必须重启。`compat.inventoryExpansion.enabled` 默认 `true`（新/缺失配置采用 broad-default；已有配置中的显式 `false` 保持关闭）；显式 `false` 仅作为重启时的紧急回退，控制窄范围 IInventory getter transformer 与可选 capacity Mixin 配置；OFF 不会停用 ItemStack 规则、ordinary late 配置、常驻 early/baseline 配置或其中的专用写入安全 Mixin。`compat.inventoryStackLimitBlacklist.modIds` 是唯一容量黑名单输入：空列表为 no-op，所列 Mod 的可归属接收端/受支持公开 owner 按 Forge `ownedPackages` 精确包边界绕过容量扩展，并跳过该 Mod 的 capacity late 配置，不会禁用其 ordinary/safety 配置。继承黑名单父类或实现其接口本身不排除 addon；共享 ownerless 对象、私有 delegate、receiverless 构造路径仍可能 UNKNOWN。已加载但无归属包的 Mod 会导致启动检查失败，未加载 ID 记录无效果；当前没有 `classNames` 黑名单配置。

### 原版库存、槽位与网络路径

- `SlotLimitMixin` 先求物品动态上限；仅当 `inventory.getInventoryStackLimit() > 0` 时，才与 `Slot` 背后的
  `IInventory#getInventoryStackLimit()` 取较小值。`inventory.getInventoryStackLimit()` 返回非正值时不进入该 `min`
  分支，该非正值路径不属于此 mixin 的安全保证。
- `ContainerMixin` 通过 `ContainerInsertHooks` 将声明的槽位上限与库存上限合并后再参与合并。
- `VanillaInventoryLimitMixin` 覆盖编译期显式目标表内的一组原版库存实现（12 类，见 [T2b 原版目标表](agent/t2b-%E5%8E%9F%E7%89%88%E7%9B%AE%E6%A0%87%E8%A1%A8.md) §2），按 `getInventoryStackLimit()I` 返回值处理；启动期库存容量扩展关闭时，此 getter 注入返回原值。
- 旧的 vanilla inventory-write 上下文状态通道已随 T4a 移除；这不等于当前写入 helper 被移除。`InventoryWriteLimitMixin` 位于常驻 `mixins.stackupup.baseline.early.json`，对 `TileEntityLockableLoot#setInventorySlotContents` 调用 `StackLimitHooks.resolveInventoryWriteLimit`。
- `InventoryPlayerAddResourceMixin` 在 `canMergeStacks` 和 `addResource` 中各有一个直接调用 `resolveInventoryClampLimit`
  的业务点。这两个调用点仍然存在，不能按“没有调用方”处理。
- `StackCountCodec` 让大数量在网络包中可传输；`ItemStackNbtMixin`、`PacketBufferMixin` 和 `PacketUtilMixin`
  分别覆盖持久化或包读写入口。这里不引入文档中不存在的额外 NBT 字段。
- 创造模式包由 `NetHandlerPlayServerMixin` 按当前物品的动态上限校验；实体掉落合并和掉落拆分也有对应 early mixin。

### Forge `IItemHandler` 与当前槽位覆盖

Forge 1.12.2 的 `SlotItemHandler` 源码中：

- `getSlotStackLimit()` 直接查询 `itemHandler.getSlotLimit(index)`。
- `getItemStackLimit(ItemStack)` 会通过 `insertItem(..., true)` 模拟插入并计算可接受数量；可修改 handler 会暂时清空并恢复槽位。

当前 `SlotItemHandlerMixin` 只保留 `getItemStackLimit()` 的 `@ModifyReturnValue`（调用 `resolveItemHandlerSlotLimit`，按
`min(slotLimit, itemLimit)` 收敛）；`getSlotStackLimit()` 的独立动态上限注入已按 T3 移出，槽位上限自然跟随 handler 侧真实来源。
原先“在原值等于 64 时保持原值，否则与全局上限取较大值”的描述属 T3 前状态，与当前源码不符，不得再作为当前实现引用。

`ForgeItemHandlerLimitMixin` 当前的显式目标只有 `ItemStackHandler` 与 `EntityEquipmentInvWrapper` 两个自洽类；`InvWrapper`、
`SidedInvWrapper`、`CombinedInvWrapper`、`RangedWrapper` 已按 T3 移出注入（转发 wrapper 不是独立容量来源）。这是现状登记，不是对这些目标全部安全的结论。

AE2 相关内部槽类型和输出限制：项目代码尝试设置，第三方真实写入无源码不可判定。

## 容量安全不变量

### 广告值必须与真实写入面闭合

核心不变量是：

> 对外广告的容量不得大于真实写入路径能够承载的容量。

容量站点只能按写入源码判定：

- **自洽**：写入前重新读取与广告相同的上限来源，或使用与广告相同的底层容量来源。
- **断链**：只改了广告值，写入面不读取该值，也没有可靠 remainder。
- **转发**：查询或写入都委托给 delegate；wrapper 自身不是可以独立抬高的容量来源。

`IItemHandler` 的真实插入还必须满足 `storedDelta + remainderCount == offered`。这条等式只用于观察真实写入；`simulate=true`
不改变库存，不能当作落库证据，也不能为了审计再次执行一次真实插入。

### Forge wrapper 的源码事实

Forge `EntityEquipmentInvWrapper` 必须与实体的 vanilla 存储路径分开判定。当前 Forge 源码明确表明：

- `getSlotLimit()` 对装甲槽返回 1，对其他装备槽返回 64。
- `EntityEquipmentInvWrapper#insertItem()` 是该 wrapper 中计算本次 `limit` 并返回 `remainder` 的路径；它使用
  `getStackLimit()` 的 `min(getSlotLimit(slot), stack.getMaxStackSize())` 结果，扣除已有堆叠后，非模拟时写入空槽或增长已有堆叠。
- `setStackInSlot()` 不经过 `insertItem()` 的 `limit`/`remainder` 计算，而是直接调用实体的 vanilla setter；这是另一条写入路径，不能用
  `insertItem()` 的 remainder 事实替代。

因此不能把 Forge `EntityEquipmentInvWrapper` 写成“无余量必吞”；remainder 事实不自动证明实体底层
写入语义都安全，两条路径必须分别有源码和测试证据。源码位置：
`build/rfg/minecraft-src/java/net/minecraftforge/items/wrapper/EntityEquipmentInvWrapper.java`。

Forge `ItemStackHandler` 的源码会在 `insertItem()` 中再次使用 `getSlotLimit()` 与物品上限计算写入量，并返回
remainder；这是它自身写入链的事实。不能把这个结论传播给没有源码的第三方子类。

### 未知 handler 与转发 wrapper

容量准入规则是：未知 `IItemHandler` 不动态扩容。`getSlotLimit()` 只能说明接口报告的值；在看不到实现的情况下，不能按类名、接口实现关系或 `64` 这个数猜测它的写入容量。已移除的是广泛的 dynamic ASM replacement layer；当前另有启动期开关控制的窄范围 `InventoryStackLimitTransformer`，只处理非 Minecraft `IInventory` getter，不是未知 handler 的通用扩容入口。`SlotItemHandlerMixin` 仍处理槽位 `getItemStackLimit()` 结果，未知实现的真实写入容量仍不能由该注入或 getter 数值证明。

转发 wrapper 不能因为 wrapper 自己返回一个数就扩大广告：

- `InvWrapper` 和 `SidedInvWrapper` 的 `getSlotLimit()` 读取背后的 `IInventory` 上限，写入也调用背后库存的写入方法。
- `CombinedInvWrapper` 和 `RangedWrapper` 将查询和 `insertItem()` 转发给底层 handler。
- `VanillaDoubleChestItemHandler` 按实际访问的箱体转发上限和写入。

因此转发 wrapper 不应作为独立广告 patch 目标；应当先证明真实 delegate 的容量，再在 delegate 的真实写入面收敛。当前
`ForgeItemHandlerLimitMixin` 和 `Ae2ItemHandlerInsertLimiter` 仍保留部分 wrapper 目标或信任项，这是待 T3/T10 处理的
已知差距，不是完成证明。

### 禁止写入后余量补偿

禁止在真实写入后重新计算余量、回填、重试、补偿，或用这些动作改变业务结果。原因是：

1. handler 可能已经自行处理截断或溢出。
2. handler 可能改写输入 stack 的表示，调用方无法从事后状态可靠推断落库量。
3. 再次写入或回填会把吞物品问题变成复制物品问题，也可能重复执行模组副作用。

`Ae2ItemHandlerInsertLimiter` 是现有 AE2 投喂点的调用侧限流器。其 `simulate=false` 分支把一次 offered stack
分成多个分片，并在一次调用内循环进行多次真实 `handler.insertItem(..., false)`，按各分片的 remainder 累计 accepted 后生成最终
remainder。这是当前存在的多次真实分片及其守恒风险，不等同于真实写入完成后的补偿。禁止的写入后补偿是：真实写入后重新计算余量、回填、重试或再次写入，以改变业务结果；当前分片实现也不能作为这种做法的设计先例。T10/T12
必须明确审查并收敛它。未来守恒审计只能记录 `offered`、写入前后状态、remainder、handler 类名、slot 和调用点，不能修正结果。

## Coremod getter transformer 与 Mixin 分层

当前兼容路径由启动期开关控制的窄范围 ASM getter transformer 和分层 Mixin 配置组成。`StackUpUpCore` 仅在库存容量扩展开启且无冲突时注册 `InventoryStackLimitTransformer`，它只处理非 Minecraft 具体 `IInventory` 类的 `getInventoryStackLimit()I`（含 SRG 名称）；这不是已移除的广泛 dynamic ASM 替换层。`StackUpUpMixinConnector` 经 `IMixinConnector` 加入常驻 early/baseline 与 ordinary late 配置，并按启动开关分别加入 optional capacity early/late 配置；`modIds` 仅跳过 capacity late 配置。MixinBooter 的 `general.blacklistedConfigs` 是整配置停用功能，和 selective capacity blacklist 不同，可能连带停用被列入配置中的安全 Mixin。下文若引用 `DynamicCompatTransformer`、`DynamicCompatMethodProbe`、`CompatibilityLimitPatch` 或 `FixedCompatTargets`，仅表示历史实现和审计证据。

### Early/Baseline Mixin

无冲突时 connector 始终加入 ordinary `mixins.stackupup.early.json` 与 `mixins.stackupup.baseline.early.json`：前者覆盖 `Item`、`ItemStack`、库存合并、网络、数量持久化、渲染等基础路径；baseline 配置含 `Container`、`Slot`、库存 getter/write hooks 与 Forge `SlotItemHandler`。其中 `VanillaInventoryLimitMixin` 在启动容量开关关闭时保留 getter 原值；`InventoryWriteLimitMixin` 则始终注册，并通过 `resolveInventoryWriteLimit` 处理 `TileEntityLockableLoot#setInventorySlotContents` 写入。这条专用 writer 安全 Mixin 不由容量黑名单控制。

`mixins.stackupup.capacity.early.json` 是独立可选配置，仅库存容量扩展开启时加入；它承载 Forge 容量 Mixin，不因普通 early 配置是否加载而合并。`StackLimitHooks` 是这些入口共享的调用面。静态目标的 handler 使用 Java `private static` 方法；包裹调用使用 MixinExtras 的 `@WrapOperation`，修改返回值使用 `@ModifyReturnValue`，重载目标必须写完整 descriptor。

### Late Mixin

`StackUpUpMixinConnector` 分别登记 ordinary late 与 capacity late 模块，并按目标 Mod presence 加载 ordinary profiles。当前模块表有 18 个 ordinary late 模块，另有独立的 IC2 Classic ordinary 模块；ordinary 配置不受 `inventoryExpansion.enabled` 或 `modIds` 容量黑名单影响。容量开关开启时才加入 16 个 capacity late 配置与独立的 IC2 Classic capacity 配置；容量黑名单仅跳过与所列 Mod ID 对应的 capacity 配置。修改启动开关/黑名单需重启；MixinBooter `general.blacklistedConfigs` 是单个完整配置级别的停用方式。

- IC2 Classic 的 `mixins.stackupup.late.ic2classic.json` 是 ordinary 配置，`SlotBaseMixin` 将报告的 slot limit 限制在原生 64；独立的 `mixins.stackupup.capacity.late.ic2classic.json` 当前为空，仍与普通 safety Mixin 分开登记。
- Farmer's Delight Legacy 的 ordinary late 配置当前为空；可选 `mixins.stackupup.capacity.late.farmersdelight.json` 只声明 Basket、Cabinet、Cooking Pot、Skillet 的 getter Mixin 目标。

这些模块登记只说明 Mixin 配置和目标类的源码范围；不等于第三方真实写入容量已验证或 Mod 整体获得无损兼容。第三方源码缺失的写入语义仍按“无源码不可判定”处理。

### 历史 dynamic ASM 证据（已移除）

以下内容记录已移除的 dynamic ASM replacement layer，仅用于解释历史审计结论，不描述当前运行时路径：

1. `DynamicCompatTransformer` 曾在 `basicClass` 为空时直接返回，并在 `transformedName` 为空时使用备用类名。
2. `CoremodClassFilter` 曾过滤确定无关的基础运行时类。
3. `DynamicCompatMethodProbe` 曾只扫描当前类直接声明的方法。
4. `FixedCompatTargets` 曾让显式 Mixin 目标跳过 dynamic ASM。
5. `CompatibilityLimitPatch` 曾把目标方法中写死的原版 64 替换为 `StackLimitHooks.getCompatibilityStackSize()`；
   `ITEM_HANDLER` profile 曾不生成补丁。

历史 ASM 按方法名（含映射名）识别候选、按目标方法名查找 `BIPUSH 64` 字节码常量并替换，方法 descriptor 和常量所在的
真实写入/语义位置未闭合；命中方法名或字节码常量只说明历史补丁尝试，不构成真实容量写入证据。其 early path 使用 Java
以避免 Kotlin 标准库在 coremod 早期加载，但 `CompatibilityLimitPatch` 曾使用 `Consumer<ClassNode>` 和 lambda，
不满足 early path 的字节码禁用要求。已移除的是这一广泛 dynamic ASM layer。当前的 `InventoryStackLimitTransformer` 是独立的窄 getter transformer，只处理启用时的非 Minecraft `IInventory#getInventoryStackLimit()I`；不能把它描述成旧 dynamic 层的恢复，也不能把 getter 变换本身当作真实写入容量证据。T2/T11 登记须涵盖实际启用的 transformer 与对应 Mixin 配置，并继续按真实写入路径取证。

## 规则数据流

```text
.su / .su.md
  → RuleSourceLocator.resolveLoadOrder()
  → RuleReloadPipeline.loadDslRules()
      ├─ MarkdownStateParser / MarkdownRuleSource
      └─ DslRuleSource / RuleLineLoader / DslParser / RuleCompiler
  → RuleSnapshot + RuntimeContextRequirements
  → RuleRuntimeCoordinator.reload()
  → RuleRuntime.replaceRuntime()
  → StackLimitService.resolve(StackContext)
  → StackLimitHooks.*
  → StackUpUpMixinConnector 注册的 early/late Mixin
```

### 来源与编译

`RuleSourceLocator.resolveLoadOrder()` 的原始发现顺序是：仅当主 `main.su` 不存在时加入旧规则文件，随后是世界级
`main.su.md`、世界级 `world.su`、按文件名排序的配置目录 `.su`、按文件名排序的 `.su.md`、显式加入的主 `main.su` 和 `user.su`
。配置目录 `.su` 列表本身包含 `main.su`；locator 随后按 canonical path 去重（获取失败时回退到 absolute path），因此显式加入的
`main.su` 不会重复出现。

`RuleReloadPipeline.loadDslRules()` 在读取前对 `primaryRulesFile` 调用 `RuleFileTemplate.ensureExists`；缺失时会创建空的
`main.su`。这个创建动作对后续 `resolveLoadOrder()` 的 legacy 存在性判断的影响列在“已知限制”。

`RuleReloadPipeline.loadDslRules()` 不直接把这份发现顺序当作评估顺序，而是按后缀分流；最终快照先合并列表中保持原顺序的
Markdown 规则，再合并 DSL 规则，即 `markdownRules + dslRules`。每个文件内部的规则顺序保持不变。

- Markdown 规则只读取 `stackupup`/`su` fenced code block，并把标题 gate 与文件 state 合并到 `RuleGateContext`。
- DSL 行经 `RuleLineLoader` 清理注释后进入 tokenizer、parser、compiler；错误带来源和行号并停止当前加载。
- `RuleCompiler` 产出 `CompiledRule`；`RuleField`、`ComparisonOperator` 和 `RuleStepKind` 是静态强类型枚举。
- 动作按规则顺序作用于前一结果；`RuleAction` 自身保证结果至少为 1，服务最终再受全局上限约束。

### 运行时上下文与缓存

`StackContextResolver` 从 `ItemStack` 取得 registry id、namespace、metadata、物品类型和原版基线。昂贵或可选字段由
`RuleField.contextProviders` 形成 provider plan，只采集当前快照需要的矿辞、material 或 creative tab；resolver 不按字段名另写分支。
`RuleRuntime` 替换快照或矿辞索引时会新建 `StackLimitService`，从而刷新解析缓存。

1.12.2 的 `ResourceLocation.splitObjectName` 只按第一个冒号分割 namespace 与 path，path 可以保留多个冒号。DSL
不能按冒号数量拒绝资源路径，也不能未经 grammar 证据把第三段自动解释为 metadata。当前 `RuleLiteralMatcherCompiler` 已有整数
`@meta` 和基础通配；它仍保留旧的末段数字解析和非法 meta 静默回退。T7.2 规划的是 literal AST、非法输入 fail-fast、`@*`
以及多冒号语义的正式定型，不在本文宣称已完成。

## 已知限制

1. **容量目标尚未完成统一准入审计。** `ForgeItemHandlerLimitMixin` 当前只保留 `ItemStackHandler` 与 `EntityEquipmentInvWrapper`
   两个自洽目标（转发 wrapper 已按 T3 移出）；`SlotItemHandlerMixin` 只剩 `getItemStackLimit()` 收敛注入，原 `Math.max` 分支已移除；
   `Ae2ItemHandlerInsertLimiter` 仍是含按实现类型划分信任项的限幅工具类，但已不在 AE2 热路径上（仅供测试/探针直接使用），
   其真实插入分支仍会把一次 `offered` 分成多片循环写入——这是该工具类自身的多次真实分片风险，不等同于写入后的补偿，
   也不能替代 T2/T3/T10 的写入证据。
2. **原版目标表的闭合范围有限。** `VanillaInventoryLimitMixin` 已改为编译期显式目标表（12 类，`VanillaInventoryTargets.TARGETS`，
   含登记护栏测试），但表内结论只覆盖这 12 类的写入路径；表外目标（`InventoryLargeChest`、`TileEntityBeacon`、`ContainerEnchantment`
   匿名子类等）的处置不能由该表外推，且本文不把该表写成已完成独立复核的安全登记。
3. **第三方真实写入容量仍有 UNKNOWN。** 目标 Mod 的 Mixin 注册或我方目标类声明，不等于取得其真实 setter/handler 源码，也不证明容量守恒。仍须按第三方实际 write path 取证的登记目标包括 `appliedenergistics2`、`ae2`、`actuallyadditions`、`brandonscore`、`cyclopscore`、`enderio`、`ic2`（IC2 Classic 共用此 Mod ID）、`mantle`、`refinedstorage`、`storagenetwork`、`integrateddynamics`、`limelib`、`immersiveengineering`、`nuclearcraft`、`colossalchests`、`gregtech`、`techreborn`/`reborncore` 和 `farmersdelight`。本仓库 Farmer's Delight Mixin 只列出四个 getter 目标；IC2 Classic 的 `SlotBaseMixin` 是客户端仓库中的 ordinary safety config，不构成第三方 writer 实现证据。Tech Reborn 补丁仅尝试修改 RebornCore `Inventory#getInventoryStackLimit()`；AE2 内部槽与输出限制也只说明项目代码尝试设置，第三方真实写入仍按“无源码不可判定”处理（缺失依赖台账见 [T2a 登记表](agent/t2a-%E5%AE%B9%E9%87%8F%E7%AB%99%E7%82%B9%E7%99%BB%E8%AE%B0%E8%A1%A8.md) §5）。
4. **区分现行 writer hook 与已移除的旧状态通道。** `resolveInventoryClampLimit` 仍由 `InventoryPlayerAddResourceMixin` 的 `canMergeStacks` 与 `addResource` 直接调用；`InventoryWriteLimitMixin` 仍在 baseline early 配置中调用 `resolveInventoryWriteLimit` 处理 `TileEntityLockableLoot#setInventorySlotContents`。T4a 移除的是旧 inventory-write 上下文状态通道，不是上述 resolver 或当前 Mixin。
5. **非标准库存路径未闭合。** 完全绕过 vanilla `Slot`/`Container` 或 Forge 标准 handler 契约的自定义容器，需要独立取得真实写入证据；当前不因类名扩大容量。
6. **运行时审计尚未落地。** 现有 wrapper 诊断测试若只运行未打 mixin 的 Forge 基线，不能证明 mixin 生效态的守恒，也不能替代
   T12 的真实投喂报告。审计必须排除 `simulate=true`，并且只观察、不补偿。
7. **DSL 字面量仍有旧解析边界。** 多冒号 `ResourceLocation` path 的合法性不能由冒号数判断；在 T7.2
   完成前，不把旧末段数字启发式扩展成新的语法承诺。
8. **本地化重载缺少真实基线。** 当前 `ProxyClient` 只在语言代码变化时调用 `RuleMessages.syncLanguage`；标准 F3+T
   会重建语言资源，不能用某个局部 `LanguageMap` 调用或代码静态推断代替真实客户端 F3+T。T8.0 必须先记录同语言代码下资源重载前后的消息解析和注入结果。
9. **重载错误的原子发布语义尚未闭合。** `RuleReloadPipeline` 可以返回带错误的报告，而当前协调器仍把返回快照交给发布路径；不能把“报告含错误时保留旧运行态”当作当前保证。
10. **历史 broad dynamic ASM 层已移除；当前另有窄范围 getter transformer。** `DynamicCompatMethodProbe` 曾按当前类直接声明的方法名（含映射名）识别候选，`CompatibilityLimitPatch` 曾按目标方法名查找 `BIPUSH 64`；方法 descriptor 和该常量所在的真实写入/语义位置未闭合，且其 early path 曾有 `Consumer<ClassNode>` 与 lambda 的字节码禁用问题。当前 `InventoryStackLimitTransformer` 是 `StackUpUpCore` 在容量开关开启且无冲突时注册的 `IInventory#getInventoryStackLimit()I` 窄 transformer；Mixin 另有 early/baseline、ordinary late 和 optional capacity early/late 配置。两种形式的目标注册均不代替真实写入证据，必须逐路径取证。
11. **规则主文件创建会改变 legacy fallback。** `RuleSourceLocator.resolveLoadOrder()` 只在 `main.su` 不存在时加入旧规则文件，而
    `RuleReloadPipeline.loadDslRules()` 会在读取前创建缺失的 `main.su`；缺失的 `main.su` 被 pipeline 创建后，若旧
    `stackupup-rules.su` 仍存在，下一次 reload 将不再满足 legacy fallback 条件，加载结果可能改变。该迁移语义尚未显式定型。

## 规划事项

以下只引用 [重构任务清单](agent/%E9%87%8D%E6%9E%84%E4%BB%BB%E5%8A%A1%E6%B8%85%E5%8D%95.md) 的后续任务；本次文档更新没有执行它们的生产实现。
**T13 未完成；T3 的 Forge handler 目标收敛已按决策记录 §3.5 实施（本轮只按源码更新了本文描述），T11 的原版目标表已在源码中落地但验收状态以任务清单为准；
本文不把现有目标列表、诊断测试或 mixin 加载记录写成完成证明。**

- **T2a / T2b + T11：容量准入与原版目标表。** 依据 Forge/vanilla 的查询与写入源码建立“自洽 / 断链 / 转发”登记，替换历史运行时
  `== 64` 猜测；保留 `resolveInventoryClampLimit` 的两个直接业务调用点，并对 `InventoryLargeChest`、主动收紧的原版实现和匿名类单独取证，
  登记时区分 `InventoryStackLimitTransformer` 的 IInventory getter 目标与 Mixin 的 owner/name/descriptor，并分别闭合真实写入语义。
- **T3：Forge handler 目标收敛。** 重新审查 `ItemStackHandler`、各转发 wrapper、`EntityEquipmentInvWrapper` 与
  `SlotItemHandler`；不把 wrapper 作为独立广告 patch，不把 Forge wrapper 的 remainder 事实改写成“必吞”，并分别闭合 Forge 与
  vanilla entity 写入路径。
- **T4a / T4b：清理状态通道。** 先处理 inventory-write 上下文的业务语义；`markResolvedItemLimit`/`consumeResolvedItemLimit`
  必须等待缓存键和热路径方案验证，不得裸删。
- **T10：AE2 投喂限流器重排。** 按 T2/T3 的写入证据重新评估信任项，区分当前多次真实分片与禁止的写入后补偿，再区分静态准入与
  T12 运行时审计，不用类名替代行为证据。
- **T12.1–T12.5：透明守恒审计。** 只包围项目主动发起的真实 `insertItem(..., false)`，排除模拟调用，输出包含 `simulate`、
  `offered`、`before`、`after`、`remainder`、handler、slot 和调用点的机器可读报告；不回填、不重试、不补偿。
- **T13.1 / T13.2：覆盖面收缩与受控回扩。** 依赖 T3、T11 和 T12.5 的闭合证据，记录收缩后的功能缺口；任何回扩都必须同时接管真实写入面，不能只扩大
  GUI 或广告值。T13 未在当前实现中完成。
- **T7.0–T7.3：DSL 解析收敛。** 先补真实 token 位置，再定型 literal grammar、fail-fast 错误和多冒号 path 语义；不得按冒号数量拒绝
  1.12.2 合法 path。
- **T8.0–T8.3：本地化生命周期。** 先用真实客户端执行 F3+T 建立基线，再决定自有译表、重新注入和聊天路径；
  `LanguageMap.replaceWith` 或等价局部调用只能作为辅助验证。
- **T9-R1–R3：MixinBooter 版本调研。** 只依据官方源码、构建元数据和可重放的编译/运行结果决定是否升级，不在调研阶段改依赖或
  mixin。

规划中的容量决策理由和风险取舍以 [兼容决策记录](agent/compatibility-decision-record.md) 为准；新增目标必须回到任务清单的源码证据、remainder
守恒和“无源码不可判定”约束。
