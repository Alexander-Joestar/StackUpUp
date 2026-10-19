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

`StackUpUpConfig` 的配置分为 `general`、`compat`、`client` 和 `debug` 四组（`src/main/kotlin/io/alexjoest/stackupup/StackUpUpConfig.kt:19-37`）。`general.maxStackSize` 是全局兼容上限，默认值为
`64`；`compat.vanilla.craftingSlotLimit` 是工作台网格与合成结果槽的性能上限，默认值为 `64`，`0` 保持原版/默认行为，
而不是平衡设置。NuclearCraft 兼容配置位于 `compat.nuclearcraft`：`speedUpgradeLimit` 和 `energyUpgradeLimit` 默认值为
`0`，分别表示保留 NuclearCraft 的默认升级堆叠上限；正值才覆盖对应升级类型。`debug.mergeLimitTrace` 默认 `false`，
只门控容器合并上限解析的诊断日志，不改变解析结果（`StackUpUpConfig.kt:162-169`）。配置重载由
`StackUpUpConfig.applyReloadControlledValues()` 统一应用。

启动容量策略由 `InventoryExpansionStartupConfig` 在 coremod 阶段从 `config/stackupup.cfg` 读取并缓存，修改后必须重启。`compat.inventoryExpansion.enabled` 默认 `true`（新/缺失配置采用 broad-default；已有配置中的显式 `false` 保持关闭）；显式 `false` 仅作为重启时的紧急回退，控制结构化容量 getter transformer 与可选 capacity Mixin 配置；OFF 不会停用 ItemStack 规则、ordinary late 配置、常驻 early/baseline 配置或其中的专用写入安全 Mixin。`compat.inventoryStackLimitBlacklist.modIds` 是唯一容量黑名单输入：空列表为 no-op，所列 Mod 的可归属接收端/受支持公开 owner 按 Forge `ownedPackages` 精确包边界绕过容量扩展，并跳过该 Mod 的 capacity late 配置，不会禁用其 ordinary/safety 配置。继承黑名单父类或实现其接口本身不排除 addon；共享 ownerless 对象、私有 delegate、receiverless 构造路径仍可能 UNKNOWN。已加载但无归属包的 Mod 会导致启动检查失败，未加载 ID 记录无效果；当前没有 `classNames` 黑名单配置。

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

容量准入规则是：未知 `IItemHandler` 不动态扩容。`getSlotLimit()` 只能说明接口报告的值；在看不到实现的情况下，不能按类名、接口实现关系或 `64` 这个数猜测它的写入容量。已移除的是广泛的 dynamic ASM replacement layer；当前另有启动期开关控制的窄范围 `InventoryStackLimitTransformer`，按结构处理声明容量 getter 的 `IInventory`/`IItemHandler`/`Slot` 实现者（原版与第三方同一规则，含接口 default 方法与抽象基类的具体方法体，仅排除合成容器等配置托管类），不是未知 handler 的通用扩容入口。`SlotItemHandlerMixin` 仍处理槽位 `getItemStackLimit()` 结果，未知实现的真实写入容量仍不能由该注入或 getter 数值证明。

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

当前兼容路径由启动期开关控制的窄范围 ASM getter transformer 和分层 Mixin 配置组成。`StackUpUpCore` 仅在无冲突且库存容量扩展开启时注册 `InventoryStackLimitTransformer`（`src/main/kotlin/io/alexjoest/stackupup/StackUpUpCore.kt:79-92`），它不是已移除的广泛 dynamic ASM 替换层。`StackUpUpMixinConnector` 经 `IMixinConnector` 加入常驻 early/baseline 与 ordinary late 配置，并按启动开关分别加入 optional capacity early/late 配置；`modIds` 仅跳过 capacity late 配置。MixinBooter 的 `general.blacklistedConfigs` 是整配置停用功能，和 selective capacity blacklist 不同，可能连带停用被列入配置中的安全 Mixin。下文若引用 `DynamicCompatTransformer`、`DynamicCompatMethodProbe`、`CompatibilityLimitPatch` 或 `FixedCompatTargets`，仅表示历史实现和审计证据。

