# 兼容取舍与决策记录

本文件是 StackUpUp 兼容性取舍、准入理由和回扩条件的唯一决策记录。它只记录容量安全、Forge/vanilla
写入路径和已知模组兼容边界；不替代源码，也不实现后续任务。

文中术语固定如下：

- **已决策**：当前必须遵守的行为和安全底线。
- **当前实现**：源码中已经存在的行为；不等于最终准入结论。
- **当前限制**：已知但尚未解决的覆盖面或证据缺口。
- **拟议任务**：需要单独实现和验证的后续工作。本次文档改动不执行 T2–T13。

## 1. 已决策

### 1.1 容量安全不变量

对外广告的容量不得大于真实写入路径能够承载的容量。判断依据必须是目标对象的查询、限制和写入源码，不能依据类名、`== 64`
哨兵、注释或“主动表态”猜测。

对一次真实 `IItemHandler#insertItem`，守恒审计使用：

```text
storedDelta + remainderCount == offered
```

这只是只读审计公式，不是写入后的修复算法。

### 1.2 容量站点三分类与证据状态

对已有充分源码或可重复行为证据的 patch 目标，必须按源码归入且仅归入下列三类；证据不足的目标另记为下述独立证据状态：

- **自洽**：非转发目标的查询与真实写入使用同一上限，或插入逻辑依据同一上限计算并按契约返回 remainder。
- **转发**：查询和写入转发给 delegate；包装器自身只归入此类，不是可独立抬高的容量来源。delegate 另行分类，不得抬高包装器。
- **断链**：源码证明广告值与真实写入限制不同源，且没有可靠的写入面接管或 remainder 契约。

**独立证据状态：** **无源码不可判定**表示第三方源码或可重复行为证据缺失；它不是第四类容量站点。处于该状态的目标不得被强行归入上述三类，也不得改写成“断链”“安全”或任何确定性结论。

只有“自洽”目标，或“转发”链已同时接管 delegate 真实写入面的目标，才可进入扩大覆盖面的实现任务。处于上述独立证据状态的实现保持安全上限，不动态扩容。

### 1.3 兼容实现顺序

1. 目标类、方法和完整 descriptor 明确，优先使用按模组加载的 late mixin。
2. 原版或 Forge 基础路径使用 early mixin。
3. 已由 mixin 接管的目标必须从动态 ASM 跳过表核对，不能重复注入。
4. 只有 mixin 无法表达且有明确写入证据时，才考虑 ASM。

新的兼容补丁不按上游 StackUp 的 `IInventory`/`Slot` 继承关系无条件放大，也不把动态 ASM 扩展到未知 `IItemHandler`。

### 1.4 未知 `IItemHandler` 不扩容

`IItemHandler#getSlotLimit(slot)` 是 handler 的容量承诺，但未知实现的返回值不能证明其内部 setter、`insertItem`
或持久化路径能够承载更大堆叠。即使返回值字面上是 64，也不因此抬高 slot 广告。

未知 handler 继续按真实可见上限处理；需要扩大时，必须先取得对应版本的写入源码或可重复的真实守恒证据，并为具体内层目标建立显式兼容任务。

### 1.5 否决写入后余量补偿

不恢复或新增“真实写入后重新计算余量、回填源栈/光标、重试或补偿”的写入后余量补偿方案。写入后无法可靠区分以下情况：

- 目标库存截断并丢弃了多余数量；
- 目标库存改写了传入 stack；
- 模组已经自行保存或返回溢出物；
- 自定义容器替换了槽位或绕过了 vanilla 流程；
- 客户端副本与服务端库存暂时不同步。

事后回填可能把吞物品变成复制物品，或与模组自己的溢出语义冲突。正常调用 `insertItem` 所要求的返回值可以作为该次 API
调用的契约结果；它不能被扩展成写入后的业务补偿层。预先按固定 cap 把 offered 拆成多个彼此独立的上限内插入调用，可以是调用方限幅，但每个
chunk 的 remainder 只能决定该 chunk 的返回值和进度，不能重新投喂已经接受的数量，也不能依据写入后的状态改写业务结果。守恒审计只观察、记录、报告，不改变业务结果。

### 1.6 机器和模组兼容

机器兼容先核对真实写入面，再决定是否需要容量补丁：

- `IInventory#getInventoryStackLimit()`；
- `IItemHandler#getSlotLimit()` 与 `insertItem()`；
- `setInventorySlotContents()` 或模组自己的持久化 setter；
- 写入前是否再次读取同一上限，及真实 remainder 是否可靠。

不先改 `Slot#getItemStackLimit`、GUI 数字或客户端显示来掩盖服务端容量问题。客户端显示问题和服务端真实库存问题分开验证。重载方法必须登记完整
descriptor。

## 2. 当前实现（源码事实）

以下内容描述当前代码，不代表 T3/T10/T11 已完成收敛：

- `src/main/java/io/alexjoest/stackupup/mixin/early/ForgeItemHandlerLimitMixin.java:15-32` 当前把 `ItemStackHandler`、
  `EntityEquipmentInvWrapper`、`InvWrapper`、`SidedInvWrapper`、`CombinedInvWrapper` 和 `RangedWrapper` 的 `getSlotLimit`
  返回值纳入同一个 `original == 64` 分支。
- `src/main/java/io/alexjoest/stackupup/mixin/early/SlotItemHandlerMixin.java:19-39` 当前仅在原值 `original == 64`
  时保持原值，其他值可能进入 `Math.max` 分支；这是已知限制，不能作为未知 handler 的安全证明。
- `src/main/java/io/alexjoest/stackupup/mixin/early/VanillaInventoryLimitMixin.java:22-53` 当前覆盖多类 vanilla
  `IInventory`，并先调用 `StackLimitHooks.resolveInventoryWriteLimit`，再处理 64 哨兵。这个运行时目标集合仍需 T11
  的编译期登记表收敛。
- `src/main/java/io/alexjoest/stackupup/core/Ae2ItemHandlerInsertLimiter.java:18-75` 当前对不在白名单的 handler 使用
  `min(64, getSlotLimit(slot))` 分片投喂；`simulate=false` 的 `:42-55` 会把每个不超过 cap 的 chunk 作为独立真实插入，遇到
  remainder 或零进度就停止并返回原始剩余量。它不是对已写入数量的事后回填，但也不因此自动获准；T10 必须证明这是预先限幅而非补偿式重试，并让
  T12 按每次真实调用审计。白名单由 `:77-84` 的类型判断决定，类型列表和真实写入契约必须重新核验，不能把类型名当作安全证据。
- `src/main/java/io/alexjoest/stackupup/mixin/late/AppEngPatternTermMixin.java:15-29` 当前只在 AE2 三类 pattern terminal
  构造结束后提升空白 pattern 输入槽 `patternSlotIN`；该项目代码不证明 AE2 其他槽或第三方内部写入容量，相关目标仍须按源码和运行证据审查。
- `StackLimitHooks.resolveInventoryClampLimit` 定义于
  `src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt:181-192`。当前两个 `InventoryPlayer` 业务调用点是
  `src/main/java/io/alexjoest/stackupup/mixin/early/InventoryPlayerAddResourceMixin.java:20` 和 `:42`。
  `resolveInventoryWriteLimit` 在 `StackLimitHooks.kt:175-177` 仍有桥接调用；T4a 若移除 inventory-write 状态通道，必须处理该桥接并保留
  resolver 及上述两个调用点，不能以“没有调用方”为由删除。

## 3. Forge 与 vanilla 写入事实

### 3.1 Forge handler 与 wrapper