#### `InventoryStackLimitTransformer` 的匹配条件

1. 类加载时先读不可变策略快照：`InventoryStackLimitHook.classLoadPolicy()`；`isExpansionEnabled()` 为 `false` 时原样返回该类字节（`src/main/java/io/alexjoest/stackupup/core/InventoryStackLimitTransformer.java:92-96`）。
2. 目标判定只读类自身字节码元数据，不解析、不初始化目标类（`InventoryStackLimitTransformer.java:99-110,122-127`）。满足「实现 `IInventory`」「实现 `IItemHandler`」「继承 `Slot`」任一条件即为容量目标，命名空间不参与判定，原版具体类与第三方具体类走同一条规则（`src/main/java/io/alexjoest/stackupup/core/InventoryClassHierarchyRepository.java:30-59`）。
3. 排除表与结构跳过条件（`InventoryStackLimitTransformer.java:63-72,102-110,129-141`）：
   - 没有方法体可改的声明按**方法级**跳过（`ACC_ABSTRACT` / `ACC_NATIVE` / `ACC_STATIC`，其中 `ACC_STATIC` 是唯一有回归测试覆盖的那一位——静态方法没有 `this` 可发 receiver hook）；类级**不再**跳过接口与抽象类：接口 default 方法与抽象基类里的具体 getter 体同样被改写，未覆写该 getter 的实现者/子类由此自动继承扩容量；
   - 本 Mod 包 `io/alexjoest/stackupup/` 与其根类；
   - `net/minecraft/inventory/InventoryCrafting` 与 `net/minecraft/inventory/InventoryCraftResult`：这两个类的容量由 `compat.vanilla.craftingSlotLimit` 与 `VanillaInventoryLimitMixin` 托管，在这里替换字面量会盖过该配置；
   - 类加载时已落在黑名单包内的类（黑名单在策略发布后才完整，调用时判定仍会再拦一次，见下）。

#### 方法白名单与 literal 替换

只替换容量 getter 内的 `BIPUSH 64`（`InventoryStackLimitTransformer.java:143-169`）：

- `getInventoryStackLimit` / `func_70297_j_`，descriptor `()I`；
- `getSlotLimit`，descriptor `(I)I`；
- `getSlotStackLimit` / `func_75219_a`，descriptor `()I`；
- `getItemStackLimit` / `func_178170_b`，descriptor 经 `FMLDeobfuscatingRemapper.mapDesc` 归一后为 `(Lnet/minecraft/item/ItemStack;)I`。

抽象、本地与静态方法不在处理范围（方法级跳过，`InventoryStackLimitTransformer.java:163-166,177-185`）。接口 default 方法与抽象基类里的具体 getter 体则会被处理。命中时把该常量替换为 `aload_0 → Object.getClass() → sipush 64 → invokestatic InventoryStackLimitHook.resolveInventoryStackLimit(Class,int)`，其余字节码不变；整个类没有命中则返回原始字节数组（`InventoryStackLimitTransformer.java:112-115,186-206`）。处理过程抛出的 `RuntimeException` 会保留原类，不产生半补丁结果（`InventoryStackLimitTransformer.java:116-119`）。

`getSlotLimit(I)I` 这条 lane 只影响广告、不影响真实写入；常见情况下它与同一对象的 `getInventoryStackLimit()` 同值（两条 lane 由同一个兼容上限改写），但黑名单粒度会打破这个同值关系，见下文「调用时判定」末段。

#### 调用时判定

`InventoryStackLimitHook.ClassLoadPolicy` 是不可变快照：`shouldPatch(className)` = 扩展开关开启 且策略已 `prepared` 且类名不在黑名单包内；`resolveInventoryStackLimit` 再要求 `original == 64` 才返回兼容上限，否则保持原值（`src/main/java/io/alexjoest/stackupup/core/InventoryStackLimitHook.java:127-137`）。因此 transformer 只决定「这个类的 64 是否变成一次可判定的调用」，具体数值由每次 getter 调用时的策略快照决定；在策略发布前完成变换的类会先解析为原版 64，不会整局停留在未补丁状态（`InventoryStackLimitTransformer.java:34-36`）。