| 目标                                   | 查询与写入证据                                                                                                                                                                                                                                                                                                                                      | 当前决策边界                                                                                                                                                                                                           |
|----------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ItemStackHandler`                     | `getSlotLimit()` 返回 64：`build/rfg/minecraft-src/java/net/minecraftforge/items/ItemStackHandler.java:156-160`；`insertItem()` 在 `:79-116` 调用 `getStackLimit()`，该方法在 `:162-165` 取 `min(getSlotLimit, stack.getMaxStackSize())`，并返回 remainder                                                                                          | Forge 基类的该路径可按自洽证据审查；子类覆盖行为仍不能凭基类名推断                                                                                                                                                     |
| `EmptyHandler`                         | `build/rfg/minecraft-src/java/net/minecraftforge/items/wrapper/EmptyHandler.java:47-49` 原样返回插入 stack，`:66-69` 的 slot limit 为 0                                                                                                                                                                                                             | 零容量且不会写入；可作为安全的拒绝目标，不能扩大                                                                                                                                                                       |
| `VanillaDoubleChestItemHandler`        | `build/rfg/minecraft-src/java/net/minecraftforge/items/VanillaDoubleChestItemHandler.java:142-159` 把插入转给实际单箱 handler，`:183-187` 按访问的箱体读取 `getInventoryStackLimit()`                                                                                                                                                               | 转发 wrapper，不是独立容量来源；必须核对实际箱体写入面                                                                                                                                                                 |
| `InvWrapper`                           | `getSlotLimit()` 转发 `getInv().getInventoryStackLimit()`：`build/rfg/minecraft-src/java/net/minecraftforge/items/wrapper/InvWrapper.java:201-205`；`insertItem()` 使用该值计算写入量并在 `:73-155` 返回 remainder                                                                                                                                  | 转发包装器不是独立容量来源；不得单独抬高 wrapper，需核对被包装的 `IInventory`                                                                                                                                          |
| `SidedInvWrapper`                      | `insertItem()` 在 `build/rfg/minecraft-src/java/net/minecraftforge/items/wrapper/SidedInvWrapper.java:88-168` 依据 `getSlotLimit()` 写入并返回 remainder；`:230-234` 转发 `inv.getInventoryStackLimit()`                                                                                                                                            | 同上；侧面 wrapper 的安全性取决于 delegate 的真实写入路径                                                                                                                                                              |
| `CombinedInvWrapper` / `RangedWrapper` | `CombinedInvWrapper.java:109-134` 和 `RangedWrapper.java:66-103` 分别转发 `insertItem()`、`getSlotLimit()`                                                                                                                                                                                                                                          | 仅作转发链处理，不作为独立 patch 来源                                                                                                                                                                                  |
| `EntityEquipmentInvWrapper`            | `insertItem()` 在 `build/rfg/minecraft-src/java/net/minecraftforge/items/wrapper/EntityEquipmentInvWrapper.java:86-123` 读取已有堆叠，计算 `limit`，只写入 `limit` 以内的数量，并在超量时返回复制出的 remainder；`:168-171` 的 `getStackLimit()` 是 `min(getSlotLimit, stack.getMaxStackSize())`；`:161-166` 的 slot limit 是 armor 为 1、手部为 64 | **不能写成“无余量必吞”。Forge 的 `insertItem` 有上限计算和 remainder。** 但 `setStackInSlot()` 在 `:173-179` 直接调用实体 setter，绕过 `insertItem` 的限制；整体扩容仍须分别验证 Forge 插入路径和 vanilla 实体存储路径 |

### 3.2 EntityEquipment 的 Forge/vanilla 分界

Forge wrapper 的文档明确说明它通过 `EntityLivingBase#getItemStackFromSlot` / `setItemStackToSlot` 暴露装备和手部库存；源码位置为
`EntityEquipmentInvWrapper.java:34-38`。因此必须把两层事实分开：

- Forge 层的 `insertItem` 自己计算上限、执行 `simulate` 分支、限制写入数量并返回 remainder。这是可核对的 Forge API
  行为，不能以旧的“setter 无 clamp”描述替代它。
- vanilla `EntityLiving` 的 `getItemStackFromSlot` / `setItemStackToSlot` 在
  `build/rfg/minecraft-src/java/net/minecraft/entity/EntityLiving.java:999-1022` 直接读写 `inventoryHands` /
  `inventoryArmor`；这些 setter 本身没有按 64 截断。
- vanilla `EntityPlayer` 的对应实现位于
  `build/rfg/minecraft-src/java/net/minecraft/entity/player/EntityPlayer.java:2416-2449`，直接读写主手、副手和 armor 列表；
  `InventoryPlayer.setInventorySlotContents` 在
  `build/rfg/minecraft-src/java/net/minecraft/entity/player/InventoryPlayer.java:610-629` 也只是写入列表。

结论是：Forge wrapper 的 `insertItem` 路径不能预先判为断链；vanilla setter 也不能单独证明动态装备容量已经被接管。手部槽和
armor 槽必须分别测试，armor 的 slot limit 为 1 的不可堆叠语义不应被“回扩”改变。

### 3.3 vanilla `IInventory` 事实

- `InventoryBasic.setInventorySlotContents` 在
  `build/rfg/minecraft-src/java/net/minecraft/inventory/InventoryBasic.java:143-153` 读取自身 `getInventoryStackLimit()`
  并截断。
- `TileEntityLockableLoot.setInventorySlotContents` 在
  `build/rfg/minecraft-src/java/net/minecraft/tileentity/TileEntityLockableLoot.java:155-166` 采用同类 clamp。
- `InventoryLargeChest.getInventoryStackLimit()` 在
  `build/rfg/minecraft-src/java/net/minecraft/inventory/InventoryLargeChest.java:203-206` 只转发上半箱；但
  `setInventorySlotContents()` 在 `:188-198` 按 index 写上半箱或下半箱。两半上限不一致时，广告来源与写入来源不闭合，不能仅凭现有原版默认值把它判为安全。
- `ContainerEnchantment` 的匿名 `InventoryBasic` 子类在
  `build/rfg/minecraft-src/java/net/minecraft/inventory/ContainerEnchantment.java:47-55` 明确返回 64；
  `TileEntityBeacon.getInventoryStackLimit()` 在
  `build/rfg/minecraft-src/java/net/minecraft/tileentity/TileEntityBeacon.java:459-462` 明确返回
  1。它们是主动限制或功能缺口，不应因未扩大就被写成吞物证据。

### 3.4 两个相邻证据门

- **1.12.2 `ResourceLocation`**：`build/rfg/minecraft-src/java/net/minecraft/util/ResourceLocation.java:40-55` 的
  `splitObjectName()` 只查第一个冒号，path 取从该冒号之后的完整子串。因此 path 可以保留多个冒号。DSL 不能按冒号数量否定合法
  path，也不能把第三段未经 grammar 证据解释成 meta；应按实际 DSL grammar、源码和测试判定。
- **F3+T 资源重载**：`build/rfg/minecraft-src/java/net/minecraft/util/text/translation/LanguageMap.java:98-103` 的
  `replaceWith()` 会清空全局语言表后重新放入传入表，缺键时 `:131-137` 回退为 key。任何本地化兼容改动都必须先通过真实客户端
  F3+T 观察完整 Forge 资源重载链；单独调用 `LanguageMap.replaceWith` 只能作辅助回归，不能冒充真实 F3+T 基线。

### 3.5 T3 Forge handler 目标收敛判定（2026-08-08 执行）

按 T2a 登记表（docs/agent/t2a-容量站点登记表.md §2.3）三分类，对 `ForgeItemHandlerLimitMixin` 的 6 个目标与
`SlotItemHandlerMixin` 的 2 个方法逐一定位 Forge/vanilla 写入路径源码后收敛。Forge 路径与 vanilla 实体路径分开取证：

| 目标（方法） | 分类 | 关键证据（build/rfg/minecraft-src/java/net/minecraftforge|minecraft/） | T3 动作 |
| --- | --- | --- | --- |
| `ItemStackHandler#getSlotLimit(I)I` | 自洽 | `items/ItemStackHandler.java:88`（insertItem 写入前读 `getStackLimit`）、`:107-116`（落库+remainder）、`:157-165`（getSlotLimit=64、getStackLimit=min(getSlotLimit, maxStackSize)） | 保留注入（64→compat） |
| `wrapper/EntityEquipmentInvWrapper#getSlotLimit(I)I` | 自洽（P0 事实 a） | `:95`（limit=getStackLimit）、`:108-120`（setItemStackToSlot/grow 落库）、`:122`（remainder）、`:162-166`（装甲 1/手部 64）、`:168-171`；vanilla 实体 setter 为无截断列表直写：`entity/EntityLiving.java:1012-1022`、`entity/player/EntityPlayer.java:2432-2449`、`entity/item/EntityArmorStand.java:155-167` | 保留注入（装甲槽 1 不提升） |
| `wrapper/InvWrapper#getSlotLimit(I)I` | 转发 | `:73-135`（insertItem 转发 setInventorySlotContents）、`:202-204`（getSlotLimit 转发 `inv.getInventoryStackLimit()`） | 移除注入 |
| `wrapper/SidedInvWrapper#getSlotLimit(I)I` | 转发 | `:88-168`（insertItem 转发 inv）、`:231-233`（getSlotLimit 转发） | 移除注入 |
| `wrapper/CombinedInvWrapper#getSlotLimit(I)I` | 转发 | `:109-115`（insertItem 转发子 handler）、`:128-134`（getSlotLimit 按 index 转发） | 移除注入 |
| `wrapper/RangedWrapper#getSlotLimit(I)I` | 转发 | `:66-74`（insertItem 转发 compose）、`:98-106`（getSlotLimit 转发） | 移除注入 |
| `items/SlotItemHandler#getSlotStackLimit()I` | 转发 | `:107-110`（广告转发 `itemHandler.getSlotLimit(index)`） | 移除独立动态上限注入（原 `Math.max(original, compat)` 会把广告抬到 handler 真实上限之上；槽位上限自然跟随 handler 真实来源） |
| `items/SlotItemHandler#getItemStackLimit(ItemStack)I` | 自洽 | `:112-140`（广告由 handler 侧 `insertItem(..., true)` simulate 求得），再由 `StackLimitHooks.resolveItemHandlerSlotLimit`（src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt:137-151）按 min(slotLimit, itemLimit) 收敛 | 保留 |

判定结论与红线核对：

- 四个转发 wrapper 与 `SlotItemHandler#getSlotStackLimit` 不再作为独立容量来源注入；`EntityEquipmentInvWrapper`
  未写成「无余量必吞」（Forge `insertItem` 上限计算与 remainder 由源码闭合，vanilla setter 为无截断直写）。
- 实现后 `ForgeItemHandlerLimitMixin` 的 `original == 64` 值检查是目标类的取值语义（两个自洽目标的
  `getSlotLimit` 只返回 1 或 64，装甲槽 1 必须保持），不再是「该目标是否可 patch」的准入判据——目标集合由本表
  三分类固定。
- 未使用 `@Accessor` 猜 delegate 容量；未引入写入后余量补偿。
- 守恒护栏：`src/test/kotlin/io/alexjoest/stackupup/core/WrapperCapacityDiagnosticTest.kt` 覆盖
  ItemStackHandler（64 与提升态）、四个转发 wrapper（不注入、remainder 闭合、提升的底层来源自然跟随）、
  EntityEquipment（手部提升态与装甲槽 1）、未 patch 的 `IInventory` stub 包装场景，以及两个 mixin 的
  字节码结构检查（转发目标已移除、slot 独立注入已移除）。
- 未决事项：`src/main/kotlin/io/alexjoest/stackupup/dev/DevWrapperCompatProbes.kt` 的 wrapper/SlotItemHandler
  探针期望仍按旧语义（getSlotLimit == compat），T3 租约外未改动，需后续任务按其转发酵更新期望；
  T2a 登记表 §2.3 的 mixin 目标行数（6→2）需随之刷新。

## 4. 当前限制

1. 动态 patch 基类可能传播到没有覆盖方法的第三方子类；静态目标表不能穷举第三方实现。没有源码或可重复运行证据的条目统一为
   **无源码不可判定**。
2. `ForgeItemHandlerLimitMixin` 已按 §3.5 收敛为两个自洽目标（ItemStackHandler、EntityEquipmentInvWrapper），
   四个转发 wrapper 与 `SlotItemHandler#getSlotStackLimit` 的独立注入已移除（T3 执行，2026-08-08）。
3. 当前 AE2 限流器的白名单同时包含 Forge wrapper、`ItemStackHandler` 和装备 wrapper；白名单理由不能由 `instanceof` 取代，T10
   必须补齐每项查询、写入、`simulate` 和 remainder 证据。AE2 的 `AdaptorItemHandler`、三个 pattern terminal
   目标及其第三方内部写入链在本记录中没有对应第三方源码，统一记为 **无源码不可判定**，不能用项目自己的 mixin 名称补出结论。
4. `InventoryLargeChest` 的上限转发与按 index 写入存在结构性不一致风险；T11 不能仅保留现有 mixin 目标而跳过两半箱验证。
5. NuclearCraft、Tech Reborn、RebornCore 等第三方内部写入链不因方法名或历史注释自动获得分类。当前缺少对应版本源码时，记录为
   **无源码不可判定**，不写成断链，也不允许据此新增 patch。
6. 本记录没有真实客户端 F3+T 的运行证据，因此真实资源重载基线当前视为 **尚未建立**。`LanguageMap.replaceWith()`
   的源码只能证明清空/替换行为，不能填补 T8.0 的行为证据缺口。

## 5. 历史研究证据（不等于当前准入）

### 5.1 研究保留下来的风险

历史兼容问题集中在“广告面扩大、写入面没有同步扩大”：vanilla 可能把大堆叠投入只接受 64 的目标，发生截断、丢失或与模组自有溢出逻辑冲突。这个风险支持第
1 节的写入前容量一致原则，但不支持事后回填。

固定目标使用显式 late mixin、完整方法 descriptor，并让已接管目标避开泛化 ASM，仍是可维护性较高的方向。它不能替代对真实写入源码的核验。

### 5.2 NuclearCraft

历史记录中列出的核验面包括：

- NuclearCraft `1.12.2o` 的 `nc.tile.internal.inventory.ItemHandler`：`insertItem`、`getStackSplitSize`、`getSlotLimit`；
- `nc.tile.inventory.ITileInventory#setInventorySlotContents`；
- 另一 `1.12.2` 路径中的 Forge `InvWrapper` / `SidedInvWrapper` 及其背后的 `IInventory`。

这些名称只能作为后续源码审查入口。没有对应第三方源码时，以上对象均为 **无源码不可判定**；不得把它们写成“必然截断”或“已自洽”。取得源码后，必须核对真实
setter、handler limit、插入 remainder 和版本差异。

### 5.3 Tech Reborn / RebornCore

历史记录中列出的核验面包括 RebornCore 的 `Inventory#setInventorySlotContents`、`getInventoryStackLimit`，以及
`InventoryItemHandler#insertItem`、`getSlotLimit`。Tech Reborn 1.12.x 的机器库存核心不能只按 TR 的 GUI 或 slot 类判断。

当前没有足够的第三方源码证据时，相关目标统一为 **无源码不可判定**。后续若取得源码，先核对 RebornCore 的真实写入面，再决定是否建立窄范围
late mixin；不扩展成未知 handler 的通用规则。

### 5.4 缺失第三方 artifact 台账

以下条目在本记录中没有可读的对应第三方源码或 jar；括号内文件名/版本未登记，不能据此补猜具体实现。每项均为
**无源码不可判定**：

- late 目标 jar：`appliedenergistics2`（AE2）、`actuallyadditions`、`brandonscore`、`cyclopscore`、`enderio`、`ic2`、`mantle`、
  `refinedstorage`、`storagenetwork`、`integrateddynamics`、`limelib`、`immersiveengineering`；各自对应的 1.12.x
  jar（文件名/版本未登记）均缺失。
- 机器研究 jar：`nuclearcraft`（1.12.2 / 1.12.2o 对应 jar，文件名/版本未登记）、`techreborn`（1.12.x 对应 jar，文件名/版本未登记）和
  `reborncore`（1.12.2 对应 jar，文件名/版本未登记）。

Forge 自身 wrapper 和 vanilla 反编译源码不属于上述缺失项；它们按第 3 节的 repo-relative 源码位置核验。

### 5.5 AE2 投喂路径

`src/main/java/io/alexjoest/stackupup/core/Ae2ItemHandlerInsertLimiter.java` 是项目自己的投喂适配层。Forge `IItemHandler`
的正式契约在 `build/rfg/minecraft-src/java/net/minecraftforge/items/IItemHandler.java:61-75`：`insertItem` 接受
`simulate` 参数并返回未插入的 remainder。

对超出 cap 的 `simulate=false` 输入，当前实现把输入预先拆成多个独立 chunk；每个 chunk 都是一次不超过 cap 的真实
`insertItem`，只有该 chunk 的 remainder 或零进度会停止后续 chunk。这与写入后回填已接受数量不是同一语义，但仍必须由 T10
逐调用点核对，不能把现有循环直接当成已批准的补偿机制。

AE2 `AdaptorItemHandler`、三个 pattern terminal 目标及其包裹 handler 的第三方内部实现，在本记录中均为 **无源码不可判定**
。项目自己的 `AppEngPatternTermMixin.java:15-29` 只证明它尝试提升空白 pattern 输入槽 `patternSlotIN`，不证明其他槽或 AE2
内部写入容量；安全性仍须由 T10 的静态白名单证据和 T12 的真实运行报告分别确认。

## 6. 拟议任务与准入边界

### T3：Forge handler 目标收敛

**待决边界：**逐目标核对 `ItemStackHandler`、各转发 wrapper 和 `EntityEquipmentInvWrapper` 的完整查询—写入链。不得把
wrapper 当独立容量来源，不得使用 `@Accessor` 猜 delegate 容量，不得按旧结论把 EntityEquipment 写成“无余量必吞”。T3 明确禁止对
`SlotItemHandler` 独立动态扩容；不得仅凭 slot 查询值或 `original == 64` 抬高其广告。

**准入条件：**每个保留目标都要有真实 `simulate=false` 插入、写入前后状态和 remainder 守恒证据；EntityEquipment 必须分别覆盖
armor 与手部，并同时说明 Forge wrapper 插入路径和 vanilla setter 路径。无法闭合的目标保持安全收缩。

**执行状态（2026-08-08）：**判定与实现完成，见 §3.5。保留目标（ItemStackHandler、EntityEquipmentInvWrapper、
SlotItemHandler#getItemStackLimit）的真实 `simulate=false` 守恒测试与转发目标（四个 wrapper、SlotItemHandler#getSlotStackLimit）
的移除护栏在 `WrapperCapacityDiagnosticTest` 中落地；`DevWrapperCompatProbes` 的旧语义探针期望与 T2a 登记表 §2.3 行数刷新
属租约外未决事项。

### T10：AE2 白名单重排

**待决边界：**白名单是静态、窄范围的调用方策略，不是全局 handler 规则；`isTrusted` 不能以类名作为理由。
`EntityEquipmentInvWrapper` 不能直接移除并声称必吞，也不能直接保留并声称已安全，必须引用第 3.2 节的 Forge/vanilla 分界证据。

**准入条件： **白名单内外分别覆盖真实插入、模拟插入和 remainder；第三方源码缺失时写**无源码不可判定**并维持 64 安全上限。T10
的静态白名单不能替代 T12 的运行时审计。

### T11：vanilla 目标表

**待决边界：**以源码穷举 `getInventoryStackLimit()` 实现者，改为编译期显式表，不能继续用运行时 `original == 64` 猜测
provenance。`ContainerEnchantment` 和 `TileEntityBeacon` 的未扩大行为要记录为功能限制或主动限制，不写成吞物 bug。