**`getSlotLimit` 与真实写入上限的同值是有条件的（源码 + 生产 jar 字节码 + 调用方代码证据，非运行时验证）：** `McJtyLib` 的 `NullSidedInvWrapper` / `FacedSidedInvWrapper` 与 RFTools 的 `ItemFilterInvWrapper` 的 `getSlotLimit(int)` 是写死的 `return 64;`（`McJtyLib/src/main/java/mcjty/lib/varia/NullSidedInvWrapper.java:181-183`、`FacedSidedInvWrapper.java:193-195`、`RFTools/src/main/java/mcjty/rftools/blocks/itemfilter/ItemFilterInvWrapper.java:187-189`），它们作为具体类被 transformer 改写；承载它们的 TE 的 `getInventoryStackLimit()` 同样是字面 64（`DefaultSidedInventory.java:54-56`、`ItemFilterTileEntity.java:112-115`）并走同一条改写，所以两条 lane 命中同一个兼容上限，正常情况下 `wrapper.getSlotLimit(slot) == inv.getInventoryStackLimit()`。**但黑名单按 owned package 匹配，wrapper 在 `mcjty.lib.*`（mcjtylib）、TE 在 `mcjty.rftools.*`（rftools）**：只拉黑 `rftools` 会让 TE 回到 64 而 wrapper 仍广告兼容上限，此时「广告 > 真实」成立。后果有限——`insertItem` 读的是 `inv.getInventoryStackLimit()`（真实值），`getSlotLimit` 只影响广告，已知读取方是 RS 的 `StorageExternalItem.getCapacity()`、AE2 `ContainerAdapter.getInventoryStackLimit()` 与 EnderIO `ItemTools.hasFreeSpace`，都不丢物，代价是余量显示偏高加少量白跑的插入尝试；要避免则同时拉黑 `mcjtylib`。

#### 策略发布时序

- `configureExpansionEnabled` 由 coremod 注册阶段与 connector 的 `connect()` 各调用一次；`prepare(compatibilityLimit, blacklistedPackages)` 才把策略标记为 `prepared`。决策冻结后只允许关闭，不允许重新开启（`InventoryStackLimitHook.java:23-31,46-54`、`StackUpUpCore.kt:85`、`src/main/kotlin/io/alexjoest/stackupup/bootstrap/StackUpUpMixinConnector.kt:44`）。
- `prepare` 的实际调用点在 `StackUpUp.preInit`：`handleConfigChanged()` → `StackLimitHooks.refreshInventoryStackLimitBlacklist()`，后者用 `Loader.instance().modList` 的 `ownedPackages` 把配置的 Mod ID 展开成包名，再调用 `InventoryStackLimitHook.prepare(StackUpUpConfig.maxStackSize, packages)`（`src/main/kotlin/io/alexjoest/stackupup/StackUpUp.kt:47-51,83`、`src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt:139-172`）。黑名单为空时不展开 `ownedPackages`，直接以空数组 prepare（`StackLimitHooks.kt:145-151`）。
- 启动策略直接读 `config/stackupup.cfg`，不初始化 Forge ConfigManager（`src/main/java/io/alexjoest/stackupup/core/InventoryExpansionStartupConfig.java:14-40,50-110`）。策略已发布后，配置变更只会更新兼容上限（`StackLimitHooks.kt:141-143`）；改变开关或黑名单必须重启。