`InventoryLargeChest` 在两半箱真实来源一致前不得进入安全扩大表。必须用上下半箱上限不一致的测试证明风险；若回扩，对外查询值至少取
`min(upper, lower)`，不能只读取 `upper`。

**关联边界：**T4a 若调整 inventory-write 通道，必须保留 `resolveInventoryClampLimit` 的定义和两个 `InventoryPlayer`
调用点；不得用删除调用方来消除问题。

### T12：默认关闭的运行时守恒审计

**待决边界：**只包围项目主动发起的真实投喂点；不全局拦截第三方 handler，不改 handler 行为。`simulate=true`
不参与守恒统计、守恒判定或损失告警，但可在统一机器 schema 中保留 `simulate` 标记和原始输入/返回观测；不执行第二次真实写入。

**准入条件：**对 `simulate=false` 的真实事件至少记录 `schemaVersion`、调用点、handler 类名、slot、`simulate`、`offered`、
`before`、`after`、`storedDelta`、`remainderCount` 和 `balanced`。`simulate=true` 若保留记录，统一机器 schema 可保留
`schemaVersion`、调用点、handler 类名、slot、`simulate` 及原始输入/返回观测，但不计算 `balanced`
、不参与守恒告警。审计只读、只记录、只报告；不回填、不重试、不补偿。默认关闭时不得给生产热路径增加配置读取、日志构造或额外分配。

T12 只能提供运行时证据，不能把没有源码的第三方条目静态改写成安全或断链；其报告应回填 T2 登记表和本记录，而不是自动生成补丁。

### T13：覆盖面收缩与回扩

**前置条件：**T3、T10、T11 和 T12.5 的静态/运行时证据闭合后，才形成覆盖面矩阵。没有这些证据时，不以功能缺口为理由回扩广告容量。

**回扩准入：**

- **EntityEquipment**：armor 槽的 1 上限和不可堆叠语义保持不变。手部槽只有在 Forge `insertItem` 的上限计算/remainder、vanilla
  实体存储、所有实际投喂入口均通过真实守恒测试后，才可建立窄范围任务；仅证明 `getSlotLimit` 或 `setItemStackToSlot` 不足以回扩。
- **`InvWrapper` 包第三方 `IInventory`**：绝不重新 patch wrapper。只有 T12 观测到具体内层类，且拥有该版本源码或可重复守恒证据时，才可为内层类建立
  late mixin；无源码条目保持 **无源码不可判定**并不回扩。
- **`InventoryLargeChest`**：只有上、下两半都能承载同一 advertised limit，并通过两半不一致测试，才可考虑回扩；对外查询值至少取
  `min(upper, lower)`，且不得超过两半真实值；否则保持不 patch。

T13 的最终矩阵中的回扩处置状态只能是“已回扩”“决定不回扩（附理由）”或“等 T12
审计数据”；这不替代“自洽/转发/断链”三分类或独立证据状态“无源码不可判定”。在矩阵闭合前，本文不把任何一项写成已回扩。

## 7. 发布前验证门

兼容实现任务必须把源码证据、目标完整性和行为验证分开记录：

- 对每个自洽目标验证 `storedDelta + remainderCount == offered`，且测试覆盖实际 patch 状态，不只验证 Forge 裸基线。
- 对每个第三方缺源码目标保留独立证据状态 **无源码不可判定**和缺失的版本/源码范围，不用类名补结论。
- T12 报告必须区分模拟与真实写入，并能回到调用点、handler 类名和 slot。
- 改动 coremod、mixin 或自动化参数时，执行项目规定的字节码护栏和服务端自动验收；未执行的命令不得写成通过。
- 本记录只收敛决策和证据，不因文档更新删除、修改或补偿生产实现。

## 8. Mixin 生态、注入器与借鉴仓库决策

本节只收敛 Mixin 生态版本边界、注入器选择和参考仓库的可迁移范围；不把上游新版本资料倒灌为当前实现，也不改变前述容量事实。

### 8.1 已决策：当前构建事实与上游候选事实分层

- **当前构建事实（不是升级结果）：** `build.gradle.kts:434` 通过 `modUtils.enableMixins` 锁定 `zone.rong:mixinbooter:10.7`；
  `:438-439` 使用 `io.github.llamalad7:mixinextras-common:0.5.0`，分别作为 `compileOnly` 和 `annotationProcessor`
  。当前不能据此宣称运行时 provider、bootstrap、service 或打包关系已经闭合。该 10.7 锁定是当前基线；按项目决策，迁移至 11
  为计划内必选项，此事实句不因计划改变。
- 当前仍使用 early/late loader：`src/main/kotlin/io/alexjoest/stackupup/StackUpUpCore.kt:3-13,88-93` 注册 early 配置，
  `src/main/kotlin/io/alexjoest/stackupup/bootstrap/StackUpUpLateMixinLoader.kt:5-15,18-35` 按 mod presence 和开关排队
  late 配置。`src/main/resources/mixins.stackupup.early.json:1-7`、`mixins.stackupup.late.ae2.json:3-6`、
  `mixins.stackupup.late.brandonscore.json:4-6` 等配置使用 `JAVA_8`/`refmap`；late 配置字段并非全部一致，仍须逐个核对。