读侧扩容不扩大写侧：`InventoryStackLimitTransformer` 只改容量 getter，`InventoryWriteLimitMixin` 只包裹 `TileEntityLockableLoot#setInventorySlotContents` 这一处声明，因此它覆盖继承该 setter 的原版子类（`TileEntityChest`、`TileEntityDispenser`、`TileEntityShulkerBox`），不覆盖 `TileEntityFurnace` 与 `TileEntityBrewingStand`（二者继承 `TileEntityLockable`、各自声明 setter），不覆盖 `TileEntityHopper`（重写了 setter），也不覆盖 `EntityMinecartContainer`（抽象类、自带 setter）。这些未被该 Mixin 覆盖的类在自己的 setter 里按同一 getter 夹取（`TileEntityBrewingStand` 不夹取），所以“广告值 = 落库值”仍自洽，但它们没有 item-aware 写入规则。`EntityMinecartContainer` 虽然是抽象类，但它的 `getInventoryStackLimit()` 是方法体里的字面量 `return 64;`（`build/rfg/minecraft-src/java/net/minecraft/entity/item/EntityMinecartContainer.java:165-168`），因此现在会被 transformer 改写，`EntityMinecartChest`/`EntityMinecartHopper` 从继承的声明解析到兼容上限；它们继承的自有 setter（`:111-119`）落盘前又用同一个 getter 夹取，所以抬高读侧与真实写入仍然自洽。`VanillaInventoryLimitMixin` 按 receiver 类精确匹配，不参与这条路径。

### Early/Baseline Mixin

无冲突时 connector 始终加入 ordinary `mixins.stackupup.early.json` 与 `mixins.stackupup.baseline.early.json`：前者覆盖 `Item`、`ItemStack`、库存合并、网络、数量持久化、渲染等基础路径；baseline 配置含 `Container`、`Slot`、库存 getter/write hooks 与 Forge `SlotItemHandler`。其中 `VanillaInventoryLimitMixin` 在启动容量开关关闭时保留 getter 原值；`InventoryWriteLimitMixin` 则始终注册，并通过 `resolveInventoryWriteLimit` 处理 `TileEntityLockableLoot#setInventorySlotContents` 写入。这条专用 writer 安全 Mixin 不由容量黑名单控制。

`mixins.stackupup.capacity.early.json` 是独立可选配置，仅库存容量扩展开启时加入；它承载 Forge 容量 Mixin，不因普通 early 配置是否加载而合并。`StackLimitHooks` 是这些入口共享的调用面。静态目标的 handler 使用 Java `private static` 方法；包裹调用使用 MixinExtras 的 `@WrapOperation`，修改返回值使用 `@ModifyReturnValue`，重载目标必须写完整 descriptor。

### Late Mixin

`StackUpUpMixinConnector` 分别登记 ordinary late 与 capacity late 模块，并按目标 Mod presence 加载 ordinary profiles。当前模块表有 4 个 ordinary late 配置（`StackUpUpMixinConnector.kt:304-309`），加 1 条 IC2 Classic ordinary 配置（`:311-313`），合计 5 个；ordinary 配置不受 `inventoryExpansion.enabled` 或 `modIds` 容量黑名单影响。容量开关开启时才加入 `capacityModules` 的 16 条 capacity late 配置（`:315-332`）；两张表分开登记、不是一一对应。容量黑名单仅跳过与所列 Mod ID 对应的 capacity 配置（`:148-158`）。修改启动开关/黑名单需重启；MixinBooter `general.blacklistedConfigs` 是单个完整配置级别的停用方式。

- **哪些 per-mod mixin 还需要保留，要按「transformer 是否真的能命中」逐个判定，不能只看方法体里有没有字面 64。** 判定需要两个条件同时成立：①目标类确实 `implements IInventory`/`IItemHandler` 或 `extends Slot`（`InventoryClassHierarchyRepository.isStackLimitTarget` 的匹配条件）；②getter 体内确实是字面 `bipush 64`。`InventoryStackLimitTransformer` 本身只按**方法级** `ACC_ABSTRACT`/`ACC_NATIVE`/`ACC_STATIC` 跳过没有方法体的声明（`src/main/java/io/alexjoest/stackupup/core/InventoryStackLimitTransformer.java:163-166,177-185`），类级不跳过抽象类与接口，所以「字面量写在抽象基类或接口 default 方法体里」本身不构成保留理由。
- Mystical Agriculture 的 `TileEssenceReprocessor` 同时满足两个条件（`implements ISidedInventory` + 纯字面 `bipush 64`），因此 `MysticalAgricultureInventoryLimitMixin` 与 `mixins.stackupup.capacity.late.mysticalagriculture.json` 已在 0.2.5.1 删除，容量改由 transformer 提供，**行为不变**。
- EnderIO 的 `AbstractInventoryMachineEntity` **只满足条件②，不满足条件①**，所以那条 mixin 不能删：`javap -p -c EnderIO-CEu-5.5.1.jar` 显示它自身声明里没有 `IInventory`，整条继承链（`AbstractMachineEntity` → `TileEntityEio` → `com.enderio.core.common.TileEntityBase` → `TileEntity`）也没有；`IInventory` 桥接是独立的内部类 `AbstractInventoryMachineEntity$InventoryWrapper`（其 `func_70297_j_()` 转发给 `this$0.getInventoryStackLimit()`）。`isStackLimitTarget` 因此不匹配外层类，transformer 从不会改写它的 `bipush 64`。该 javadoc 此前写成「transformer 也会改写，本 mixin 退化为 passthrough」，是错的，现已订正。对照案例是两个同名不同类：`TileEnchanter` **确实** `implement ISidedInventory`，它的 getter 是真目标，因此该条目作为重复登记被删除。
- `ae2supergiant` 的 `ContainerAdapterMixin` 的 `64` 是 `Math.min` 折叠的种子而非广告上限，保留它是为了让「AE2S 决策 + 黑名单门」显式可见，而不是交给一次盲目的字面量改写。
- Mystical Agriculture：**0.2.5.1 起已无独立配置**——`mixins.stackupup.capacity.late.mysticalagriculture.json`、`MysticalAgricultureInventoryLimitMixin` 与其结构测试 `MysticalAgricultureMixinSourceTest.kt` 均已删除。原目标是抽象基类 `com.blakebr0.mysticalagriculture.tileentity.reprocessor.TileEssenceReprocessor`：`javap` 读 `MysticalAgriculture-1.12.2-1.7.5.jar` 可见它是 `public abstract class ... implements ISidedInventory`、getter 体为 `bipush 64; ireturn`，6 个具体子类（Inferium/Intermedium/Prudentium/Superium/Supremium/Ultimate Reprocessor）都不声明该 getter。两个条件（真实现接口 + 纯字面 64）都成立，所以 transformer 本来就能覆盖，mixin 是重复登记。删除后其容量行为由 `InventoryStackLimitTransformerTest` 覆盖：构造 reprocessor 形状、变换抽象基类、在不声明 getter 的具体子类实例上调用 getter，断言兼容上限经继承到达（未变换的对照仍读 64）。写入侧未改动也不需要改：`func_70299_a`（`setInventorySlotContents`）落盘前调用同一个 `func_70297_j_()` 并用 `ItemStack.func_190920_e` 夹回该值（同一 jar 字节码，`func_70299_a` 偏移 57-71），所以抬高 getter 等于抬高真实落库上限。
- RFTools：只有一条 capacity late 配置 `mixins.stackupup.capacity.late.rftools.json`（登记 modId `rftools`，`StackUpUpMixinConnector.kt:331`），没有 ordinary 配置。容量的读侧主要靠公共改写：`mcjty.lib.container.DefaultSidedInventory` 的 `getInventoryStackLimit()` 是接口 default 方法里的字面量 `return 64;`（`McJtyLib/src/main/java/mcjty/lib/container/DefaultSidedInventory.java:54-56`），transformer 现在会改写它，因此 Modular Storage、Storage Scanner 等实现者自动继承扩容量。唯一的显式补丁是安全性的：`StorageScannerTileEntity#update` 把未注入完的余量写回输入槽时，`InventoryHelper.setInventorySlotContents(stackLimit, index, stack)` 的第一个参数是上限而不是槽位下标，`McJtyLib` 会就地把 `stack` 截断到该上限（`McJtyLib/src/main/java/mcjty/lib/container/InventoryHelper.java:456-488`）。`RftoolsStorageScannerLimitMixin` 用 `@ModifyArg(index = 0)` 把该参数换成 receiver 自己的 `getInventoryStackLimit()`（`src/main/java/io/alexjoest/stackupup/mixin/late/rftools/RftoolsStorageScannerLimitMixin.java`），两个调用点共用同一个注入点 descriptor。它不改写任何写入方法，也不额外判断扩展开关——`getInventoryStackLimit()` 本身已承载开关与黑名单。**该修复已由维护者实机确认**（无可用路由存储时放入 >64 不再被截断），实机只覆盖这一条路径。
- IC2 Classic 的 `mixins.stackupup.late.ic2classic.json` 是 ordinary 配置，`SlotBaseMixin` 将报告的 slot limit 限制在原生 64；capacity 表里没有 `ic2classic` 条目（`mixins.stackupup.capacity.late.ic2classic.json` 这个文件不存在），与 IC2 相关的是 `mixins.stackupup.capacity.late.ic2.json`（登记 modId `ic2`）。
- Farmer's Delight Legacy 已没有独立配置（ordinary 与 capacity 两份 `farmersdelight` JSON 及其 Mixin 类已删除）：其 Basket、Cabinet、Cooking Pot、Skillet 是具体 `IInventory` 实现，`getInventoryStackLimit()` 为字面量 `return 64;`，由 `InventoryStackLimitTransformer` 在类加载时改写。仓库内以真实类名生成等价字节码的测试是 `src/test/kotlin/io/alexjoest/stackupup/core/FarmersDelightAsmLiteralPatchTest.kt`，它不是第三方运行时验证。

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
不满足 early path 的字节码禁用要求。已移除的是这一广泛 dynamic ASM layer。当前的 `InventoryStackLimitTransformer` 是独立的窄 getter transformer，启用时按结构处理具体实现者的 `getInventoryStackLimit()I` 等容量 getter（原版类同样命中）；不能把它描述成旧 dynamic 层的恢复，也不能把 getter 变换本身当作真实写入容量证据。T2/T11 登记须涵盖实际启用的 transformer 与对应 Mixin 配置，并继续按真实写入路径取证。

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