- **上游候选事实：** [MixinBooter 官方 README](https://github.com/CleanroomMC/MixinBooter) 当前 11.x 说明其基于
  CleanMix，early/late divide 淡出，`IEarlyMixinLoader`/`ILateMixinLoader` deprecated，并支持 manifest 的 `MixinConfigs`/
  `MixinConnector`；README 还记载 11.12 使用 CleanroomMC 自有 MixinExtras fork。这些是 11.x 迁移目标版本事实（迁移为计划内必选项），不是当前 10.7
  的注册事实，不能直接删除现有 loader 或改写配置。
- 11.x 候选的版本、坐标、签名、manifest、classloader、Extras provider 和迁移结果必须分别按 T9-R1、T9-R3 取证；不能把 README
  的注册示例写成已经完成的迁移。当前版本矩阵见
  `docs/agent/mixin-%E7%94%9F%E6%80%81%E4%B8%8E%E6%B3%A8%E5%85%A5%E6%9C%80%E4%BD%B3%E5%AE%9E%E8%B7%B5.md:87-108`。

### 8.2 已决策：生态职责、来源边界与证据等级

- [MixinBooter 官方仓库](https://github.com/CleanroomMC/MixinBooter) 是 Forge 1.8–1.12.2 侧的
  bridge/bootstrap、配置发现和兼容层来源；它不能替代 Mixin 核心、CleanMix 或 MixinExtras 的职责证明。
- [CleanMix 官方仓库](https://github.com/CleanroomMC/CleanMix) 是面向旧 Forge/Cleanroom 环境的 Mixin fork，负责 Mixin
  核心转换、classloading service、注入和 AP/refmap 等核心层；其架构资料不能证明当前项目的 10.7 jar 已经使用某个 CleanMix
  版本。
- [SpongePowered/Mixin 官方仓库](https://github.com/SpongePowered/Mixin) 是上游 Mixin/ASM
  trait、字节码织入、注入器、Accessor/Shadow、AP 和混淆基础的来源；它不是当前项目的 Forge 侧 loader，也不能把上游发行版本直接当作当前运行时版本。
- [LlamaLad7/MixinExtras 官方仓库](https://github.com/LlamaLad7/MixinExtras) 是 SpongePowered Mixin 的 companion
  library，提供额外注入器和表达式/操作包装能力；当前项目只在 `build.gradle.kts:438-439` 直接声明其 common 0.5.0 的编译/AP
  配置，不能由此推断运行时初始化和 provider 装载完成。
- [CleanroomMC/MixinExtras 官方仓库](https://github.com/CleanroomMC/MixinExtras) 是另一来源边界。其 DeepWiki
  URL（https://deepwiki.com/CleanroomMC/MixinExtras）当前返回 `Repository not found`；官方 GitHub 页面可访问。README raw 仅
  `https://raw.githubusercontent.com/CleanroomMC/MixinExtras/master/README.MD` 成功，main/master 的其他 README 大小写路径均返回
  404；DeepWiki 不可用时的回退范围仍是官方 GitHub README/源码、当前项目实际依赖以及 jar/POM/manifest/构建产物。页面中的坐标
  `com.cleanroommc:mixinextras-common:0.5.5` 仍是时间敏感示例，不是当前项目事实。
- `io.github.llamalad7` 与 `com.cleanroommc` 两个 MixinExtras 不自动二进制兼容或 API
  兼容；provider、bootstrap、service、shading/relocation 和重复装载都必须以对应版本 jar/POM/manifest、编译结果和运行矩阵核对。DeepWiki
  只能提供架构入口，不能据此新增 API 或替代实际依赖证据。生态台账和失败记录见
  `docs/agent/mixin-%E7%94%9F%E6%80%81%E4%B8%8E%E6%B3%A8%E5%85%A5%E6%9C%80%E4%BD%B3%E5%AE%9E%E8%B7%B5.md:71-85`、
  `:110-149`。

### 8.3 已决策：注入器按语义选择，不按数量评价

- `@Inject`、`@Redirect`、`@Shadow` 的数量不构成质量指标。`@Shadow` 是访问目标已有字段/方法的成员桥；只有 handler
  确实需要目标私有状态或私有方法时才使用，不能为了“Shadow 太少”新增成员，也不能用 Shadow 值证明容量。
- 需要保留一次原调用并围绕它改变参数、结果或是否执行时，优先核对当前 provider 后使用 `@WrapOperation`；只修改已有表达式结果时优先
  `@ModifyExpressionValue`；只修改完整方法返回值时优先 `@ModifyReturnValue`；单参数、多个参数、局部变量或 receiver 的局部语义分别评估
  `@ModifyArg`、`@ModifyArgs`、`@ModifyVariable`、`@ModifyReceiver` 等 `Modify*` 工具。包装器必须保留并按契约调用原
  `Operation`，可链式不等于可以吞掉或多次调用原操作。
- 新代码默认不新增 `@Redirect`。`@Redirect`、`@Inject(at = @At("HEAD"), cancellable = true)` 的完整控制流接管、`@Overwrite`
  、把字面量 `64` 当容量替换的 `@ModifyConstant`，以及用 `require=0` 掩盖目标失效，均属于高风险写法；只有完整目标证据，或明确可选目标、mod
  gate 和结构化缺失诊断时才可保留。
- 每个注入必须核对目标类、方法完整 descriptor、`@At` 的 owner/name/descriptor、ordinal/slice、handler 参数和静态性、`remap`
  /混淆边界、配置与生成 refmap、预期匹配数，以及两个或多个 Mixin 链式共存时的原操作调用次数和顺序。注入成功不等于容量广告与真实写入闭合。选择表和迁移规则见
  `docs/agent/mixin-%E7%94%9F%E6%80%81%E4%B8%8E%E6%B3%A8%E5%85%A5%E6%9C%80%E4%BD%B3%E5%AE%9E%E8%B7%B5.md:151-182,211-227`。

### 8.4 当前限制与 UNKNOWN：高风险样例仅待 T14 审计

以下条目是审查入口，不是修复完成清单；在 T14 的正式范围、静态/字节码和行为证据闭合前，不得写成已迁移、安全或已通过：

- `src/main/java/io/alexjoest/stackupup/mixin/early/EntityItemMergeMixin.java:11-20` 使用 `@Redirect` 替换
  `ItemStack#getMaxStackSize()I` 调用；需先闭合合并双方的真实上限、调用点 descriptor、原操作调用契约和数量/remainder
  结果，再决定是否迁移到 `@WrapOperation`。
- `src/main/java/io/alexjoest/stackupup/mixin/early/NetHandlerPlayServerMixin.java:21-56` 使用 Shadow 配合
  `HEAD + cancellable` 接管创造模式包处理的完整控制流；
  `src/main/java/io/alexjoest/stackupup/mixin/early/ItemStackNbtMixin.java:21-71` 在 `writeToNBT` 入口取消并重建
  NBT。两者必须分别对照目标版本的线程、异常、取消、字段、tag/capability 和往返语义，不能以 Shadow 数量或 Count 变大证明正确。
- `src/main/java/io/alexjoest/stackupup/mixin/early/SlotItemHandlerMixin.java:16-39` 修改 slot 广告并读取 handler 查询；未知
  `IItemHandler` 的 setter、`insertItem`、delegate 和持久化路径仍无由此补出的证据，不能因广告修改成功而动态扩容。
- `src/main/java/io/alexjoest/stackupup/mixin/late/ItemGridHandlerMixin.java:13-24` 使用 `require=0` 的 `@WrapOperation`
  ，且当前 handler 不调用传入的原 operation；必须证明它是明确可选目标、补齐缺失诊断，并验证多个 wrapper 共存和 `Math.min`
  替换语义。`require=0` 不能成为核心目标的静默成功门。

### 8.5 已决策：参考仓库只借鉴结构，不借鉴未经闭合的容量行为

- **StackUp：** 只借鉴确定性顺序规则，以及旧协议编码尽量保持兼容的思路；扩展数量仍必须增加版本、能力、最大值和编码范围协商。StackUp
  的全局 `Item#setMaxStackSize`、按 `BIPUSH 64` 命中的 ASM、整方法 splice 均不可照搬；其研究路径没有提供第三方 `insertItem`
  remainder/真实写入闭环证据，任何无 remainder 或无行为测试的做法也不可照搬为 StackUpUp 的容量证明或实现准入。证据入口见
  `docs/agent/借鉴仓库与重构对照.md:61-103`，包括
  `C:/dev/mc/_tmp/StackUp/src/main/java/pl/asie/stackup/script/ScriptHandler.java:38-69`、
  `C:/dev/mc/_tmp/StackUp/src/main/java/pl/asie/stackup/script/ScriptContext.java:137-173,192-208`、
  `C:/dev/mc/_tmp/StackUp/src/main/java/pl/asie/stackup/core/MaxStackConstantPatch.java:39-69`、
  `C:/dev/mc/_tmp/StackUp/src/main/java/pl/asie/stackup/core/StackUpTransformer.java:154-219` 和 packet splice 路径。
- **biggerstacks-Unofficial：** 只借鉴把 `RuleSet` 作为权威快照入口的结构方向、watcher、登录 handshake/运行期 sync 和按 mod
  分层；不能把其独立 template override 当作已同步的权威快照，也不能照搬直接丢弃 `insertItem` remainder、slot 与 `ItemStack`
  求值分叉、override 与 RuleSet 不同步、`require=0` 静默核心目标或对私有结构使用反射。其 Minecraft 1.21.1/NeoForge/Java 21
  代码不能替代 Forge 1.12.2、Java 8、当前目标 jar 的源码、字节码和运行证据。证据入口见 `docs/agent/借鉴仓库与重构对照.md:105-153`
  ，尤其 `:136-153` 的同步、remainder、require 和反射边界。
- 两个参考仓库的结构启示都不能改变本记录的容量不变量：广告值仍不得大于真实写入能力；真实 `insertItem` 必须保留 remainder
  并接受守恒审计；没有 Forge 1.12.2 第三方写入源码或可重复行为证据的目标仍是 **无源码不可判定**。

### 8.6 拟议任务、MixinBooter 11 必选迁移计划与关联链接

- 在 `T9-R1`、`T9-R3` 和 T14 完成前，**保持当前 MixinBooter 10.7，不修改依赖**（当前事实）；11 迁移为计划内必选项，
  T9-R3 决定目标版本与迁移顺序，T14.7 是迁移准入门。T14（含 T14.0-T14.7）仍按任务清单的规划、证据审查与迁移准入门执行；未完成前任何关键
  provider、注册、混淆、字节码或容量证据不闭合，都保持当前版本。
- 后续 11 迁移必须同时具备官方来源 URL、候选与当前 jar/POM/manifest、compileOnly/AP/runtime/shaded
  关系、编译检查、运行时加载矩阵和变换后字节码矩阵。当前列出的官方仓库根 URL 只是研究入口，T9-R1 尚未补齐具体
  tag/release、README commit、候选 POM 或 Maven metadata URL，不能把它们当作版本化证据。缺任一关键证据时，结论只能是
  `UNKNOWN`；第三方源码或 jar 缺失时使用 **无源码不可判定**，不得用类名、DeepWiki 页面或新版本示例补猜。
- **T14 状态：** [
  `T14 Mixin 生态与注入重构`](%E9%87%8D%E6%9E%84%E4%BB%BB%E5%8A%A1%E6%B8%85%E5%8D%95.md#t14-mixin-%E7%94%9F%E6%80%81%E4%B8%8E%E6%B3%A8%E5%85%A5%E9%87%8D%E6%9E%84)
  已在任务清单 `:465-577` 正式定义 T14（含 T14.0-T14.7）；这些仍是 **未执行的规划、证据审查与迁移准入任务**，不是生产实现、迁移或
  MixinBooter 升级的完成证明。T14.7（迁移准入门）完成前不得解锁生产迁移或 MixinBooter 11 迁移；迁移为计划内必选项，本段不是'可选'表述。缺 jar、未运行生命周期验证或其他关键证据缺失时继续保留
  **无源码不可判定**或 `UNKNOWN`，不得写成通过。正式定义前的建议/UNKNOWN 证据及其对照记录仍保留于 [
  `T14 对照与验收门`](%E5%80%9F%E9%89%B4%E4%BB%93%E5%BA%93%E4%B8%8E%E9%87%8D%E6%9E%84%E5%AF%B9%E7%85%A7.md#51-t14mixin-%E7%94%9F%E6%80%81%E4%B8%8E%E6%B3%A8%E5%85%A5%E9%87%8D%E6%9E%84%E6%AD%A3%E5%BC%8F%E8%A7%84%E5%88%92%E7%8A%B6%E6%80%81)（
  `:309-319`），仅作历史/对照证据，不替代现行 T14 范围。
- 关联资料：[
  `Mixin 生态与注入最佳实践`](mixin-%E7%94%9F%E6%80%81%E4%B8%8E%E6%B3%A8%E5%85%A5%E6%9C%80%E4%BD%B3%E5%AE%9E%E8%B7%B5.md)、[
  `借鉴仓库与重构对照`](%E5%80%9F%E9%89%B4%E4%BB%93%E5%BA%93%E4%B8%8E%E9%87%8D%E6%9E%84%E5%AF%B9%E7%85%A7.md)、[
  `T9 MixinBooter 11 迁移取证`](%E9%87%8D%E6%9E%84%E4%BB%BB%E5%8A%A1%E6%B8%85%E5%8D%95.md#t9-mixinbooter-11-%E8%BF%81%E7%A7%BB%E5%8F%96%E8%AF%81%E5%BF%85%E9%80%89%E8%BF%81%E7%A7%BB)、[
  `T9-R1`](%E9%87%8D%E6%9E%84%E4%BB%BB%E5%8A%A1%E6%B8%85%E5%8D%95.md#t9-r1-%E7%89%88%E6%9C%AC%E4%B8%8E-api-%E8%BF%81%E7%A7%BB%E5%8F%96%E8%AF%81%E5%90%88%E5%B9%B6%E5%8E%9F-r1-%E4%B8%8E-r2)、[
  `T9-R3`](%E9%87%8D%E6%9E%84%E4%BB%BB%E5%8A%A1%E6%B8%85%E5%8D%95.md#t9-r3-%E8%BF%81%E7%A7%BB%E8%B7%AF%E5%BE%84%E5%86%B3%E7%AD%96%E4%B8%8E%E8%A7%A6%E5%8F%91%E6%9D%A1%E4%BB%B6)、[MixinBooter 官方仓库](https://github.com/CleanroomMC/MixinBooter)、[CleanMix 官方仓库](https://github.com/CleanroomMC/CleanMix)、[SpongePowered/Mixin](https://github.com/SpongePowered/Mixin)、[LlamaLad7/MixinExtras](https://github.com/LlamaLad7/MixinExtras)
  和 [CleanroomMC/MixinExtras](https://github.com/CleanroomMC/MixinExtras)。

- **T9-R3 迁移路径决策（2026-08-08 追加）：** 本节是 T9-R3 的决策产出，只决定迁移路径、触发条件与回滚边界，
  不实施迁移；实施由 T9-R3 授权、T14.7 通过后创建的独立迁移实现任务执行（`重构任务清单.md:446-457,571-582`）。
  本段与 8.1 的「当前构建事实」分层一致：10.7 是当前事实，11.13 是计划迁移目标；本文不把计划写成已使用。
- **目标版本与坐标：** 目标为 `zone.rong:mixinbooter:11.13`（2026-08-05 发布），坐标不变。证据：11.13 POM
  （https://maven.cleanroommc.com/zone/rong/mixinbooter/11.13/mixinbooter-11.13.pom）声明依赖
  `com.cleanroommc:cleanmix:0.7.1`、`com.cleanroommc:mixinextras-common:0.5.5`、`gson:2.8.0`、`guava:21.0`、
  `asm-debug-all:5.2`，无 relocation；官方 README
  （https://raw.githubusercontent.com/CleanroomMC/MixinBooter/master/README.md）以 11.13 为当前 Maven 示例版本，
  并写明「As of 11.0, MixinBooter is built on CleanMix」「As of 11.12, MixinBooter uses CleanroomMC's own MixinExtras
  fork」「As of 11.0, early/late divide is no longer present, therefore IEarly/ILateMixinLoaders are deprecated」。
  README 正文的「CleanMix 0.6.0」表述与 11.13 POM 的 0.7.1 不一致，判定 README 文字滞后，以 POM 为准。
- **注册路径决策：保留现有 loader，不迁 manifest。** 项目 loader 实际只使用：`StackUpUpCore.kt:88-92` 的
  `getMixinConfigs()`（IEarlyMixinLoader 成员；`StackUpUpCore.kt:78` 的 `injectData` 是 `IFMLLoadingPlugin` 的成员，
  不是 loader 接口成员）与 `StackUpUpLateMixinLoader.kt:9,11-15` 的 `getMixinConfigs()`/`shouldMixinConfigQueue(Context)`。
  11.x main 分支源码
  （https://raw.githubusercontent.com/CleanroomMC/MixinBooter/main/src/main/java/zone/rong/mixinbooter/IEarlyMixinLoader.java、
  https://raw.githubusercontent.com/CleanroomMC/MixinBooter/main/src/main/java/zone/rong/mixinbooter/ILateMixinLoader.java）
  确认两个接口带 `@Deprecated`（javadoc 注明「as of 11.0, the line of 'early' and 'late' mixin loading no longer is
  present」），但 `getMixinConfigs`/`shouldMixinConfigQueue(Context|String)`/`onMixinConfigQueued(Context|String)`
  成员仍存在且签名与项目使用点逐点一致；T9-R1 已核实运行时路径与 10.7 逐点一致（`loadEarlyLoaders` 与
  LoadControllerMixin CONSTRUCTING 阶段未变）。因此首次迁移保留两个 loader 类，代码零改动，仅产生 deprecation
  编译警告（无 `-Werror`）；manifest `MixinConfigs`/`MixinConnector` 注册模型列为后续可选清理项，不并入本次版本迁移，
  避免一次改动叠加两个变量。main 分支可能超前于 11.13 tag，上述 raw 源码属佐证；tag 级证据以 T9-R1 账本为准。
- **迁移顺序（实施任务按序执行）：** ① 版本号 `gradle/libs.versions.toml:20` 的 `mixinbooter = "10.7"` 改为
  `"11.13"`（版本值唯一一处；`build.gradle.kts:53` 的字符串插值不变；8.1 所引 `build.gradle.kts:434/438-439`
  行号已随 T15 build-logic 重构过期，实际触点以本段为准）→ ② 删除外部 mixinextras 声明：`build.gradle.kts:57-58`
  的 `compileOnly`/`annotationProcessor` 与 `gradle/libs.versions.toml:21,29` 的 `mixinextrasCommon` 条目
  （`io.github.llamalad7:mixinextras-common:0.5.0`，改用 11.13 内嵌的 Cleanroom fork 0.5.5；17 个 mixin 文件的
  `com.llamalad7.mixinextras.*` import 同包名不变）→ ③ 编译验证：记录 deprecation 警告集合，跑 `test` 与
  `spotlessCheck` → ④ `runServerAutoTest` 全量回归，另覆盖 `CoremodHierarchyBytecodeSafetyTest`/
  `EarlyMixinBytecodeSafetyTest`/`MixinBooterIntegrationTest` → ⑤ T14.7 准入门复核，实施任务不自宣通过。
- **回滚边界：** 步骤①为单值回退（`gradle/libs.versions.toml:20` 改回 `10.7`）；步骤②为两处恢复
  （`build.gradle.kts:57-58` 与 `gradle/libs.versions.toml:21,29`）；两步相互独立、可分别回退；回退按 jj 工作区快照
  语义处理，不执行 `git restore`/`git reset` 等清理命令，不覆盖既有改动。
- **触发条件与停止条件：** 触发条件：T9-R1 取证已闭合、本决策记录生效、T14.0–T14.6 复核完成且 T14.7 通过后，
  创建独立迁移实现任务（build.gradle.kts 红线例外，`重构任务清单.md:451,582`）。停止条件（任一命中即停止，
  T14.7 未通过前一律不实施）：provider/注册/refmap/classloader/容量证据未闭合；`runServerAutoTest` 或字节码护栏
  失败；删除外部 mixinextras 后 `com.llamalad7.mixinextras.*` import 编译解析失败；11.13 内嵌 MixinExtras 的运行时
  bootstrap 无启动日志证据。
- **UNKNOWN 账本（交接 T14.0 复核）：** ① CleanMix 0.7.1 对 `minVersion: "0.8"` 配置的兼容性未实测（项目全部 mixin
  配置均为 `"minVersion": "0.8"`，如 `mixins.stackupup.early.json:5`；DeepWiki/README 显示 CleanMix fork 自 Mixin
  0.8.7，原则可满足，但具体版本字符串与 config 解析未以运行验证）→ UNKNOWN；② 11.13 内嵌 MixinExtras 是否自动完成
  provider 初始化（README 称 AP 依赖无需自声明，但运行时 bootstrap 机制未以启动日志核实）→ UNKNOWN；③ 现有 jar
  manifest（`build-logic/convention/src/main/kotlin/minecraft.gradle.kts:103-108`，含 `FMLCorePlugin` 等、不含
  `MixinConfigs`/`MixinConnector`）与 `required-after:mixinbooter@[10.0,)`（`StackUpUp.kt:34`）在 11.13 下的行为未验证
  → UNKNOWN；④ `CoremodClassFilter.java:19-20` 的 `zone/rong/mixinbooter/` 跳过前缀在 11.13 下是否需扩展到
  CleanMix/mixinextras 类包未验证 → UNKNOWN（`org/spongepowered/` 已在跳过表 :17）。
- **DeepWiki 与来源访问失败记录：** 本会话未注入 DeepWiki MCP 工具，降级为 DeepWiki 网站页面与官方 GitHub。
  `https://deepwiki.com/CleanroomMC/MixinBooter` 页面存在但版本表止于 10.0，不含 11.x 内容，属旧索引，不能作为
  11.x 注册事实（与 `mixin-生态与注入最佳实践.md:89-92` 既有冲突记录一致）；`https://deepwiki.com/CleanroomMC/CleanMix`
  首次提取无文本、重试成功：CleanMix 为 Fabric Mixin 0.17.3 / SpongePowered Mixin 0.8.7 的 fork，经 MixinBootstrap
  多阶段生命周期（PREINIT/INIT/DEFAULT）接入可插拔 classloading service（Cleanroom 原生、Forge 1.12.2 LaunchWrapper +
  MixinBooter、ModLauncher 7+），转换引擎使用 ASM 9.x 与 legacy 5.x，AP 生成 refmap；GitHub API tree 请求 403
  rate limit，降级 raw 文件直取成功。

### 8.7 T14.0 来源与版本账本复核（2026-08-08 追加）

本节是 T14.0「来源与版本账本复核」的窄复核产出：只交叉核对 T9 账本与当前构建配置、本地产物、DeepWiki/官方来源，不改依赖、不改 build 文件、不改 Mixin。复核方式为 DeepWiki MCP（https://mcp.deepwiki.com/mcp，HTTP JSON-RPC/SSE）、官方 Maven（maven.cleanroommc.com / Maven Central）、官方 GitHub（页面/raw，GitHub REST API 被限流）与本机 gradle 缓存、build/ 产物交叉核对。

**T9 账本复核结论：** T9-R1/T9-R3 的版本决定、坐标、发布事实、回滚边界与 4 项 UNKNOWN 与本复核逐条一致，未发现冲突；以下仅新增证据精度与 2 项新 UNKNOWN。当前构建触点复核：`gradle/libs.versions.toml:20` `mixinbooter = "10.7"`、`:21` `mixinextrasCommon = "0.5.0"`、`:29` `io.github.llamalad7:mixinextras-common`；`build.gradle.kts:53-54` `modUtils.enableMixins("zone.rong:mixinbooter:${...}")` 字符串插值、`:57-58` `compileOnly`/`annotationProcessor(libs.mixinextrasCommon)`、`:59-62` AP 追加 `asmDebugAll`/`guava`/`gson`/mixin（与 10.7 tag 上游 AP 集合一致）。

**本地产物账本（当前 10.7 基线，全部实际核对）：**

- `zone.rong:mixinbooter:10.7` 位于 `~/.gradle/caches/modules-2/files-2.1/zone.rong/mixinbooter/10.7/`：jar（`3fdfffe4…/mixinbooter-10.7.jar`，5564093 B，1665 条目）、sources jar（`3b37a075…`）、POM（`7ec886c0…`，**无任何依赖声明**，运行时内容全部内嵌）。MANIFEST.MF 全字段：`TweakClass: org.spongepowered.asm.launch.MixinTweaker`、`FMLCorePlugin: zone.rong.mixinbooter.MixinBooterPlugin`、`FMLCorePluginContainsFMLMod: true`、`ForceLoadAsMod: false`、`Premain-Class`/`Agent-Class: org.spongepowered.tools.agent.MixinAgent`、`Can-Redefine-Classes`/`Can-Retransform-Classes: true`。jar 内容统计：`org/spongepowered/` 1064 类（嵌入 UniMix/Sponge Mixin 核心）、`com/llamalad7/mixinextras/` 类（**内嵌 LlamaLad7 MixinExtras 0.5.0**，含 `MixinExtrasBootstrap`、`ap/MixinExtrasAP`）、`org/objectweb/asm/`、`zone/rong/mixinbooter/` 26 类（含 `fix/mixinextras/MixinExtrasFixer`）。`META-INF/services/`：`javax.annotation.processing.Processor` → `com.llamalad7.mixinextras.ap.MixinExtrasAP` + `org.spongepowered.tools.obfuscation.MixinObfuscationProcessorInjection/Targets`；`IMixinService` → `org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapper`；`IMixinServiceBootstrap` → `…LaunchWrapperBootstrap`；另有 `IGlobalPropertyService`、`IObfuscationService`。条目时间戳：sponge 类 2025-01-08、mixinextras 类 2025-07-27、jar 打包 2025-09-30。与上游 10.7 tag build.gradle（https://raw.githubusercontent.com/CleanroomMC/MixinBooter/10.7/build.gradle，`enableMixins('com.github.CleanroomMC:UniMix:9d4b487ed3')`、`embed 'io.github.llamalad7:mixinextras-common:0.5.0'`）一致：当前 jar 即按该 tag 构建的内嵌形态。
- `io.github.llamalad7:mixinextras-common:0.5.0` 位于 `~/.gradle/caches/modules-2/files-2.1/io.github.llamalad7/mixinextras-common/0.5.0/`：jar（`286da960…`）、sources jar（`8db1dbef…`）、POM（`e6bfe617…`，MIT，无依赖）。jar 的 MANIFEST 仅 `Manifest-Version: 1.0`。Maven Central metadata（https://repo1.maven.org/maven2/io/github/llamalad7/mixinextras-common/maven-metadata.xml）版本 0.4.0→0.5.4，`0.5.0` 存在，最新 0.5.4（lastUpdated 2026-04-15）。
- `build/` 产物：`build/resources/main/mixins.StackUpUp.refmap.json` 与 `build/tmp/mixins/mixins.StackUpUp.refmap.json` **逐字节一致**（refmap 由当前 AP 链生成，含 SRG `func_` 映射，如 `CommandGiveMixin` 的 `execute → func_184881_a`）；`build/resources/main/` 下 20 个 mixin 配置 JSON（early 1 + late 19）。AP 链（编译期）为 `build.gradle.kts:57-62` 的 mixinextras 0.5.0 + asm-debug-all 5.2 + guava + gson + mixinbooter 内嵌 AP。
- **候选版本不在本地缓存：** `~/.gradle/caches/modules-2/files-2.1/` 下无 `com.cleanroommc`（cleanmix 0.7.1、mixinextras-common 0.5.5），无 `mixinbooter/11.13`；11.13 候选 jar 仅本次在线下载核对（见下），不构成本地运行证据。

**10.7 vs 11.13 对照表（版本/坐标/职责/early-late/manifest/CleanMix/Extras/Sponge）：**

| 维度 | 当前 10.7（本地产物） | 候选 11.13（官方 Maven 产物，2026-08-05 发布） |
|---|---|---|
| 坐标 | `zone.rong:mixinbooter:10.7` | `zone.rong:mixinbooter:11.13`（坐标不变；metadata lastUpdated 20260805224202，GitHub releases 页 11.13 = Aug 5 22:42，与 T9-R3 一致） |
| 职责 | Forge 1.12.2 bridge/bootstrap + 配置发现 + 兼容修复（UniMix 0.15.3 内嵌） | 同职责，改基于 CleanMix 0.7.1（11.13 POM：`com.cleanroommc:cleanmix:0.7.1`、`com.cleanroommc:mixinextras-common:0.5.5`、`gson:2.8.0`、`guava:21.0`、`asm-debug-all:5.2`，无 relocation；cleanmix metadata 0.2.6→0.7.1，lastUpdated 2026-08-05 22:24，与 11.13 同批次发布） |
| early/late | `IEarlyMixinLoader`/`ILateMixinLoader` + `Context`（项目使用点见 8.6） | 接口仍存在但 `@Deprecated`（main 分支源码已核实，见 8.6）；DeepWiki 索引声称"未弃用"为陈旧索引，不作 11.x 证据 |
| manifest | `TweakClass`（MixinTweaker）+ `FMLCorePlugin` + `FMLCorePluginContainsFMLMod: true` + `ForceLoadAsMod` + Premain/Agent | 11.13 jar manifest 实测：`FMLCorePlugin: zone.rong.mixinbooter.MixinBooterPlugin` + `Premain/Agent` + `Can-Redefine/Retransform` + **`MixinConfigs: mixin.mixinbooter.init.json`**；**无 `TweakClass`**、**无 `FMLCorePluginContainsFMLMod`** |
| MixinConnector | 项目无 connector；10.7 不扫描 manifest connector | CleanMix 支持 manifest `MixinConfigs`/`MixinConnector` 扫描（DeepWiki：`MixinPlatformAgentDefault` 读取两属性，`MixinConnectorManager` 装载）；项目是否使用仍为未验证项 |
| CleanMix | 不涉及（10.7 内嵌 UniMix 0.15.3，派生自 Mixin 0.8.7；DeepWiki/README 一致） | `cleanmix-0.7.1.jar` 实测：716 条目，`org/spongepowered/` 708 类（即 Mixin 核心 fork 本体），`Implementation-Title: CleanMix`、`Implementation-Version: 0.7.1`、vendor cleanroommc.com；不内嵌 ASM/Extras；POM 依赖 guava 21.0、gson 2.2.4、asm-tree/commons/util 9.8（均 runtime）；DeepWiki：fork 自 Fabric Mixin 0.17.3 / SpongePowered Mixin 0.8.7，`MixinBootstrap.VERSION = "0.8.7"` |
| Extras | 10.7 jar 内嵌 LlamaLad7 MixinExtras 0.5.0（`com.llamalad7.mixinextras.*`）；项目另声明 compileOnly/AP 0.5.0 | 11.13 jar 内嵌 Cleanroom fork 0.5.5（`mixinextras-common-0.5.5.jar` 实测 565 条目，559 类仍为 `com.llamalad7.mixinextras.*` **同包名、无 relocation**，`META-INF/services/javax.annotation.processing.Processor` 同名指向 `MixinExtrasAP`）；`com.cleanroommc:mixinextras-common` metadata 仅 0.5.5（lastUpdated 2026-07-28）；CleanroomMC/MixinExtras GitHub 页确认 fork 自 LlamaLad7/MixinExtras，README 坐标 `com.cleanroommc:mixinextras-common:0.5.5` |
| Sponge | 10.7 内嵌 org/spongepowered 1064 类（UniMix 0.15.3） | 11.13 jar 内嵌 org/spongepowered 594 类（CleanMix 0.7.1 核心）；SpongePowered/Mixin 上游 tag `0.8.7` 存在（raw `https://raw.githubusercontent.com/SpongePowered/Mixin/0.8.7/build.gradle` HTTP 200；注意 `/releases/tag/0.8.7` 页面 404，tag 存在但可能无对应 GitHub release 条目） |
| ASM | 10.7 jar 内嵌 org/objectweb/asm | 11.13/0.7.1 jar **均不内嵌 ASM**；11.13 POM 声明 asm-debug-all 5.2，cleanmix 0.7.1 POM 声明 asm 9.8（runtime）→ 运行时两套 ASM 并存的实际装载未验证（见 UNKNOWN ⑥） |

**DeepWiki 查询/失败记录（可追溯）：** 调用类型为 DeepWiki MCP 端点 `https://mcp.deepwiki.com/mcp` 的 HTTP JSON-RPC（SSE 响应），`initialize`/`tools/list` 成功（serverInfo DeepWiki 2.14.3）。

- CleanroomMC/MixinBooter：`read_wiki_structure` 成功（9 页：Overview/Getting Started/Core Architecture/Mixin Loading System/Compatibility System/Enhanced Debugging/Context System/API Reference/Build and Deployment）；`ask_question` 成功但索引**陈旧**：changelog 止于 10.6、无任何 11.x/CleanMix 内容、声称 `IEarly/ILateMixinLoader` "not deprecated"（与官方 README 11.x 声明冲突）→ 判定为旧索引，不作 11.x 注册事实（与 `mixin-生态与注入最佳实践.md:89-92` 既有冲突记录一致）；其 10.6=0.5.0-rc.1、10.4=0.5.0-beta.5 的记载交叉印证 10.7 嵌入 MixinExtras 0.5.0。
- CleanroomMC/CleanMix：`read_wiki_structure` 成功（8 节）；`ask_question` **首次调用连接超时（WinError 10060）**，重试成功：fork 自 Fabric Mixin 0.17.3 / SpongePowered Mixin 0.8.7，职责为 Mixin 核心转换（MixinTransformer/ASM）、可插拔 classloading service（IMixinService）、注入器、AP 与 refmap 生成；Forge 1.12.2 LaunchWrapper + MixinBooter 原生支持（MixinTweaker 生产路径）、ModLauncher 7–10+；manifest `MixinConfigs`/`MixinConnector` 由 `MixinPlatformAgentDefault` 扫描；wiki 无 0.6.0→0.7.1 差异明细（gradle.properties 的 buildVersion=0.2.4 与模块版本 0.7.1 并存，属版本号体系内部事项）。
- CleanroomMC/MixinExtras：`read_wiki_structure` **失败：`Repository not found`（DeepWiki 未索引该 wiki）**；降级官方 GitHub 成功：仓库存在、标注 "forked from LlamaLad7/MixinExtras"、README 仅示例坐标 `com.cleanroommc:mixinextras-common:0.5.5`。
- LlamaLad7/MixinExtras：`ask_question` 成功：经 `MixinExtrasBootstrap.init()` 单次初始化，`InjectionInfo`/`IExtension` 注册 + "take control" 多版本协商（新版本接管）；当前上游版本 0.5.4（README）；「任何其他平台」（含旧 Forge）需手动 shade + 显式 init。
- GitHub REST API（api.github.com）：MixinBooter releases、CleanroomMC/MixinExtras repo 信息、SpongePowered/Mixin tags 三请求均 **403 rate limit**；降级 GitHub HTML 页面、releases 页、raw 文件与 maven-metadata.xml 均成功。
**UNKNOWN 账本逐条（来源/缺口/影响/停止条件）：**

- ① **CleanMix 0.7.1 对 `minVersion: "0.8"` 配置的兼容性未实测**（T9-R3 交接）。来源：DeepWiki（CleanMix fork 自 Mixin 0.8.7，`MixinBootstrap.VERSION="0.8.7"`，原则可满足）+ 11.13 POM（cleanmix 0.7.1）。缺口：`src/main/resources/mixins.stackupup.early.json:5` 等全部配置 `"minVersion": "0.8"`，但 0.7.1 的具体版本字符串解析与 config 校验未以运行验证。影响：迁移后 config 解析失败或 minVersion 告警。停止条件：11.13 启动日志显示全部配置装载成功且无 minVersion 错误。
- ② **11.13 内嵌 MixinExtras 的 provider 初始化未以启动日志核实**（T9-R3 交接）。来源：11.13 jar 内嵌 `com/llamalad7` 559 类（Cleanroom fork 0.5.5，同包名）；当前 10.7 jar 内嵌 MixinExtras 0.5.0 且上游 10.7 tag 由 `MixinBooterPlugin` 调 `MixinExtrasBootstrap.init()`（10.7 tag build.gradle 已复核）。缺口：11.13 的 bootstrap 是否自动完成（README 称 AP 依赖无需自声明），无启动日志证据；`com.llamalad7.mixinextras.*` import 在删除外部 0.5.0 声明后能否解析也未验证。影响：`@WrapOperation` 等注入器不注册 → 目标不命中或静默失效。停止条件：11.13 启动日志含 MixinExtras bootstrap/init 证据 + 编译期 import 解析成功。
- ③ **现有 jar manifest 与 `required-after` 在 11.13 下的行为未验证**（T9-R3 交接）。来源：`build-logic/convention/src/main/kotlin/minecraft.gradle.kts:106-116`（`FMLCorePlugin`/`FMLCorePluginContainsFMLMod`/`ForceLoadAsMod`/`FMLAT`，无 `MixinConfigs`/`MixinConnector`，本复核已重核）；`StackUpUp.kt:34` `required-after:mixinbooter@[10.0,)`；新增对照事实：11.13 自身 manifest 含 `MixinConfigs` 且无 `TweakClass`/`FMLCorePluginContainsFMLMod`。缺口：项目 jar 的 FML 版本依赖区间与 coremod 装载在 11.13 运行时未验证。影响：装载失败或版本区间解析异常。停止条件：`runServerAutoTest` 全量回归 + 启动日志。
- ④ **`CoremodClassFilter.java:17-24` 跳过前缀是否需扩展**（T9-R3 交接）。来源：当前 `SKIPPED_PREFIXES` 含 `org/spongepowered/` 与 `zone/rong/mixinbooter/`，**无 `com/llamalad7/`**（本复核重核）；11.13 候选类分布：org/spongepowered 594（已被跳过表覆盖）、com/llamalad7 559（**未覆盖**）、zone/rong 41（mixinbooter 已覆盖）。缺口：com/llamalad7 类是否进入 coremod 早期处理链未验证。影响：coremod 早期路径无谓处理/类加载风险。停止条件：字节码护栏 + 启动日志确认 com/llamalad7 类不进入 coremod 链或证实无影响。
- ⑤ **11.13 无 `TweakClass` manifest 的启动路径未验证**（本复核新增）。来源：10.7 manifest `TweakClass: org.spongepowered.asm.launch.MixinTweaker` vs 11.13 manifest 无该属性（CleanMix 平台 manager 替代 MixinTweaker 的架构，DeepWiki 佐证）。缺口：无 11.13 实际启动日志证明 CleanMix 平台装载替代路径生效。影响：tweak 阶段类加载路径变化。停止条件：11.13 服务端/客户端启动日志 + 字节码护栏。
- ⑥ **11.13 运行时 ASM 版本并存未验证**（本复核新增）。来源：11.13 jar 与 cleanmix-0.7.1.jar 均不内嵌 ASM（实测条目统计为 0）；11.13 POM 声明 asm-debug-all 5.2、cleanmix 0.7.1 POM 声明 asm-tree/commons/util 9.8（runtime）。缺口：classpath 上 5.2 与 9.8 并存时转换链实际使用哪套未验证。影响：转换期 ClassReader/ClassWriter 行为差异。停止条件：11.13 启动日志 + 转换后字节码矩阵（T14.4/T14.7 范围）。

**无源码不可判定与迁移行为边界：** 11.13/cleanmix-0.7.1/mixinextras-0.5.5 候选 jar 仅在线下载核对 manifest 与内容统计，未保留本地副本、未运行加载矩阵；其运行行为证据（provider/refmap/classloader/容量写入）仍为**无源码不可判定**，本复核不改变 8.6 的停止条件：T14.7 通过前不实施迁移。第三方容量写入目标缺口维持既有台账（`compatibility-decision-record.md:156-163`、`mixin-生态与注入最佳实践.md:97-101`），本复核不重列。DeepWiki 全部查询只作架构/职责佐证，不作版本化运行事实；GitHub API 限流与 DeepWiki 未索引均已按上表逐条记录，未把失败项写成通过。