`StackContentKey` 是 ItemStack 到已解析上限的内容缓存键，固定只由 `(itemId, metadata)` 构成：`itemId` 使用 registry name，未注册物品
（测试场景）回退到物品类名。它不持有 `ItemStack` 实例，也不把 `count` 或 `NBTTagCompound` 放进键；因此同一 item ID/metadata 的堆栈在
count 或 NBT 变化时不会被重新分键或使这项内容缓存失效，而 metadata 变化会得到新键。快照/矿辞索引替换时，`RuleRuntime` 新建
`StackLimitService`；服务 epoch 会清空内容缓存，服务引用校验也阻止旧快照值泄漏到新快照。

规则 matcher 的 `readFields()` 机械推导字段覆盖，`RuleContextRequirements` 再按策略裁剪缓存键材料：item/mod/type/meta/size 由固定
身份分量承载，material/tab 才把字段值加入键，ORE 由 item ID+metadata 的稳定身份决定但会让服务避开零分配快路径。缓存键材料至多一个
且不读 ORE 的快照使用 `StackLimitService` 的开放寻址 `FastResolvedCache`，命中不创建中间键对象；多字段或含 ORE 的快照使用
`ConcurrentHashMap` 慢路径。

单个 item 比较的字面量不含 `*` 时编译为 `ExactItemMatcher`，比较 item ID 并按需比较 `@整数` metadata；字面量含 `*` 时使用
`ItemPatternMatcher`，`@*` 虽然语义上不约束 metadata，仍属于通用 pattern 分支。若快照恰好只有一条正向 `item = *` 规则且动作恰为
单个 `SET`，`StackLimitService` 直接检查 `baseLimit > 1`、应用 SET 并执行 `1..maxStackSize` 限制，绕过通用缓存/map 和 matcher
遍历；`baseLimit <= 1` 不命中该规则并保留非堆叠基线。额外规则、`item != *`、动作链或非 SET 动作均回到通用路径。

1.12.2 的 `ResourceLocation.splitObjectName` 只按第一个冒号分割 namespace 与 path，path 可以保留多个冒号。DSL
不能按冒号数量拒绝资源路径，也不能未经 grammar 证据把第三段自动解释为 metadata。当前 `RuleLiteralMatcherCompiler` 通过
`ItemLiteralSyntax` 定型 `@整数`、`@*` 和多冒号 path 语义；非法 `@` 写法 fail-fast，不再使用旧的末段数字解析或静默回退。

## 已知限制

1. **容量目标尚未完成统一准入审计。** `ForgeItemHandlerLimitMixin` 当前只保留 `ItemStackHandler` 与 `EntityEquipmentInvWrapper`
   两个自洽目标（转发 wrapper 已按 T3 移出）；`SlotItemHandlerMixin` 只剩 `getItemStackLimit()` 收敛注入，原 `Math.max` 分支已移除；
   `Ae2ItemHandlerInsertLimiter` 仍是含按实现类型划分信任项的限幅工具类，但已不在 AE2 热路径上（仅供测试/探针直接使用），
   其真实插入分支仍会把一次 `offered` 分成多片循环写入——这是该工具类自身的多次真实分片风险，不等同于写入后的补偿，
   也不能替代 T2/T3/T10 的写入证据。
2. **原版目标表的闭合范围有限。** `VanillaInventoryLimitMixin` 已改为编译期显式目标表（12 类，`VanillaInventoryTargets.TARGETS`，
   含登记护栏测试），但表内结论只覆盖这 12 类的写入路径；表外目标（`InventoryLargeChest`、`TileEntityBeacon`、`ContainerEnchantment`
   匿名子类等）的处置不能由该表外推，且本文不把该表写成已完成独立复核的安全登记。
3. **第三方真实写入容量仍有 UNKNOWN。** 目标 Mod 的 Mixin 注册或我方目标类声明，不等于取得其真实 setter/handler 源码，也不证明容量守恒。仍须按第三方实际 write path 取证的登记目标包括 `appliedenergistics2`、`ae2`、`actuallyadditions`、`brandonscore`、`cyclopscore`、`enderio`、`ic2`（IC2 Classic 共用此 Mod ID）、`mantle`、`refinedstorage`、`storagenetwork`、`integrateddynamics`、`limelib`、`immersiveengineering`、`nuclearcraft`、`colossalchests`、`gregtech`、`techreborn`/`reborncore`、`mysticalagriculture` 和 `farmersdelight`。Mystical Agriculture 的 `TileEssenceReprocessor` 行附有本地 jar 的读侧字节码证据（`func_70299_a` 落盘前调用同一容量 getter 夹取），但仍无运行期验证。Farmer's Delight Legacy 已没有自己的 Mixin 配置，其容量只由结构化 transformer 的 literal 补丁承担，因此连 getter 目标清单都不再由本仓库的 Mixin 声明表达，第三方写入语义仍不可判定。IC2 Classic 的 `SlotBaseMixin` 是 ordinary safety config，不构成第三方 writer 实现证据。Tech Reborn 补丁仅尝试修改 RebornCore `Inventory#getInventoryStackLimit()`；AE2 内部槽与输出限制也只说明项目代码尝试设置，第三方真实写入仍按“无源码不可判定”处理（缺失依赖台账见 [T2a 登记表](agent/t2a-%E5%AE%B9%E9%87%8F%E7%AB%99%E7%82%B9%E7%99%BB%E8%AE%B0%E8%A1%A8.md) §5）。
4. **区分现行 writer hook 与已移除的旧状态通道。** `resolveInventoryClampLimit` 仍由 `InventoryPlayerAddResourceMixin` 的 `canMergeStacks` 与 `addResource` 直接调用；`InventoryWriteLimitMixin` 仍在 baseline early 配置中调用 `resolveInventoryWriteLimit` 处理 `TileEntityLockableLoot#setInventorySlotContents`。T4a 移除的是旧 inventory-write 上下文状态通道，不是上述 resolver 或当前 Mixin。
5. **非标准库存路径未闭合。** 完全绕过 vanilla `Slot`/`Container` 或 Forge 标准 handler 契约的自定义容器，需要独立取得真实写入证据；当前不因类名扩大容量。
6. **运行时审计尚未落地。** 现有 wrapper 诊断测试若只运行未打 mixin 的 Forge 基线，不能证明 mixin 生效态的守恒，也不能替代
   T12 的真实投喂报告。审计必须排除 `simulate=true`，并且只观察、不补偿。
7. **DSL 其余诊断仍有边界。** `ItemLiteralSyntax` 已定型 `@整数`、`@*` 和多冒号 `ResourceLocation` path 语义；非法 `@` 写法
   fail-fast，不能再把旧末段数字启发式当作当前语法。其他 DSL 错误的列级定位和更广泛的 tokenizer/AST 工作仍按规则示例中的测试覆盖边界处理。
8. **本地化重载缺少真实基线。** 当前 `ProxyClient` 只在语言代码变化时调用 `RuleMessages.syncLanguage`；标准 F3+T
   会重建语言资源，不能用某个局部 `LanguageMap` 调用或代码静态推断代替真实客户端 F3+T。T8.0 必须先记录同语言代码下资源重载前后的消息解析和注入结果。
9. **重载错误的原子发布语义尚未闭合。** `RuleReloadPipeline` 可以返回带错误的报告，而当前协调器仍把返回快照交给发布路径；不能把“报告含错误时保留旧运行态”当作当前保证。
10. **历史 broad dynamic ASM 层已移除；当前另有窄范围 getter transformer。** `DynamicCompatMethodProbe` 曾按当前类直接声明的方法名（含映射名）识别候选，`CompatibilityLimitPatch` 曾按目标方法名查找 `BIPUSH 64`；方法 descriptor 和该常量所在的真实写入/语义位置未闭合，且其 early path 曾有 `Consumer<ClassNode>` 与 lambda 的字节码禁用问题。当前 `InventoryStackLimitTransformer` 是 `StackUpUpCore` 在容量开关开启且无冲突时注册的窄 transformer，按结构处理声明容量 getter 的 `IInventory`/`IItemHandler`/`Slot` 实现者（含原版具体类、接口 default 方法与抽象基类的具体方法体，只排除配置托管类与本 Mod 包；方法级跳过 abstract/native/static 声明，见本文「Coremod getter transformer 与 Mixin 分层」）；Mixin 另有 early/baseline、ordinary late 和 optional capacity early/late 配置。两种形式的目标注册均不代替真实写入证据，必须逐路径取证。
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
- **T7.0–T7.3：DSL 解析的剩余收敛。** item literal grammar、非法 `@` fail-fast 与多冒号 path 语义已由 `ItemLiteralSyntax` 落地；剩余
  tokenizer/AST 及其他错误的列级定位仍按规则示例中的已知限制推进，不得按冒号数量拒绝 1.12.2 合法 path。
- **T8.0–T8.3：本地化生命周期。** 先用真实客户端执行 F3+T 建立基线，再决定自有译表、重新注入和聊天路径；
  `LanguageMap.replaceWith` 或等价局部调用只能作为辅助验证。
- **T9-R1–R3：MixinBooter 版本调研。** 只依据官方源码、构建元数据和可重放的编译/运行结果决定是否升级，不在调研阶段改依赖或
  mixin。

规划中的容量决策理由和风险取舍以 [兼容决策记录](agent/compatibility-decision-record.md) 为准；新增目标必须回到任务清单的源码证据、remainder
守恒和“无源码不可判定”约束。
