# 兼容取舍与决策记录

> 状态：PARTIAL（当前构建与主要容量决策已同步；T8/T14/T16 仍有未闭合项，发布准入未通过）

本文件是 StackUpUp 兼容性取舍、准入理由和回扩条件的决策记录。它只记录容量安全、Forge/vanilla
写入路径、Mixin 生态边界和已知模组兼容限制；不替代源码，也不把未运行或未复核的结果写成通过。

**当前构建事实**：MixinBooter `11.17` + CleanMix `0.7.2`（2026-09-15 由 11.13/0.7.1 成对升级，见 §8.9）；Mixin 配置通过 manifest
`MixinConnector` 装载 `StackUpUpMixinConnector : IMixinConnector`。10.7、`IEarlyMixinLoader`/`ILateMixinLoader`、旧注册方式和
JSONL 守恒报告均为历史基线，不得写成当前实现。T12 方案 A 已归档且不再作为 T13 依赖；T8.0 为 PARTIAL，T8.2/T13
为 BLOCKED，T14.1–T14.6 是当时查过留下的审计参考材料（不是待验收任务），T16 为 UNKNOWN（产物未形成、尚未开始）。T14.7 是一份已知限制清单，不是准入门。

文中术语固定如下：

- **已决策**：当前必须遵守的行为和安全底线。
- **当前实现**：源码中已经存在的行为；不等于最终准入结论。
- **当前限制**：已知但尚未解决的覆盖面或证据缺口。
- **历史证据**：旧版本、旧 loader 或已归档报告，仅用于对照。
- **拟议任务**：需要单独实现和验证的后续工作。

## 1. 已决策

### 1.1 容量安全不变量

对外广告的容量不得大于真实写入路径能够承载的容量。判断依据必须是目标对象的查询、限制和写入源码，不能依据类名、`== 64`
哨兵、注释或“主动表态”猜测。对一次真实 `IItemHandler#insertItem`，守恒审计使用：

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
3. **历史边界**：在动态 ASM 存在的中间版本中，已由 mixin 接管的目标还必须从动态 ASM 跳过表核对，不能重复注入；动态 ASM 已由删除提交 `9cff8e7` 移除，当前不存在该跳过表或重复注入面。
4. 只有 mixin 无法表达且有明确写入证据时，才考虑窄范围 ASM；当前不保留动态 ASM 实现，未来恢复必须重新取得完整目标、写入面和独立准入证据。

新的兼容补丁不按上游 StackUp 的 `IInventory`/`Slot` 继承关系无条件放大；当前不恢复或扩展已删除的动态 ASM 层，也不对未知 `IItemHandler` 动态扩容。

### 1.4 未知 `IItemHandler` 不扩容

`IItemHandler#getSlotLimit(slot)` 是 handler 的容量承诺，但未知实现的返回值不能证明其内部 setter、`insertItem`
或持久化路径能够承载更大堆叠。即使返回值字面上是 64，也不因此抬高 slot 广告。未知 handler 继续按真实可见上限处理；需要扩大时，必须先取得对应版本的写入源码或可重复的真实守恒证据，并为具体内层目标建立显式兼容任务。

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

不先改 `Slot#getItemStackLimit`、GUI 数字或客户端显示来掩盖服务端容量问题。客户端显示问题和服务端真实库存问题分开验证。重载方法必须登记完整 descriptor。

## 2. 当前实现（源码事实）

以下内容描述当前代码，不代表 T3/T10/T11 已完成收敛：

- `src/main/java/io/alexjoest/stackupup/mixin/early/ForgeItemHandlerLimitMixin.java:33-46` 的 `@Mixin` 目标只有 `net.minecraftforge.items.ItemStackHandler` 和 `net.minecraftforge.items.wrapper.EntityEquipmentInvWrapper` 两个类（T3 收敛后），`@ModifyReturnValue(method = "getSlotLimit", at = @At("RETURN"))` 把原值 `original == 64` 替换为 `StackLimitHooks.getCompatibilityStackSize()`，其余值保持原值。该值检查是目标类的取值语义（ItemStackHandler 恒为 64；EntityEquipmentInvWrapper 装甲槽 1、手部 64，装甲槽 1 必须保持），不再是「该目标是否可 patch」的准入判据（§3.5）。`InvWrapper`、`SidedInvWrapper`、`CombinedInvWrapper`、`RangedWrapper` 四个转发 wrapper 已不在注入目标内。
- `src/main/java/io/alexjoest/stackupup/mixin/early/SlotItemHandlerMixin.java:26-39` 只保留 `@Shadow getSlotStackLimit()` 与对 `getItemStackLimit` 的 `@ModifyReturnValue`：转调 `StackLimitHooks.resolveItemHandlerSlotLimit(stack, original, slotLimit)`（定义于 `src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt:175-189`）。`getSlotStackLimit` 的独立动态上限注入已移除，不存在 `Math.max` 分支；槽位广告自然跟随 `itemHandler.getSlotLimit(index)` 的真实来源。
- `src/main/java/io/alexjoest/stackupup/mixin/early/VanillaInventoryLimitMixin.java:28-41` 声明编译期显式目标表 12 个 vanilla 类，`getInventoryStackLimit()I` 的 `@ModifyReturnValue`（:43-50，`require = 0`）无条件替换为 `StackLimitHooks.getCompatibilityStackSize()`；运行时不再按 `original == 64` 哨兵判断（哨兵已删除，t2b §6.3），也不调用任何写入上限解析函数。
- `src/main/java/io/alexjoest/stackupup/mixin/late/AppEngAdaptorItemHandlerMixin.java`（方案 A，2026-08-08）不再包含任何注入：类保留为 AE2 late config 入口保险丝，`AdaptorItemHandler#addItems` 的 `insertItem` 调用原样执行——热路径零分支、零分配（不用 `@WrapOperation` + `operation.call`：`Operation.call(Object...)` 是 varargs，javac 产物含 Object[] 与装箱分配，字节码证据见 §3.8）。"不吞"由原版/Forge remainder 契约结构性保证，运行期由边界探针 `DevAutomationServerDriver#probeBoundaryLimit` 验证。
- `src/main/java/io/alexjoest/stackupup/core/Ae2ItemHandlerInsertLimiter.java`（方案 A 后）保留为纯工具类：只被 `Ae2ItemHandlerInsertLimiterTest` / `WrapperCapacityDiagnosticTest` 等测试护栏直接使用，已不在任何热路径上；对不在白名单的 handler 的 `min(64, getSlotLimit(slot))` 分片投喂逻辑原样保留（预先限幅而非写入后回填，§3.6 判定不变），原 ConservationAuditor 审计分支已随审计器整体移除（§3.8）。
- `src/main/java/io/alexjoest/stackupup/mixin/late/AppEngPatternTermMixin.java:24-56` 只在 AE2 三类 pattern terminal 构造结束后经反射提升空白 pattern 输入槽 `patternSlotIN`；该项目代码不证明 AE2 其他槽或第三方内部写入容量，相关目标仍须按源码和运行证据审查。
- `StackLimitHooks.resolveInventoryClampLimit` 定义于 `src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt:192-203`；当前两个 `InventoryPlayer` 业务调用点是 `src/main/java/io/alexjoest/stackupup/mixin/early/InventoryPlayerAddResourceMixin.java:19-21`（`stackupup$useMergeLimit`）和 `:42-44`（`stackupup$usePickedStackLimit`），二者均以 `@ModifyExpressionValue` 接入。`resolveInventoryWriteLimit` 在当前工作副本中已不存在（`rg -n "resolveInventoryWriteLimit" src/` 无命中），旧 inventory-write 状态通道与 `VanillaInventoryWriteMixin` 已随 T4a 移除；上述 resolver 及两个调用点仍必须保留，不得以“没有调用方”为由删除。

## 3. Forge 与 vanilla 写入事实

### 3.1 Forge handler 与 wrapper

| 目标 | 查询与写入证据 | 当前决策边界 |
| --- | --- | --- |
| `ItemStackHandler` | `getSlotLimit()` 返回 64：`build/rfg/minecraft-src/java/net/minecraftforge/items/ItemStackHandler.java:156-160`；`insertItem()` 在 `:79-116` 调用 `getStackLimit()`，该方法在 `:162-165` 取 `min(getSlotLimit, stack.getMaxStackSize())`，并返回 remainder | Forge 基类的该路径可按自洽证据审查；子类覆盖行为仍不能凭基类名推断 |
| `EmptyHandler` | `build/rfg/minecraft-src/java/net/minecraftforge/items/wrapper/EmptyHandler.java:47-49` 原样返回插入 stack，`:66-69` 的 slot limit 为 0 | 零容量且不会写入；可作为安全的拒绝目标，不能扩大 |
| `VanillaDoubleChestItemHandler` | `build/rfg/minecraft-src/java/net/minecraftforge/items/VanillaDoubleChestItemHandler.java:142-159` 把插入转给实际单箱 handler，`:183-187` 按访问的箱体读取 `getInventoryStackLimit()` | 转发 wrapper，不是独立容量来源；必须核对实际箱体写入面 |
| `InvWrapper` | `getSlotLimit()` 转发 `getInv().getInventoryStackLimit()`：`build/rfg/minecraft-src/java/net/minecraftforge/items/wrapper/InvWrapper.java:201-205`；`insertItem()` 使用该值计算写入量并在 `:73-155` 返回 remainder | 转发包装器不是独立容量来源；不得单独抬高 wrapper，需核对被包装的 `IInventory` |
| `SidedInvWrapper` | `insertItem()` 在 `build/rfg/minecraft-src/java/net/minecraftforge/items/wrapper/SidedInvWrapper.java:88-168` 依据 `getSlotLimit()` 写入并返回 remainder；`:230-234` 转发 `inv.getInventoryStackLimit()` | 同上；侧面 wrapper 的安全性取决于 delegate 的真实写入路径 |
| `CombinedInvWrapper` / `RangedWrapper` | `CombinedInvWrapper.java:109-134` 和 `RangedWrapper.java:66-103` 分别转发 `insertItem()`、`getSlotLimit()` | 仅作转发链处理，不作为独立 patch 来源 |
| `EntityEquipmentInvWrapper` | `insertItem()` 在 `build/rfg/minecraft-src/java/net/minecraftforge/items/wrapper/EntityEquipmentInvWrapper.java:86-123` 读取已有堆叠，计算 `limit`，只写入 `limit` 以内的数量，并在超量时返回复制出的 remainder；`:168-171` 的 `getStackLimit()` 是 `min(getSlotLimit, stack.getMaxStackSize())`；`:161-166` 的 slot limit 是 armor 为 1、手部为 64 | **不能写成“无余量必吞”。Forge 的 `insertItem` 有上限计算和 remainder。** 但 `setStackInSlot()` 在 `:173-179` 直接调用实体 setter，绕过 `insertItem` 的限制；整体扩容仍须分别验证 Forge 插入路径和 vanilla 实体存储路径 |

### 3.2 EntityEquipment 的 Forge/vanilla 分界

Forge wrapper 文档明确它通过 `EntityLivingBase#getItemStackFromSlot` / `setItemStackToSlot` 暴露装备和手部库存（`EntityEquipmentInvWrapper.java:34-38`），因此必须把两层事实分开：

- Forge 层 `insertItem` 自己计算上限、执行 `simulate` 分支、限制写入数量并返回 remainder——可核对的 Forge API 行为，不能以旧的“setter 无 clamp”描述替代。
- vanilla `EntityLiving` 的 `getItemStackFromSlot` / `setItemStackToSlot`（`build/rfg/minecraft-src/java/net/minecraft/entity/EntityLiving.java:999-1022`）直接读写 `inventoryHands` / `inventoryArmor`，setter 本身没有按 64 截断。
- vanilla `EntityPlayer` 对应实现（`build/rfg/minecraft-src/java/net/minecraft/entity/player/EntityPlayer.java:2416-2449`）直接读写主手、副手和 armor 列表；`InventoryPlayer.setInventorySlotContents`（`build/rfg/minecraft-src/java/net/minecraft/entity/player/InventoryPlayer.java:610-629`）也只是写入列表。

结论：Forge wrapper 的 `insertItem` 路径不能预先判为断链；vanilla setter 也不能单独证明动态装备容量已经被接管。手部槽和 armor 槽必须分别测试，armor 的 slot limit 为 1 的不可堆叠语义不应被“回扩”改变。

### 3.3 vanilla `IInventory` 事实

- `InventoryBasic.setInventorySlotContents`（`build/rfg/minecraft-src/java/net/minecraft/inventory/InventoryBasic.java:143-153`）读取自身 `getInventoryStackLimit()` 并截断。
- `TileEntityLockableLoot.setInventorySlotContents`（`build/rfg/minecraft-src/java/net/minecraft/tileentity/TileEntityLockableLoot.java:155-166`）采用同类 clamp。
- `InventoryLargeChest.getInventoryStackLimit()`（`build/rfg/minecraft-src/java/net/minecraft/inventory/InventoryLargeChest.java:203-206`）只转发上半箱；但 `setInventorySlotContents()`（`:188-198`）按 index 写上半箱或下半箱。两半上限不一致时广告来源与写入来源不闭合，不能仅凭现有原版默认值把它判为安全。
- `ContainerEnchantment` 的匿名 `InventoryBasic` 子类（`build/rfg/minecraft-src/java/net/minecraft/inventory/ContainerEnchantment.java:47-55`）明确返回 64；`TileEntityBeacon.getInventoryStackLimit()`（`build/rfg/minecraft-src/java/net/minecraft/tileentity/TileEntityBeacon.java:459-462`）明确返回 1。它们是主动限制或功能缺口，不应因未扩大就被写成吞物证据。

### 3.4 两个相邻证据门

- **1.12.2 `ResourceLocation`**：`build/rfg/minecraft-src/java/net/minecraft/util/ResourceLocation.java:40-55` 的 `splitObjectName()` 只查第一个冒号，path 取从该冒号之后的完整子串，可以保留多个冒号。DSL 不能按冒号数量否定合法 path，也不能把第三段未经 grammar 证据解释成 meta；应按实际 DSL grammar、源码和测试判定。
- **F3+T 资源重载**：`build/rfg/minecraft-src/java/net/minecraft/util/text/translation/LanguageMap.java:98-103` 的 `replaceWith()` 清空全局语言表后重新放入传入表，缺键时 `:131-137` 回退为 key。任何本地化兼容改动都必须先通过真实客户端 F3+T 观察完整 Forge 资源重载链；单独调用 `LanguageMap.replaceWith` 只能作辅助回归，不能冒充真实 F3+T 基线。

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
| `items/SlotItemHandler#getItemStackLimit(ItemStack)I` | 自洽 | `:112-140`（广告由 handler 侧 `insertItem(..., true)` simulate 求得），再由 `StackLimitHooks.resolveItemHandlerSlotLimit`（src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt:175-189）按 min(slotLimit, itemLimit) 收敛 | 保留 |
判定结论与红线核对：

- 四个转发 wrapper 与 `SlotItemHandler#getSlotStackLimit` 不再作为独立容量来源注入；`EntityEquipmentInvWrapper` 未写成「无余量必吞」（Forge `insertItem` 上限计算与 remainder 由源码闭合，vanilla setter 为无截断直写）。
- 实现后 `ForgeItemHandlerLimitMixin` 的 `original == 64` 值检查是目标类的取值语义（两个自洽目标的 `getSlotLimit` 只返回 1 或 64，装甲槽 1 必须保持），不再是「该目标是否可 patch」的准入判据——目标集合由本表三分类固定。
- 未使用 `@Accessor` 猜 delegate 容量；未引入写入后余量补偿。
- 守恒护栏：`src/test/kotlin/io/alexjoest/stackupup/core/WrapperCapacityDiagnosticTest.kt` 覆盖 ItemStackHandler（64 与提升态）、四个转发 wrapper（不注入、remainder 闭合、提升的底层来源自然跟随）、EntityEquipment（手部提升态与装甲槽 1）、未 patch 的 `IInventory` stub 包装场景，以及两个 mixin 的字节码结构检查（转发目标已移除、slot 独立注入已移除）。
- 未决事项（已收口）：`DevWrapperCompatProbes.kt` 的转发 wrapper 探针期望已由 §3.12 修正为 `delegateInventoryStackLimit(inventory)`；T2a 登记表 §2.3 的 mixin 目标行数（6→2）已随之刷新。

### 3.6 T10 AE2 投喂白名单重排判定（2026-08-08 执行）

按 T2a 登记表三分类与 §3.5 收敛结果，对 `Ae2ItemHandlerInsertLimiter#isTrusted` 的 6 个白名单项逐项核验 Forge 写入链源码后重排。判定规则：白名单只直通写入链全部由项目源码闭合的实现；转发 wrapper 的 delegate 为任意第三方可构造类型时，其写入面无源码不可判定，不得由 `instanceof` 直通（红线：不将转发安全性与未知第三方混同）。

| 白名单项 | T2a/T3 分类 | 写入链源码证据（build/rfg/minecraft-src/java/） | T10 判定 | 动作 |
| --- | --- | --- | --- | --- |
| ItemStackHandler | 自洽 | `net/minecraftforge/items/ItemStackHandler.java:79-117`（insertItem 写前读 `getStackLimit`、落库并返回 remainder）、`:157-165`（getSlotLimit=64、getStackLimit=min） | 自洽，写入面无 delegate | 保留 trusted |
| VanillaDoubleChestItemHandler | 转发但 delegate 固定 | `.../items/VanillaDoubleChestItemHandler.java:142-160`（insertItem 转发 `getSingleChestHandler`）；`net/minecraft/tileentity/TileEntityChest.java:431-434`（getSingleChestHandler = super.getCapability）；`TileEntityLockable.java:75-78`（createUnSidedHandler = `new InvWrapper(this)`，delegate 固定为原版箱体）；`TileEntityLockableLoot.java:155-166`（setter 按同一 `getInventoryStackLimit` 夹取）；我方 `src/main/java/.../mixin/early/VanillaInventoryLimitMixin.java:28-41`（TileEntityChest 在编译期表内） | 链路由项目源码闭合且自洽 | 保留 trusted |
| EntityEquipmentInvWrapper | 自洽（P0 事实 a） | `.../items/wrapper/EntityEquipmentInvWrapper.java:86-123`（limit=getStackLimit、落库、remainder）、`:162-171`（装甲 1/手部 64） | 自洽；不得写成无余量必吞 | 保留 trusted |
| EmptyHandler | 零容量拒绝目标 | `.../items/wrapper/EmptyHandler.java:47-50`（原样返回输入 stack）、`:66-69`（slot limit 0） | 无写入路径，返回完整余量 | 保留 trusted |
| InvWrapper | 转发 | `.../items/wrapper/InvWrapper.java:73-158`（insertItem 计算 m 后转发任意 `IInventory#setInventorySlotContents`）、`:202-204`（getSlotLimit 转发） | wrapper 自身计算 limit 并返回 remainder，但 delegate 为任意 IInventory；第三方写入面无源码不可判定，instanceof 无法区分 delegate 类型 | **移出 trusted** → `min(64, getSlotLimit)` 限流分片 |
| SidedInvWrapper | 转发 | `.../items/wrapper/SidedInvWrapper.java:88-172`（insertItem 转发任意 ISidedInventory）、`:231-233`（getSlotLimit 转发） | 同上，delegate 为任意 ISidedInventory | **移出 trusted** → `min(64, getSlotLimit)` 限流分片 |
判定结论与红线核对：

- 移入 0 项：自洽集合（ItemStackHandler、EntityEquipmentInvWrapper）与零容量拒绝目标（EmptyHandler）原已在白名单内，无新的自洽 IItemHandler 类需要移入；未为功能便利扩大白名单。
- 移出后行为：InvWrapper/SidedInvWrapper 进入 untrusted 分片路径（cap=`min(64, getSlotLimit)`）。对原版自洽 delegate（InventoryBasic/TileEntityLockableLoot 系 setter 按同一上限夹取）分片全部接受，结果与直通等价；对未知第三方 delegate 单次投喂上限更紧（原设计，方案 A 后该路径仅测试使用，不再处于热路径）。
- `EntityEquipmentInvWrapper` 未移出、未写成「无余量必吞」：Forge insertItem 的上限计算与 remainder 由源码闭合（引用 P0 事实 a）；白名单只决定投喂方是否直通，不影响其自洽写入。
- 静态白名单不替代运行期验证：方案 A 后 `insertCapped` 不再处于热路径（mixin 已透传，§3.8），白名单/分片行为仅由测试护栏覆盖；运行期"不吞"由 remainder 契约结构性保证 + 边界探针验证，不再有守恒审计事件。
- 剩余风险：`instanceof ItemStackHandler` 会信任第三方子类覆盖的 `insertItem`/`getStackLimit`，子类行为无源码不可判定；该静态性风险与重排前一致，不因本任务扩大。方案 A 后我方不干预投喂语义，未知子类的写入行为交由目标侧 remainder 契约自回收，我方不再观测。

测试：

- `Ae2ItemHandlerInsertLimiterTest`：白名单内（ItemStackHandler 真实/模拟插入与 remainder 86、EmptyHandler 完整余量、VanillaDoubleChestItemHandler 无邻箱直通）与白名单外（分片、cap=0 不调用、模拟单分片、部分接受 remainder）覆盖；原 InvWrapper 信任断言更新为分片断言（150 → [64,64,22]）。
- `Ae2ItemHandlerInsertLimiterConservationAuditTest` 已随 ConservationAuditor 整体删除（§3.8）；移出项/保留项守恒行为由 `Ae2ItemHandlerInsertLimiterTest` 与 `WrapperCapacityDiagnosticTest` 的 `stored + remainder == offered` 断言继续覆盖。
- `WrapperCapacityDiagnosticTest#ae2Limiter_trustedInvWrapper_overUnexpandedInventory_conservesItems`（T3 端到端，租约外未改动）只断言守恒公式、不断言调用次数，移出后仍通过。

### 3.7 T13 回填记录（2026-08-08 执行）

> **方案 A 更新（2026-08-08）**：本小节为历史回填记录。守恒审计层（ConservationAuditor/ConservationEvent/ConservationReportWriter 与 JSONL 报告）已按用户决策整体移除（§3.8），`run/logs/stackupup-conservation.jsonl` 不再生成；下述事件号与 JSONL 行是当时运行时证据，保留作历史事实，不代表当前实现仍存在该观测通道。
> 本节是 T13「覆盖面收缩与回填」的 T2 回填收口产出（T12.5 第三种状态的关键动作）：只回填证据与判定引用，不修改生产实现；运行时事件逐条引用 run/logs/stackupup-conservation.jsonl 原始行（schema v1，事件号按行序）。配套回填：docs/agent/t2a-容量站点登记表.md §9（证据来源标注与收缩刷新）。

**实际收缩内容（T3/T10 已执行，本回填登记）：**

- T3（§3.5）：`ForgeItemHandlerLimitMixin` 目标 6→2——移除 InvWrapper、SidedInvWrapper、CombinedInvWrapper、RangedWrapper 四个转发 wrapper 的独立注入；`SlotItemHandler#getSlotStackLimit` 移除独立动态上限注入（仅保留 @Shadow 读取，槽位广告自然跟随 handler 侧真实来源）。
- T10（§3.6）：AE2 投喂白名单移出 InvWrapper/SidedInvWrapper（转发 wrapper，delegate 任意 IInventory/ISidedInventory，第三方写入面无源码不可判定），进入 `min(64, getSlotLimit)` 限流分片；白名单保留 ItemStackHandler、VanillaDoubleChestItemHandler、EntityEquipmentInvWrapper、EmptyHandler，移入 0 项。
**守恒收益（T12.5 JSONL 逐条关联调用点）：**

| 事件 | callSite | handler 类名 | slot | simulate | offered | storedDelta | remainderCount | balanced | 关联调用点（我方源码） |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| #1 | DevAutomationServerDriver#probeTarget | net.minecraftforge.items.ItemStackHandler | 0 | false | 128 | 128 | 0 | true | DevAutomationServerDriver.kt:264-277（probeTarget 真实写入 + audit）；T2a §2.3 ItemStackHandler / ForgeItemHandlerLimitMixin.java:43-46 |
| #2 | Ae2ItemHandlerInsertLimiter#insertCapped | example.TruncatingThirdPartyHandler | 2 | false | 128 | 64 | 0 | false | Ae2ItemHandlerInsertLimiter.java:23-63（insertCapped）、:82-97（isTrusted）；该事件为方案 A 前历史观测，当时的 wrap 入口 `AppEngAdaptorItemHandlerMixin` 已按 §3.8 改为无注入，当前热路径不再可达 |
| #3 | DevAutomationServerDriver#probeTarget | net.minecraftforge.items.ItemStackHandler | 0 | true | 128 | 0 | 128 | true | 同 #1；simulate 只记录，不计守恒、不构成真实写入证据 |

- 事件 #1：ItemStackHandler 提升态下单次真实写 128 全量落库、余量 0、守恒成立——运行时确认 T3 保留注入的 ItemStackHandler 站点真实写入面闭合。offered=128 与 DevAutomationConfig 默认 count=128（DevAutomationConfig.kt:91）一致。
- 事件 #2：唯一不平衡真实事件。offered=128 以单事件出现 ⇒ 走 `insertCapped` trusted 直通分支（untrusted 分片每次 ≤ `min(64, getSlotLimit)` ≤ 64，Ae2ItemHandlerInsertLimiter.java:26-32），即该 handler 经 `isTrusted` 的 instanceof 命中白名单四类之一（或其子类），但实际只落 64 且不返回余量——是 §3.6 记录在案的「instanceof 信任第三方子类覆盖的 insertItem/getStackLimit，子类行为无源码不可判定」残余风险的一次运行时实例（此句由调用点源码 + 事件数值推断，非直接类源码证据）。类名 `example.TruncatingThirdPartyHandler` 在项目源码/测试/dev 探针中无任何声明，`run/mods/` 与 `local-dev-mods/` 均为空，**归属 UNKNOWN（疑似探针合成名或外部合成类，t14.7-发布前矩阵.md 与 t2a 登记表 §9.1 同记）**；不可据其升级任何 T2a 登记条目，也不改变任何三分类。
- 事件 #3：simulate 事件，按 §6 T12 规则只记录、不参与守恒统计/判定/告警。
- 汇总文件（stackupup-conservation-summary.json）与 JSONL 逐条一致：totalEvents=3、simulateEvents=1、unbalancedRealEvents=1、unbalanced=[{example.TruncatingThirdPartyHandler, Ae2ItemHandlerInsertLimiter#insertCapped, 1}]。

**功能缺口：** 无。T3/T10 收缩未引入功能缺口：移除项均有测试覆盖（WrapperCapacityDiagnosticTest 覆盖四个转发 wrapper 不注入与 remainder 闭合、SlotItemHandler 独立注入移除护栏；Ae2ItemHandlerInsertLimiterTest 覆盖移出项分片守恒，`Ae2ItemHandlerInsertLimiterConservationAuditTest` 已随方案 A 删除）；本回填未修改任何生产代码、测试或构建配置。

**结论三态标注（T13 矩阵口径：已回扩 / 决定不回扩（附理由）/ 等 T12 审计数据；方案 A 后"等 T12 审计数据"一档由边界探针与守恒行为测试替代）：**

- ItemStackHandler：未收缩（T3 保留注入）。运行时事件 #1（真实写 128 守恒成立）与 #3（simulate）支持现状；按未收缩项登记，无回扩处置。
- EntityEquipmentInvWrapper：未收缩（T3 保留注入，装甲槽 1 不提升）。手部槽回扩按 T13 准入需所有实际投喂入口通过真实守恒测试；当前无运行时事件 → 维持不回扩，验证由守恒行为测试与边界探针承担。
- InvWrapper / SidedInvWrapper / CombinedInvWrapper / RangedWrapper（T3 移出）：**决定不回扩**——转发语义，delegate 为任意 IInventory/ISidedInventory/子 handler，包装器不是独立容量来源；JSONL 无任何 wrapper 类事件。InvWrapper/SidedInvWrapper 回扩须按 T13 准入「T12 观测到具体内层类 + 该版本源码或可重复守恒证据」，当前无此输入。
- InvWrapper / SidedInvWrapper AE2 白名单（T10 移出）：**决定不回扩**——§3.6 判定（instanceof 无法区分 delegate 类型，第三方写入面无源码不可判定）；事件 #2 类名不是白名单外 wrapper 类，不构成回扩依据。
- late 第三方 18 项：保持 **无源码不可判定**（缺失 jar，§5.4 台账），回填不改变。
- InventoryLargeChest：不在表（t2b §3.1，转发/条件断链），维持不回扩；T13 回扩准入（`min(upper, lower)` 且两半一致）当前无运行时数据输入。

**T12.5 三态诚实声明（本回填后的状态；方案 A 已废弃守恒审计任务，见 §3.8）：**

- 已闭合：报告可生成/可解析/可追溯（schema v1，3 事件逐条可回到调用点与 handler 类名）；T2a 登记表逐站点标注证据来源（t2a §9.2，无源码项保持 UNKNOWN）；本小节回填完成。
- 未闭合（如实标注，不得写成第三种状态）：该历史阶段服务端矩阵 `runServerAutoTestMatrix` **未全量执行**，当时 JSONL 仅 3 事件、覆盖 ItemStackHandler 单站点；T12.4 关闭态门运行时判定 UNKNOWN；事件 #2 归属 UNKNOWN。因此 **T12.5 第三种状态未完全达成，本回填为部分闭合（PARTIAL）**；方案 A 后该门不再作为解锁条件，运行期验证由边界探针承担。
### 3.8 方案 A：不吞物品回退策略（2026-08-08 执行）

用户决策：结构性移除热路径逻辑，依赖原版/Forge remainder 契约保证"不吞"，并移除 ConservationAuditor。事实链：原版/Forge 天然"超量不吞"——`InventoryPlayer.addResource`（双上限夹取返回剩余）、`Container.mergeItemStack`（余量留参数栈）、`ItemStackHandler.insertItem`（超量返回 remainder）；硬编码返回 64 的 mod 在 insertItem 按上限夹取时回退到 64、remainder 返回剩余、不吞（用户确认语义）。

- **热路径零逻辑**：`AppEngAdaptorItemHandlerMixin.java` 不再包含任何注入（类保留为 AE2 late config 入口保险丝），AE2 `AdaptorItemHandler#addItems` 的 `IItemHandler#insertItem` 调用原样执行——mixin 层零分支、零分配。初始方案曾尝试 `@WrapOperation` 改调 `operation.call(handler, slot, stack, simulate)`，但编译产物（javap 字节码证据）显示 `Operation.call(Object...)` 是 varargs：每次调用生成 `Object[4]` 数组与 Integer/Boolean 装箱，违背零分配约束，故最终采用"不包裹、直接放行原调用"的最简形态。我方不干预即可保证"我方不吞"：AE2 收到的堆叠若超过其写入容量，remainder 由 AE2 自身回收（AE2 无源码，`无源码不可判定`，但我方不再执行任何限幅/分片/补偿）。
- **ConservationAuditor 移除**：删除 `src/main/kotlin/io/alexjoest/stackupup/audit/` 整包（ConservationAuditor/ConservationEvent/ConservationReportWriter）与 4 个审计测试；`DevAutomationServerDriver` 删审计事件块（探针判定 evaluateProbeResult/evaluateBoundaryProbe 不依赖审计，保留）。
- **Ae2ItemHandlerInsertLimiter 保留为纯工具类**：仅测试护栏直接使用，不在热路径上；白名单/分片行为原样保留（预先限幅语义不变），不再承担任何运行期审计。
- **"不吞"验证改由边界探针承担**：`DevAutomationServerDriver#probeBoundaryLimit` 验证 `insertItem(N)` 全部存入（remainder=0）+ 追加 `insertItem(1)` 被拒（remainder=1），N 为规则解析值。
- **删除的文档**：`docs/agent/t12.5-报告schema.md`（JSONL 报告 schema 随审计器废弃）。

### 3.9 六 mod 源码审阅后的 late mixin 修正与新增（2026-08-08 执行）

本批次基于 `C:\dev\mc\1.12.2\mods-under-test\` 下六个 mod 的 1.12.2 源码审阅结论执行。DeepWiki MCP 工具不在本会话可用工具列表中（无 `mcp__deepwiki__*` 注入），按降级链使用官方仓库/运行时依赖字节码核对，缺口如实记录。

**A. AE2 两个内部库存 mixin 的死注入修正（必须项）。**
- 事实：`appeng/tile/inventory/AppEngInternalInventory.java:70` 与 `AppEngInternalAEInventory.java:225` 的真实容量源是 `getSlotLimit(int)`，两类均无 `getInventoryStackLimit` 方法；`<init>` 处的字面量 64 真实（AppEngInternalInventory.java:62、AppEngInternalAEInventory.java:51）。
- 修正：删除两个 mixin 中 `@ModifyReturnValue(method = "getInventoryStackLimit")`（require=0 静默死注入），保留 `<init>*` 的 `@ModifyConstant(intValue=64)` 补丁；不改写 `getSlotLimit` 热路径（热路径零逻辑原则）。同步重写 `Ae2MixinSourceTest` 的断言（只覆盖构造常量 + 断言死注入不再出现）。

**B. NuclearCraft：接口 default 注入可行性验证与最小方案选择。**
- 事实：`nc/tile/inventory/ITileInventory.java:95-97` 的接口 default `getInventoryStackLimit()=64` 是所有夹取点的唯一来源：`setInventorySlotContents` 写前截断（:78-80）、`nc/tile/internal/inventory/ItemHandler.java:157-159` 的 `getSlotLimit`、vanilla GUI Slot 跟随。直接实现 ITileInventory 的 6 个类中，4 个抽象基类（TileInventory/TileFluidInventory/TileEnergyInventory/TileEnergyFluidInventory）未覆写该方法；深层显式覆写（TileDistributorOutlet/Inlet、TilePassiveAbstract、TileDummy、fission 单元、InfoProcessorElement 等）与 TileBin（vanilla IInventory）语义各异，必须保持。
- 可行性验证（mixinbooter 11.13 运行时 jar 反编译字节码，非推测）：
  1. **类 mixin 注入接口 → 应用期硬抛异常**：`MixinInfo$SubType$Standard` 继承基类 `validateTarget`（`targetMustBeInterface=false`），接口目标触发 `InvalidMixinException("target type mismatch: ... is an interface")`，发生在 `MixinProcessor.applyMixins` 的 `mixin.validate()`（异常非 required 配置不吞，直接传播为 MixinTransformerError）。
  2. **接口 mixin（mixin 类=interface）+ 注入器 → Java 8 toolchain 下 ClassFormatError**：`SubType.Interface`（`targetMustBeInterface=true`）允许接口目标，`INJECTORS_IN_INTERFACE_MIXINS` 在 JAVA_8 配置下启用（`MixinApplicatorInterface.applyInjections` 放行）；但 `MixinPreProcessorInterface.prepareMethod` 对 Java 8 编译的公开 handler 置 `private+synthetic`，handler 经 `applyMethods/mergeMethod` 合并进目标接口，而 `applyAttributes` 只抬升不降低 class version（Java 8 mixin → minRequiredClassVersion=52）——private 接口方法要求 class file >= 53，v52 接口加载即 ClassFormatError。
- 结论：**接口 default 注入在本项目（Java 8 toolchain）不可行**，按任务降级链选「a) patch 具体实现类链上的公共基类」：`NuclearCraftTileInventoryLimitMixin` 对 4 个抽象基类各合并一个与接口同签名的具体覆写 `getInventoryStackLimit()`（`findTargetMethod` 按 name+desc 匹配，基类未声明 → 作为新方法合并，无需 @Overwrite；class 目标加方法任意版本合法）。「b) ItemHandler.getSlotLimit」单独不成立：`setInventorySlotContents` 的写前截断仍按 `getInventoryStackLimit()`=64，广告大于真实写入容量（违反容量不变量）。
- 配套：新增 `mixins.stackupup.late.nuclearcraft.json`（required:false、JAVA_8）、`Constants.LATE_NUCLEARCRAFT_MIXIN_CONFIG`、`MixinToggles.nuclearCraft`、loader 模块登记（modid "nuclearcraft"，`nc.Global.MOD_ID` 证据）；重写 `NuclearCraftMixinSourceTest`（原测试断言配置不存在，现断言基类收敛 + 禁止 split-only/ItemHandler-only patch）。
**C. EnderIO（CEu 内置 EnderCore）InventorySlot 新增 mixin。**
- 事实：`endercore/.../common/inventory/InventorySlot.java:233-235` 的 `getMaxStackSize()` 返回 limit（构造默认 `limit > 0 ? limit : 64` :96），是槽容量单一事实源：`getSlotLimit(int)` 委托它（:246-248）、机器 `AbstractCapabilityMachineEntity.java:134` 直接读它、GUI `EnderSlot.getSlotStackLimit` 委托它（EnderSlot.java:86）；写入面 `insertItem` 以 `min(getMaxStackSize(), stack.getMaxStackSize())` 夹取（:120/:139）——广告值与真实写入容量同源。
- 实现：`EnderIOInventorySlotLimitMixin`，`@ModifyReturnValue(method="getMaxStackSize()I", require=0)`，`original == 64 ? compat : original` 守卫；@Pseudo 兼容旧 EnderIO 1.5-1.12（无该类，目标缺失静默跳过）。
- 子类覆写语义（验证结论）：Mixin 只改写目标类目标方法的方法体字节码；TileCrafter 匿名覆写（TileCrafter.java:96-101，`bufferStacks ? 64 : 1`）有独立字节码并遮蔽基类实现，基类注入对其不生效——该覆写与显式 limit（电容槽 1）原样保留，本次不改 TileCrafter 缓冲槽语义。

**D. Colossal Chests：不实现。** 工作树是现代版（1.8.16/MC26），1.12.2 源码在 `origin/master-1.12` 分支；本批次不 clone/切换，目标类签名无 1.12.2 版本源码证据，待单独处理（无源码不可判定，不写 mixin）。

**F. IE / IC2：不实现。** `mods-under-test/` 未 clone ImmersiveEngineering 与 IC2（IndustrialCraft 2），现有 `IEInventoryHandlerMixin`/`IEMachineSlotLimitMixin`/`InvSlotMixin` 等目标签名无法与 1.12.2 版本源码核对，维持无源码不可判定，本批次不动。

**红线核对：** 未新增 @Redirect/@Overwrite；未触碰写入副作用（AE2 构造常量、NC 基类广告值、EnderIO 广告值三处均为广告/容量来源替换）；热路径零逻辑（AE2 删死注入后仅构造期常量补丁；NC/EnderIO handler 一次判断零分配）；未 clone 类不写 mixin（D/F）。运行时行为（NC/EnderIO 目标在真实 MC 启动中的装载与应用）因 `run/mods` 无对应 mod 无法在本批验证，只做结构检查与字节码级静态验证，如实记录。

### 3.10 EnderIO/NC 溢出掉落移除（2026-08-09 执行，用户明确：不允许掉落、塞不下就不塞、不吞；NC 不裁不丢靠自然排空）

用户明确移除 EnderIO legacy 机器与 NC Distributor 的"溢出掉落"行为，且不允许用吞并/补偿掩盖（遵循 §1.5 否决写入后余量补偿）。历史一致性：最早 EnderIO mixin（提交 058e128）只提升上限广告，从未处理掉落，本批是首次主动移除掉落分支。

**A. EnderIO legacy 机器 setter 掉落分支。**
- 事实（mods-under-test/EnderIO-1.5-1.12/enderio-base/src/main/java/crazypants/enderio/base/machine/baselegacy/AbstractInventoryMachineEntity.java:183-190；EnderIO-CEu 同文件 :182-190，两版签名一致）：`setInventorySlotContents` 溢出分支 = 槽内 `setCount(limit)` 夹取（:186/:185，写入面既有行为）+ 调用方栈 `contents.shrink(limit)`（:187/:186）+ `Block.spawnAsEntity(world, pos, contents)` 掉落（:188/:187）。该分支在 wrapper（`LegacyMachineWrapper.doInsertItem` :75-115，已夹取 min(itemMax, limit) 并返回 remainder）下不可达；但第三方直调 IInventory setter 时掉落与吞并真实可达，按用户要求主动消除。
- 实现：`EnderIOInventoryNoDropMixin`，@Pseudo + require=0，两个 `@WrapOperation` 分别把 `ItemStack.shrink(I)V` 与 `Block.spawnAsEntity(...)V`（方法内各仅一处调用，无需 ordinal）替换为 no-op：剩余物既不 shrink（不吞，留在调用方传入栈），也不掉落；`setCount` 夹取分支与 `markDirty` 原样保留（不触碰写入面其他副作用）。注入器选择：按 §8.3 不新增 @Redirect；抑制语义刻意不调用 original，属明确可选目标（mod gate + @Pseudo + require=0 缺失诊断），失败方向安全（注入不生效维持原掉落，不引入新丢失）。
**B. NC Distributor 裁减与掉落。**
- 事实（mods-under-test/NuclearCraft/src/main/java/nc/multiblock/distributor/Distributor.java）：`cullInventory`（:204-216）存量 > itemStackLimit 时 `setCount(itemStackLimit)` 裁减（:211）+ `dropOverflow` 掉落（:214）；触发场景是容量收缩事件（部件拆装 `refreshCapacity` :192、NBT 装载 `syncDataFrom` :532），非投喂。`dropOverflow`（:218-226）第二调用点 `onAssimilate` :131（多块合并时 `insertIntoInventory` 剩余，:478-506 按 itemStackLimit 夹取后返回 remainder）。
- 实现：`NuclearCraftDistributorNoDropMixin`，@Pseudo + require=0，两个 `@Inject(HEAD, cancellable)`：`cullInventory()Z` 恒 `setReturnValue(false)`（不裁不丢，保留超限存量，靠 `distributeItems` :289-352 每 tick 自然排空；入口满限时 remainder 由投喂方路径自行退回）；`dropOverflow(Ljava/util/List;)V` HEAD 取消，一处注入覆盖两个调用点（用户要求"不允许掉落"同样适用于合并场景）。`changed` 返回值只影响同步频率，恒 false 不影响功能。两方法体除裁减/掉落外无其他副作用（逐行核对），取消不波及其他副作用。
- 合并场景观察（如实记录，非本次新增行为）：vanilla 合并路径 `Multiblock.assimilate`（:462-491）先 `_disassembleMachine()` 弃置被吸收方（不触碰其 inventoryStacks，`_disassembleMachine` :442-449 与 `onMachineDisassembled` :94 均不处理物品），`insertIntoInventory` 收到的只是 `assimilatedStack.copy()`（:129），原栈本就随被吸收方对象弃置——本 mixin 只取消了"剩余 copy 掉落为实体"这一动作，不改变原栈弃置路径，也不引入新的物品丢失机制。

**红线核对：** 未新增 @Redirect/@Overwrite；未改上限广告 mixin；未触碰写入面其他副作用（EnderIO 保留 `setCount` 夹取与 `markDirty`；NC 保留 distributeItems 排空路径）；注入均 @Pseudo + require=0；失败方向安全（注入不生效维持 mod 原行为，不引入新丢失）。运行时行为（真实 MC 装载与应用）因 `run/mods` 无 EnderIO/NC mod 无法本批验证，只做源文本断言与编译产物字节码结构检查，如实记录。
- 生产环境 refmap 风险：EnderIO mixin 的 vanilla `@At`（`ItemStack.shrink` / `Block.spawnAsEntity`）在 dev 运行（MCP 名）可命中，生产 SRG 混淆下不重映射会静默不命中；`require=0` 使失败方向安全（维持 mod 原掉落，不引入新丢失），建议生产部署验证时处理 refmap/remap。NC mixin 的 `@Inject HEAD` 无 vanilla 成员目标，不受影响。

### 3.11 Colossal Chests / GregTech late mixin 新增（2026-08-09 执行，用户要求兼容）

基于 `mods-under-test/` 源码审阅（Colossal Chests 1.12.2 1.7.3 在 `master-1.12` 的 `origin/master-1.12` 分支，`git describe` = `1.12.2-1.7.3-3-g4ce3e729`；GregTech CEu 2.8.7-beta 工作树），新增 3 个 late mixin 与 2 个 JSON 配置。

**A. Colossal Chests：`ColossalChestsTileMixin`（字段级源头修正）。**
- 事实：`org.cyclops.colossalchests.tileentity.TileColossalChest` 的 `constructInventory()`（:232-238）与 `constructInventoryDebug()`（:240-248）共 4 处 `new IndexedInventory/LargeInventory(size, name, 64)` 第三参即栈上限（:236/:237/:241/:242），是 TE 库存容量的唯一构造来源：`getInventory()`（:422-433）在 inventory 为 null 或尺寸变化时懒重建；NBT 持久化不存上限字段（`readFromNBT` :267-284 只处理客户端跳过）；`setSize` 迁移（:186-200）经 `setInventorySlotContents` 夹取，上限变大后无截断。两方法内 64 字面量逐行核对恰好 4 处且全部是栈上限，无其他 64。
- 实现：`@ModifyConstant(method={"constructInventory","constructInventoryDebug"}, constant=@Constant(intValue=64), require=0, remap=false)` 一个 handler 覆盖 4 处，无条件替换为 `StackLimitHooks.getCompatibilityStackSize()`。`:211` 的 `new LargeInventory(0, "invalid", 0)` 是 0 上限占位，intValue=0 不匹配，不在范围。
- 与 `SimpleInventoryMixin`（cyclopscore 配置，使用期 clamp）并存一致：前者使用期夹取、这里是字段级源头修正，广告值与真实写入面同源。副作用保全：setSize 迁移截断、IndexedInventory.createIndex（:138-162 无栈校验）、onInventoryChanged/hash、NBT 持久化均不触碰。

**B. GregTech：`GregTechMetaItemMixin` / `GregTechMetaPrefixItemMixin`（直呼面归一 + 结果缓存增强）。**
- 事实：`MetaItem.getItemStackLimit(ItemStack)I`（gregtech/api/items/metaitem/MetaItem.java:323-330，MetaItem extends Item :107）按 meta 区分，meta 无效返回 64（:327），否则 `MetaValueItem.getMaxStackSize`（默认 `maxStackSize=64` :781，电池 8/工具 1 等显式覆盖）；`MetaPrefixItem`（:44 extends StandardMetaItem）覆写同签名方法（gregtech/api/items/materialitem/MetaPrefixItem.java:145-147），按 OrePrefix 返回（prefix 为 null 时 64 :146，否则 `prefix.maxStackSize`，plateDense=7 等显式覆盖）。early `ItemMixin` 只改写基类 `Item.getItemStackLimit` 方法体，对两个覆写（独立字节码）不生效；`ItemStackMixin` 覆盖 `getMaxStackSize()` 调用点层。本 mixin 补直呼面：`item.getItemStackLimit(stack)` 直接调用返回与 ItemStack 层一致的动态上限。
- 实现：两个 mixin 同构 ItemMixin——`@ModifyReturnValue(method="getItemStackLimit(Lnet/minecraft/item/ItemStack;)I", remap=false, require=0, at=@At("RETURN"))`，handler 顺序 `lookupResolvedItemLimit`（直呼面复用 ItemStack 层已解析缓存，避免重算）→ 未命中 `applyDynamicStackLimit`（内部基线解析经 `shouldBypassDynamicItemRules` 直读底层 per-meta 值，无递归、无双重应用）→ `cacheResolvedItemLimit`。规则未命中返回 original（= 各 per-meta/OrePrefix 原始上限），电池 8/工具 1/plateDense=7 语义无损。
- 机器/总线已由 ForgeItemHandlerLimitMixin 覆盖，本批不动。

**红线核对：** 未新增 @Redirect/@Overwrite；只替换上限广告值，未触碰写入副作用（Colossal Chests 构造参数、GT 返回值三处均为容量来源/广告面）；注入均 @Pseudo + require=0（mod 缺失静默跳过，失败方向安全）；未改 ItemMixin/ItemStackMixin 既有逻辑。运行时行为（真实 MC 装载与应用）因 `run/mods` 无对应 mod 无法本批验证，只做源文本断言与编译产物字节码结构检查（SourceTest），如实记录。

### 3.12 转发 wrapper 探针预期修正（2026-08-09 执行）

按 §3.5 未决事项（wrapper 探针期望仍按旧语义 `getSlotLimit == compat`），修正 `src/main/kotlin/io/alexjoest/stackupup/dev/DevWrapperCompatProbes.kt` 两个转发 wrapper 探针的断言预期：

- 事实：`InvWrapper#getSlotLimit(I)I` 与 `SidedInvWrapper#getSlotLimit(I)I` 是转发 wrapper，运行期直接返回 delegate 的 `getInventoryStackLimit()`（§3.5 三分类"转发"，`ForgeItemHandlerLimitMixin` 已收敛移除注入；**历史动态 ASM 阶段**曾由 `FixedCompatTargets` 以 probeCovered=true 显式跳过）。探针代理库存 `createInventoryProxy`/`createSidedInventoryProxy` 的 `getInventoryStackLimit` 硬编码 64，当 `activeMaxStackSize`=10000 时旧预期（== compat）必然失败——该失败为预存问题（§8.6.2 记录，与 MixinBooter 迁移无关），本修正属探针预期而非生产容量逻辑。
- 变更：`verifySingleSlotLimit` 增加 `expected` 参数；`InvWrapperLimitProbe`/`SidedInvWrapperLimitProbe` 预期改为 `delegateInventoryStackLimit(inventory)`（反射读取代理库存实际广告的 `getInventoryStackLimit`，运行期 SRG 名 `func_70297_j_`，固定 64），断言语义改为 "wrapper 广告 == delegate 上限"。`CombinedInvWrapperLimitProbe`/`RangedWrapperLimitProbe`（ItemStackHandler 提升场景）与 `SlotItemHandlerLimitProbe`（handler 广告跟随）保持 `getCompatibilityStackSize()` 预期不变；**当时的历史动态层** `FixedCompatTargets` 与生产容量逻辑未改动，前者已随动态 ASM 删除。
- 验证（实际执行）：`runServerAutoTestMatrix` BUILD SUCCESSFUL（exit 0，外层 Exec 与子 Gradle `runServerAutoTest` 均通过，无 autotest-failed marker）：activeMaxStackSize=10000 下 5 个 wrapper 探针全绿——`combined_inv_wrapper_limit 槽位上限=[10000,10000]`、`inv_wrapper_limit 槽位上限=64`、`ranged_wrapper_limit 槽位上限=[10000,10000]`、`sided_inv_wrapper_limit 槽位上限=64`、`slot_item_handler_limit 槽位上限=10000 物品上限=10000`；未装 mod 探针按规则跳过、GT 矩阵跳过、探针结束自动停服。`./gradlew test` 全量 BUILD SUCCESSFUL（0 failures）；spotlessCheck 仅剩 `RuleFieldContextProvider.kt` 预存违规（租约外，未修），本任务改动文件无 spotless 违规。
- 剩余风险：inv/sided 探针只观测广告值、不覆盖写入路径（写入面判定仍按 §3.1/§3.5 源码事实）；`activeMaxStackSize`=64 时新预期与旧预期等价（64==64），行为不变，无回归面。
- **取代关系：** 本节结论取代 §8.8「验证结果」中 `runServerAutoTestMatrix` FAILED（`inv_wrapper_limit`/`sided_inv_wrapper_limit` 槽位上限=64 预期=10000）的记录——该记录为探针预期问题，已由本节修正并实测通过。此后最近一次矩阵运行（2026-08-10，`run/logs/autotest-report.txt` 最终状态 PASS）同样无该两项失败；2026-08-10 之后的代码改动未经矩阵重跑，故当前工作副本不因此节记为矩阵通过。
## 4. 当前限制

1. **历史动态 ASM 限制（已删除）**：删除提交 `9cff8e7` 前，动态 patch 基类可能传播到没有覆盖方法的第三方子类；静态目标表不能穷举第三方实现。该风险与 profile/probe/skip 表均保留作历史审计证据，当前不存在动态 patch 层。没有源码或可重复运行证据的条目仍统一为 **无源码不可判定**。
2. `ForgeItemHandlerLimitMixin` 已按 §3.5 收敛为两个自洽目标（ItemStackHandler、EntityEquipmentInvWrapper），四个转发 wrapper 与 `SlotItemHandler#getSlotStackLimit` 的独立注入已移除（T3 执行，2026-08-08）。
3. 当前 AE2 热路径已按方案 A（§3.8）改为 mixin 原样透传，`Ae2ItemHandlerInsertLimiter` 白名单保留为纯工具类（仅测试使用，§3.6 判定不变）；"不吞"由 remainder 契约结构性保证 + 边界探针验证，不再有守恒审计。AE2 的 `AdaptorItemHandler`、三个 pattern terminal 目标及其第三方内部写入链在本记录中没有对应第三方源码，统一记为 **无源码不可判定**，不能用项目自己的 mixin 名称补出结论。
4. `InventoryLargeChest` 的上限转发与按 index 写入存在结构性不一致风险；T11 不能仅保留现有 mixin 目标而跳过两半箱验证。
5. NuclearCraft、Tech Reborn、RebornCore 等第三方内部写入链不因方法名或历史注释自动获得分类。当前缺少对应版本源码时，记录为 **无源码不可判定**，不写成断链，也不允许据此新增 patch。NuclearCraft 的 1.12.2 源码已在本批次取得（§3.9 B），其 `ITileInventory` 基类链按源码事实收敛；Tech Reborn / RebornCore 仍无源码。
6. 本记录没有真实客户端 F3+T 的运行证据，因此真实资源重载基线当前视为 **尚未建立**。`LanguageMap.replaceWith()` 的源码只能证明清空/替换行为，不能填补 T8.0 的行为证据缺口。

## 5. 历史研究证据（不等于当前准入）

### 5.1 研究保留下来的风险

历史兼容问题集中在“广告面扩大、写入面没有同步扩大”：vanilla 可能把大堆叠投入只接受 64 的目标，发生截断、丢失或与模组自有溢出逻辑冲突。这个风险支持第 1 节的写入前容量一致原则，但不支持事后回填。固定目标使用显式 late mixin、完整方法 descriptor，并让已接管目标避开泛化 ASM，仍是可维护性较高的方向。它不能替代对真实写入源码的核验。

### 5.2 NuclearCraft

历史记录中列出的核验面包括：

- NuclearCraft `1.12.2o` 的 `nc.tile.internal.inventory.ItemHandler`：`insertItem`、`getStackSplitSize`、`getSlotLimit`；
- `nc.tile.inventory.ITileInventory#setInventorySlotContents`；
- 另一 `1.12.2` 路径中的 Forge `InvWrapper` / `SidedInvWrapper` 及其背后的 `IInventory`。

这些名称只能作为后续源码审查入口。没有对应第三方源码时，以上对象均为 **无源码不可判定**；不得把它们写成“必然截断”或“已自洽”。取得源码后，必须核对真实 setter、handler limit、插入 remainder 和版本差异。

### 5.3 Tech Reborn / RebornCore

历史记录中列出的核验面包括 RebornCore 的 `Inventory#setInventorySlotContents`、`getInventoryStackLimit`，以及 `InventoryItemHandler#insertItem`、`getSlotLimit`。Tech Reborn 1.12.x 的机器库存核心不能只按 TR 的 GUI 或 slot 类判断。当前没有足够的第三方源码证据时，相关目标统一为 **无源码不可判定**。后续若取得源码，先核对 RebornCore 的真实写入面，再决定是否建立窄范围 late mixin；不扩展成未知 handler 的通用规则。

### 5.4 缺失第三方 artifact 台账

以下条目在本记录中没有可读的对应第三方源码或 jar；括号内文件名/版本未登记，不能据此补猜具体实现。每项均为 **无源码不可判定**：

- late 目标 jar：`appliedenergistics2`（AE2）、`actuallyadditions`、`brandonscore`、`cyclopscore`、`enderio`、`ic2`、`mantle`、`refinedstorage`、`storagenetwork`、`integrateddynamics`、`limelib`、`immersiveengineering`；各自对应的 1.12.x jar（文件名/版本未登记）均缺失。
- 机器研究 jar：`nuclearcraft`（1.12.2 / 1.12.2o 对应 jar，文件名/版本未登记）、`techreborn`（1.12.x 对应 jar，文件名/版本未登记）和 `reborncore`（1.12.2 对应 jar，文件名/版本未登记）。

Forge 自身 wrapper 和 vanilla 反编译源码不属于上述缺失项；它们按第 3 节的 repo-relative 源码位置核验。

### 5.5 AE2 投喂路径

方案 A（§3.8）后，AE2 投喂热路径不再经过任何项目代码：`AppEngAdaptorItemHandlerMixin.java` 不包含任何注入（仅保留为 config 入口保险丝），`IItemHandler#insertItem` 调用原样执行，零分支、零分配。Forge `IItemHandler` 的正式契约在 `build/rfg/minecraft-src/java/net/minecraftforge/items/IItemHandler.java:61-75`：`insertItem` 接受 `simulate` 参数并返回未插入的 remainder——"不吞"由该契约结构性保证。

`src/main/java/io/alexjoest/stackupup/core/Ae2ItemHandlerInsertLimiter.java` 保留为纯工具类（仅测试护栏直接使用，不在热路径上）：对超出 cap 的 `simulate=false` 输入把输入预先拆成多个独立 chunk，每个 chunk 都是一次不超过 cap 的真实 `insertItem`，只有该 chunk 的 remainder 或零进度会停止后续 chunk。这是预先限幅而非写入后回填（§3.6 判定不变）；该循环已不处于任何热路径。

AE2 `AdaptorItemHandler`、三个 pattern terminal 目标及其包裹 handler 的第三方内部实现，在本记录中均为 **无源码不可判定**。项目自己的 `AppEngPatternTermMixin.java:15-29` 只证明它尝试提升空白 pattern 输入槽 `patternSlotIN`，不证明其他槽或 AE2 内部写入容量；"不吞"的运行期验证由边界探针（`DevAutomationServerDriver#probeBoundaryLimit`）承担。
## 6. 拟议任务与准入边界

### T3：Forge handler 目标收敛

**待决边界：**逐目标核对 `ItemStackHandler`、各转发 wrapper 和 `EntityEquipmentInvWrapper` 的完整查询—写入链。不得把 wrapper 当独立容量来源，不得使用 `@Accessor` 猜 delegate 容量，不得按旧结论把 EntityEquipment 写成“无余量必吞”。T3 明确禁止对 `SlotItemHandler` 独立动态扩容；不得仅凭 slot 查询值或 `original == 64` 抬高其广告。

**准入条件：**每个保留目标都要有真实 `simulate=false` 插入、写入前后状态和 remainder 守恒证据；EntityEquipment 必须分别覆盖 armor 与手部，并同时说明 Forge wrapper 插入路径和 vanilla setter 路径。无法闭合的目标保持安全收缩。

**执行状态（2026-08-08）：**判定与实现完成，见 §3.5。保留目标（ItemStackHandler、EntityEquipmentInvWrapper、SlotItemHandler#getItemStackLimit）的真实 `simulate=false` 守恒测试与转发目标（四个 wrapper、SlotItemHandler#getSlotStackLimit）的移除护栏在 `WrapperCapacityDiagnosticTest` 中落地；`DevWrapperCompatProbes` 的旧语义探针期望与 T2a 登记表 §2.3 行数刷新属租约外未决事项。

### T10：AE2 白名单重排

**待决边界：**白名单是静态、窄范围的调用方策略，不是全局 handler 规则；`isTrusted` 不能以类名作为理由。`EntityEquipmentInvWrapper` 不能直接移除并声称必吞，也不能直接保留并声称已安全，必须引用第 3.2 节的 Forge/vanilla 分界证据。

**准入条件：**白名单内外分别覆盖真实插入、模拟插入和 remainder；第三方源码缺失时写**无源码不可判定**并维持 64 安全上限。方案 A（§3.8）后白名单不再承担运行期职责，运行期"不吞"由 remainder 契约结构性保证 + 边界探针验证。

**执行状态（2026-08-08）：**判定与重排完成，见 §3.6。移出 InvWrapper、SidedInvWrapper（转发 wrapper，delegate 任意 IInventory/ISidedInventory，第三方写入面无源码）；保留 ItemStackHandler、VanillaDoubleChestItemHandler、EntityEquipmentInvWrapper、EmptyHandler；移入 0 项。白名单内外真实/模拟/remainder 测试落地于 `Ae2ItemHandlerInsertLimiterTest`（`Ae2ItemHandlerInsertLimiterConservationAuditTest` 已随方案 A 删除）；`WrapperCapacityDiagnosticTest` 的端到端守恒测试（租约外未改动）只断言守恒公式，移出后仍通过。

### T11：vanilla 目标表

**待决边界：**以源码穷举 `getInventoryStackLimit()` 实现者，改为编译期显式表，不能继续用运行时 `original == 64` 猜测 provenance。`ContainerEnchantment` 和 `TileEntityBeacon` 的未扩大行为要记录为功能限制或主动限制，不写成吞物 bug。

`InventoryLargeChest` 在两半箱真实来源一致前不得进入安全扩大表。必须用上下半箱上限不一致的测试证明风险；若回扩，对外查询值至少取 `min(upper, lower)`，不能只读取 `upper`。

**关联边界：**T4a 若调整 inventory-write 通道，必须保留 `resolveInventoryClampLimit` 的定义和两个 `InventoryPlayer` 调用点；不得用删除调用方来消除问题。

### T12：默认关闭的运行时守恒审计（历史方案，已归档）

**历史边界：**只包围项目主动发起的真实投喂点；不全局拦截第三方 handler，不改 handler 行为。`simulate=true` 不参与守恒统计、守恒判定或损失告警，但可在统一机器 schema 中保留 `simulate` 标记和原始输入/返回观测；不执行第二次真实写入。方案 A 已按 §3.8 归档，当前不生成 JSONL 守恒报告，也不把该审计层作为 T13 依赖。

**准入条件：**对 `simulate=false` 的真实事件至少记录 `schemaVersion`、调用点、handler 类名、slot、`simulate`、`offered`、`before`、`after`、`storedDelta`、`remainderCount` 和 `balanced`。`simulate=true` 若保留记录，统一机器 schema 可保留 `schemaVersion`、调用点、handler 类名、slot、`simulate` 及原始输入/返回观测，但不计算 `balanced`、不参与守恒告警。审计只读、只记录、只报告；不回填、不重试、不补偿。默认关闭时不得给生产热路径增加配置读取、日志构造或额外分配。

T12 只能提供运行时证据，不能把没有源码的第三方条目静态改写成安全或断链；其报告应回填 T2 登记表和本记录，而不是自动生成补丁。

### T13：覆盖面收缩与回扩

**当前前置条件：**T3、T10、T11 的静态/运行时证据与当前边界探针、真实 remainder 行为闭合后，才形成覆盖面矩阵；T12/T12.5 的 JSONL 审计已由方案 A 归档，不再作为 T13 依赖。没有足够写入证据时，不以功能缺口为理由回扩广告容量。

**回扩准入：**

- **EntityEquipment**：armor 槽的 1 上限和不可堆叠语义保持不变。手部槽只有在 Forge `insertItem` 的上限计算/remainder、vanilla 实体存储、所有实际投喂入口均通过真实守恒测试后，才可建立窄范围任务；仅证明 `getSlotLimit` 或 `setItemStackToSlot` 不足以回扩。
- **`InvWrapper` 包第三方 `IInventory`**：绝不重新 patch wrapper。只有 T12 观测到具体内层类，且拥有该版本源码或可重复守恒证据时，才可为内层类建立 late mixin；无源码条目保持 **无源码不可判定**并不回扩。
- **`InventoryLargeChest`**：只有上、下两半都能承载同一 advertised limit，并通过两半不一致测试，才可考虑回扩；对外查询值至少取 `min(upper, lower)`，且不得超过两半真实值；否则保持不 patch。

T13 的最终矩阵中的回扩处置状态只能是“已回扩”或“决定不回扩（附理由）”，必要时保留“等行为证据”；这不替代“自洽/转发/断链”三分类或独立证据状态“无源码不可判定”。T13 当前为 BLOCKED：矩阵仍有失败项，且第三方写入证据未闭合；本文不把任何未满足条件的项目写成已回扩。

**历史执行状态（2026-08-08）：**T2 回填部分闭合，见 §3.7（t2a 登记表 §9 配套）：T3/T10 收缩内容登记、T12.5 JSONL 事件逐条关联调用点、结论三态标注完成；无源码项保持 **无源码不可判定**。该 JSONL 回填属于历史证据，方案 A 已归档。当前 T13 状态以 §8.8 与发布前矩阵为准：BLOCKED，不能宣称矩阵闭合。

## 7. 发布前验证门

兼容实现任务必须把源码证据、目标完整性和行为验证分开记录：

- 对每个自洽目标验证 `storedDelta + remainderCount == offered`，且测试覆盖实际 patch 状态，不只验证 Forge 裸基线。
- 对每个第三方缺源码目标保留独立证据状态 **无源码不可判定**和缺失的版本/源码范围，不用类名补结论。
- 若任务启用写入观测报告，必须区分模拟与真实写入，并能回到调用点、handler 类名和 slot；历史 T12 JSONL 已由方案 A 归档，当前不生成该报告。
- 改动 coremod、mixin 或自动化参数时，执行项目规定的字节码护栏和服务端自动验收；未执行的命令不得写成通过。
- 本记录只收敛决策和证据，不因文档更新删除、修改或补偿生产实现。
## 8. Mixin 生态、注入器与借鉴仓库决策

本节只收敛当前 Mixin 生态版本边界、注入器选择和参考仓库的可迁移范围；上游资料只作来源与架构佐证，当前实现以 11.17/0.7.2/connector 的源码、产物和运行证据为准，不改变前述容量事实。

### 8.1 已决策：当前构建事实与历史/上游资料分层

- **当前构建事实：** `gradle/libs.versions.toml:20-23,29-31` 为 MixinBooter `11.17`、CleanMix `0.7.2`；`build.gradle.kts:49-66` 将 CleanMix 作为编译期 annotation processor 挂载。当前注册入口是 manifest `MixinConnector` 指向 `StackUpUpMixinConnector`，不是旧 loader。11.17/0.7.2 于 2026-09-15 由 11.13/0.7.1 成对升级，过程与验证见 §8.9。
- `src/main/kotlin/io/alexjoest/stackupup/bootstrap/StackUpUpMixinConnector.kt:12-37,41-69` 实现 `IMixinConnector`，执行 early/late 配置入队与决策：early 处理冲突，late 处理未知配置、mod presence 和 toggle；已删除的 `MixinConfigValidator` 双向配置校验不属于当前 connector。其公共无参 `class` 形态是当前装载约束。
- **历史基线：** MixinBooter 10.7、`IEarlyMixinLoader`/`ILateMixinLoader`、`MixinTweaker` 和外部 LlamaLad7 MixinExtras 0.5.0 只用于版本对照。旧事实不得覆盖当前 11.17/0.7.2/connector。
- **上游资料：** [MixinBooter 官方 README](https://github.com/CleanroomMC/MixinBooter) 说明 11.x 基于 CleanMix、旧 early/late 接口 deprecated，并支持 manifest `MixinConfigs`/`MixinConnector`；精确行为仍以当前 jar、构建产物和运行日志为准。
- 11.13 迁移、connector、CleanMix AP/refmap 和 provider 证据已记录于 §8.6.1、§8.8，11.17/0.7.2 成对升级见 §8.9；这些局部验证不能写成发布通过，剩下的缺口如实记在 `t14.7-发布前矩阵.md` 的已知限制清单里。

### 8.2 已决策：生态职责、来源边界与证据等级

- [MixinBooter 官方仓库](https://github.com/CleanroomMC/MixinBooter) 是 Forge 1.8–1.12.2 侧的 bridge/bootstrap、配置发现和兼容层来源；它不能替代 Mixin 核心、CleanMix 或 MixinExtras 的职责证明。
- [CleanMix 官方仓库](https://github.com/CleanroomMC/CleanMix) 是 Mixin 核心转换、classloading service、注入和 AP/refmap 来源；当前项目实际使用版本为 `0.7.2`，以配置、jar/POM 和运行证据为准。
- [SpongePowered/Mixin 官方仓库](https://github.com/SpongePowered/Mixin) 是上游注入器、Accessor/Shadow、AP 和混淆基础来源；不能把上游发行版本直接当作当前运行时版本。
- [CleanroomMC/MixinExtras 官方仓库](https://github.com/CleanroomMC/MixinExtras) 是当前 11.17 内嵌 fork 的来源边界；其 DeepWiki wiki 未索引，官方页面和实际 jar/POM 为一等证据。当前 provider 为 0.5.5，同包名无 relocation。
- `io.github.llamalad7` 0.5.0 与 `com.cleanroommc` 0.5.5 不自动二进制、bootstrap、service 或打包兼容；前者及 10.7 provider 只作历史对照。DeepWiki 只作架构入口，不能替代当前 jar、AP、refmap 或运行矩阵。

### 8.3 已决策：注入器按语义选择，不按数量评价

- `@Inject`、`@Redirect`、`@Shadow` 的数量不构成质量指标。`@Shadow` 是访问目标已有字段/方法的成员桥；只有 handler 确实需要目标私有状态或私有方法时才使用，不能为了“Shadow 太少”新增成员，也不能用 Shadow 值证明容量。
- 需要保留一次原调用并围绕它改变参数、结果或是否执行时，优先核对当前 provider 后使用 `@WrapOperation`；只修改已有表达式结果时优先 `@ModifyExpressionValue`；只修改完整方法返回值时优先 `@ModifyReturnValue`；单参数、多个参数、局部变量或 receiver 的局部语义分别评估 `@ModifyArg`、`@ModifyArgs`、`@ModifyVariable`、`@ModifyReceiver` 等 `Modify*` 工具。包装器必须保留并按契约调用原 `Operation`，可链式不等于可以吞掉或多次调用原操作。
- 新代码默认不新增 `@Redirect`。`@Redirect`、`@Inject(at = @At("HEAD"), cancellable = true)` 的完整控制流接管、`@Overwrite`、把字面量 `64` 当容量替换的 `@ModifyConstant`，以及用 `require=0` 掩盖目标失效，均属于高风险写法；只有完整目标证据，或明确可选目标、mod gate 和结构化缺失诊断时才可保留。
- 每个注入必须核对目标类、方法完整 descriptor、`@At` 的 owner/name/descriptor、ordinal/slice、handler 参数和静态性、`remap`/混淆边界、配置与生成 refmap、预期匹配数，以及两个或多个 Mixin 链式共存时的原操作调用次数和顺序。注入成功不等于容量广告与真实写入闭合。选择表和迁移规则见 `docs/agent/mixin-%E7%94%9F%E6%80%81%E4%B8%8E%E6%B3%A8%E5%85%A5%E6%9C%80%E4%BD%B3%E5%AE%9E%E8%B7%B5.md:151-182,211-227`。
### 8.4 当前限制与 UNKNOWN：高风险样例仅待 T14 审计

以下条目是审查入口，不是修复完成清单；在 T14 的正式范围、静态/字节码和行为证据闭合前，不得写成已迁移、安全或已通过：

- `src/main/java/io/alexjoest/stackupup/mixin/early/EntityItemMergeMixin.java:11-20` 使用 `@Redirect` 替换 `ItemStack#getMaxStackSize()I` 调用；需先闭合合并双方的真实上限、调用点 descriptor、原操作调用契约和数量/remainder 结果，再决定是否迁移到 `@WrapOperation`。
- `src/main/java/io/alexjoest/stackupup/mixin/early/NetHandlerPlayServerMixin.java:21-56` 使用 Shadow 配合 `HEAD + cancellable` 接管创造模式包处理的完整控制流；`src/main/java/io/alexjoest/stackupup/mixin/early/ItemStackNbtMixin.java:21-71` 在 `writeToNBT` 入口取消并重建 NBT。两者必须分别对照目标版本的线程、异常、取消、字段、tag/capability 和往返语义，不能以 Shadow 数量或 Count 变大证明正确。
- `src/main/java/io/alexjoest/stackupup/mixin/early/SlotItemHandlerMixin.java:16-39` 修改 slot 广告并读取 handler 查询；未知 `IItemHandler` 的 setter、`insertItem`、delegate 和持久化路径仍无由此补出的证据，不能因广告修改成功而动态扩容。
- `src/main/java/io/alexjoest/stackupup/mixin/late/ItemGridHandlerMixin.java:13-24` 使用 `require=0` 的 `@WrapOperation`，且当前 handler 不调用传入的原 operation；必须证明它是明确可选目标、补齐缺失诊断，并验证多个 wrapper 共存和 `Math.min` 替换语义。`require=0` 不能成为核心目标的静默成功门。
- `src/main/java/io/alexjoest/stackupup/mixin/late/EnderIOInventoryNoDropMixin.java`：抑制型 `@WrapOperation` + `require=0`（不调用 original，抑制 `ItemStack.shrink` / `Block.spawnAsEntity`）；由 CDR §3.10 正当化，属明确可选目标；另注意其 vanilla `@At` 在生产 SRG 混淆下不重映射会静默不命中（详见 §3.10 生产环境 refmap 风险），失败方向安全。
- `src/main/java/io/alexjoest/stackupup/mixin/late/NuclearCraftDistributorNoDropMixin.java`：`HEAD + cancellable` 完整控制流接管（`cullInventory` 恒 `setReturnValue(false)` / `dropOverflow` HEAD 取消）；由 CDR §3.10 正当化；无 vanilla 成员目标，生产映射不受影响。

### 8.5 已决策：参考仓库只借鉴结构，不借鉴未经闭合的容量行为

- **StackUp：** 只借鉴确定性顺序规则，以及旧协议编码尽量保持兼容的思路；扩展数量仍必须增加版本、能力、最大值和编码范围协商。StackUp 的全局 `Item#setMaxStackSize`、按 `BIPUSH 64` 命中的 ASM、整方法 splice 均不可照搬；其研究路径没有提供第三方 `insertItem` remainder/真实写入闭环证据，任何无 remainder 或无行为测试的做法也不可照搬为 StackUpUp 的容量证明或实现准入。证据入口见 `docs/agent/借鉴仓库与重构对照.md:61-103`，包括 `C:/dev/mc/_tmp/StackUp/src/main/java/pl/asie/stackup/script/ScriptHandler.java:38-69`、`C:/dev/mc/_tmp/StackUp/src/main/java/pl/asie/stackup/script/ScriptContext.java:137-173,192-208`、`C:/dev/mc/_tmp/StackUp/src/main/java/pl/asie/stackup/core/MaxStackConstantPatch.java:39-69`、`C:/dev/mc/_tmp/StackUp/src/main/java/pl/asie/stackup/core/StackUpTransformer.java:154-219` 和 packet splice 路径。
- **biggerstacks-Unofficial：** 只借鉴把 `RuleSet` 作为权威快照入口的结构方向、watcher、登录 handshake/运行期 sync 和按 mod 分层；不能把其独立 template override 当作已同步的权威快照，也不能照搬直接丢弃 `insertItem` remainder、slot 与 `ItemStack` 求值分叉、override 与 RuleSet 不同步、`require=0` 静默核心目标或对私有结构使用反射。其 Minecraft 1.21.1/NeoForge/Java 21 代码不能替代 Forge 1.12.2、Java 8、当前目标 jar 的源码、字节码和运行证据。证据入口见 `docs/agent/借鉴仓库与重构对照.md:105-153`，尤其 `:136-153` 的同步、remainder、require 和反射边界。
- 两个参考仓库的结构启示都不能改变本记录的容量不变量：广告值仍不得大于真实写入能力；真实 `insertItem` 必须保留 remainder 并接受守恒审计；没有 Forge 1.12.2 第三方写入源码或可重复行为证据的目标仍是 **无源码不可判定**。
### 8.6 历史迁移决策与实际结果

- **历史决策：** T9-R1/T9-R3 将 MixinBooter 11 设为计划内必选迁移。当时还打算迁完再走一道发布前矩阵复核，那份复核的产出就是现在的 `t14.7-发布前矩阵.md`——一份已查证据和已知限制的记录，不卡任何后续任务。当时的 10.7/旧 loader 记录只代表迁移前基线。
- **当前结果：** 11.13 曾进入构建并把 CleanMix `0.7.1` 作为编译期 AP，该状态已由 §8.9 的 11.17/`0.7.2` 成对升级取代；旧 `IEarlyMixinLoader`/`ILateMixinLoader` 注册路径已由 `IMixinConnector` 取代。迁移的 refmap/AP、dev connector 装载和服务端结果见 §8.6.1、§8.8；`t14.7-发布前矩阵.md` 里还留着没查清的限制项。
- 任何生产混淆、第三方写入、高风险 Mixin 或未独立复核项目仍只能写 `UNKNOWN`/`无源码不可判定`，不得以 MixinBooter 已装载推出发布准入通过。
- **T14 状态：** [重构任务清单](%E9%87%8D%E6%9E%84%E4%BB%BB%E5%8A%A1%E6%B8%85%E5%8D%95.md)（T14 只在「T9/T14 版本与 Mixin」小节和「依赖 DAG」里出现，清单没给 T14 起过正式名）；T14.0–T14.7 的范围与先后关系以该清单当前内容为准（收口版只剩状态表和 DAG，不再按节号引用）。T14.1–T14.6 是当时查过留下的审计参考材料，不是待验收任务；T14.7 就是 `t14.7-发布前矩阵.md` 那份已知限制清单，不是准入门，里面仍有未闭合项。11.13→11.17 迁移已实施（当前版本 11.17/0.7.2，见 §8.9），但实施记录不等于发布通过。缺 jar、未运行生命周期验证或其他关键证据缺失时继续保留 **无源码不可判定**或 `UNKNOWN`，不得写成通过。T14 的对照记录仍保留于 [`借鉴仓库与重构对照.md`](%E5%80%9F%E9%89%B4%E4%BB%93%E5%BA%93%E4%B8%8E%E9%87%8D%E6%9E%84%E5%AF%B9%E7%85%A7.md)「5.1 T14：Mixin 生态与注入重构」及其子节，仅作历史/对照证据，不替代清单里的当前范围。
- 关联资料：[`Mixin 生态与注入最佳实践`](mixin-%E7%94%9F%E6%80%81%E4%B8%8E%E6%B3%A8%E5%85%A5%E6%9C%80%E4%BD%B3%E5%AE%9E%E8%B7%B5.md)、[`借鉴仓库与重构对照`](%E5%80%9F%E9%89%B4%E4%BB%93%E5%BA%93%E4%B8%8E%E9%87%8D%E6%9E%84%E5%AF%B9%E7%85%A7.md)、[`重构任务清单`](%E9%87%8D%E6%9E%84%E4%BB%BB%E5%8A%A1%E6%B8%85%E5%8D%95.md)（T9 MixinBooter 11 迁移取证、T9-R1/R3 版本与路径均见其状态表与「依赖与历史（压缩）」）、[MixinBooter 官方仓库](https://github.com/CleanroomMC/MixinBooter)、[CleanMix 官方仓库](https://github.com/CleanroomMC/CleanMix)、[SpongePowered/Mixin](https://github.com/SpongePowered/Mixin)、[LlamaLad7/MixinExtras](https://github.com/LlamaLad7/MixinExtras) 和 [CleanroomMC/MixinExtras](https://github.com/CleanroomMC/MixinExtras)。

- **T9-R3 历史迁移路径决策（2026-08-08）：** 本段保留迁移前的顺序、触发条件和回滚边界；实际实施结果见 §8.6.1 与 §8.8。10.7/旧 loader 是迁移前基线，11.13/0.7.1 是当时迁移目标，11.17/0.7.2/`IMixinConnector` 是当前事实（见 §8.9）。
- **历史目标版本与坐标：** 迁移目标为 `zone.rong:mixinbooter:11.13`（2026-08-05 发布），坐标不变。11.13 POM（https://maven.cleanroommc.com/zone/rong/mixinbooter/11.13/mixinbooter-11.13.pom）声明 `com.cleanroommc:cleanmix:0.7.1`、`com.cleanroommc:mixinextras-common:0.5.5`、`gson:2.8.0`、`guava:21.0`、`asm-debug-all:5.2`，无 relocation；官方 README（https://raw.githubusercontent.com/CleanroomMC/MixinBooter/master/README.md）说明 11.0 起基于 CleanMix、11.12 起使用 Cleanroom MixinExtras fork，且旧 early/late loader 已 deprecated。README 中的「CleanMix 0.6.0」与 11.13 POM 的 0.7.1 不一致，以 POM 为准。
- **历史注册路径方案（已被当前实施取代）：** 原方案拟保留 `IEarlyMixinLoader`/`ILateMixinLoader`，不迁移 manifest；该方案基于当时对 11.x 接口兼容性的判断。实际结果是 `StackUpUpMixinConnector` 普通 class 通过 manifest `MixinConnector` 注册，`StackUpUpCore` 不再实现 early loader，旧 `StackUpUpLateMixinLoader` 已删除（详见 §8.8）。旧接口与 `Context` 的 deprecated 证据仍作为历史对照保留，不代表当前注册路径。
- **历史迁移顺序：** ① 将 `gradle/libs.versions.toml:20` 的 MixinBooter 版本从 10.7 升至 11.13；② 删除外部 MixinExtras 0.5.0 声明，改由 11.13 内嵌 fork 提供同包名 API；③ 编译、测试、spotless 与服务端矩阵验证；④ 复查一遍刚查到的证据和限制（即后来的 `t14.7-发布前矩阵.md`）。实际执行另发现 refmap AP 回归，并已通过 `annotationProcessor(libs.cleanmix)` 修复，最终证据见 §8.6.1。
- **历史回滚边界：** 版本值和外部 MixinExtras 声明原可分别回退；此处仅记录当时的 jj 工作区回滚边界，不构成当前操作建议，也未执行任何回滚或清理。
- **历史触发与停止条件：** 原计划本想在迁移前先走完矩阵复核，并在 provider、注册、refmap、classloader、容量证据或生命周期验证未闭合时停下（后来的做法是：先按授权实施，缺什么记什么）。迁移已按授权实施，但 refmap 缺失确实命中过一次停止条件，随后完成 AP 修复；矩阵清单里没查清的项照旧留着，所以当前不得写成发布通过。
- **历史 UNKNOWN 账本的当前收口：** CleanMix `minVersion: "0.8"` 兼容性与 11.13 MixinExtras provider 已由 §8.6.1 的启动日志和编译证据闭环；生产混淆 manifest 行为、`CoremodClassFilter` 对 CleanMix/MixinExtras 类包的影响、真实生产 notch 环境及 ASM 版本实际装载仍为 UNKNOWN，不能由 dev/SRG 运行外推。
- **DeepWiki 与来源访问失败记录：** 本会话未注入 DeepWiki MCP 工具，降级为 DeepWiki 网站页面与官方 GitHub。`https://deepwiki.com/CleanroomMC/MixinBooter` 页面版本表止于 10.0，不能作为 11.x 注册事实；`https://deepwiki.com/CleanroomMC/CleanMix` 首次提取无文本、重试成功。GitHub API tree 请求 403 rate limit，降级 raw 文件直取成功。上述来源仅作架构/职责佐证，当时版本与运行结论以 11.13/0.7.1 的 POM、产物和 §8.6.1 实测为准；当前版本 11.17/0.7.2 见 §8.9。

#### 8.6.1 迁移实施记录（2026-08-09，历史中间阶段；T9-R3 授权的独立迁移实现任务实际执行）

本段是 MixinBooter 11 版本/AP 迁移的历史中间阶段记录（2026-08-09 05:37–05:48 实测），当时仍以旧 loader 验证版本升级，随后由 §8.8 迁移到 `IMixinConnector`。只记录当时执行结果与新发现，不改变 8.6 的决策、回滚边界与停止条件；实施任务不自宣通过，也没人拿 T14.7 当准入门卡它。

- **执行环境：** 默认 JDK 21（Temurin 21.0.12）加载 RFG 2.0.2 `UserDevPlugin` 失败——`UnsupportedClassVersionError`（class file 69.0 > 65.0），按任务兜底全部命令以 `JAVA_HOME=/c/Users/25761/.jdks/jbr-25.0.4 ./gradlew` 执行。
- **① 版本升级：** `gradle/libs.versions.toml:20` `mixinbooter = "10.7"` → `"11.13"`（唯一版本值处；`build.gradle.kts:53` 字符串插值不变）。**② 删除外部 mixinextras：** 按任务注记先保留外部声明以 11.13 编译通过（隔离版本变量），再删除 `build.gradle.kts` 对外部 MixinExtras 别名的 `compileOnly`/`annotationProcessor` 两行与 `gradle/libs.versions.toml` 的对应版本与库条目（该别名现已不存在），重编译通过——25 个 mixin 文件（实测 `rg -l "com.llamalad7.mixinextras" src/main/java`，非交接估算的 17）的 `com.llamalad7.mixinextras.*` import 由 11.13 内嵌 Cleanroom fork 0.5.5（同包名）提供，编译期注解解析成功，无需恢复声明。执行中一次编辑失误误删了 asmDebugAll/guava/gson/mixin AP 行，已当场纠正恢复，最终 diff 仅删除目标两行。
- **③ 编译验证（均 PASS）：** `compileKotlin`/`compileJava` 通过；`test` 全量 + `spotlessCheck` 通过（BUILD SUCCESSFUL）。deprecation 警告清单（无 `-Werror`，不阻断构建）：主源码 6 处——`StackUpUpCore.kt:7:8`、`:16:5` 的 `IEarlyMixinLoader`；`StackUpUpLateMixinLoader.kt:7:8`、`:25:50` 的 `Context`，`:8:8`、`:10:34` 的 `ILateMixinLoader`；测试源码 15 处——`MixinBooterIntegrationTest.kt:89,90,92,95,101,103,104,108,116,125,130,136,141,147,152` 的 `Context(String, Collection<String>)` 构造器。与 8.6 预测一致：两个 loader 代码零改动，仅产生 deprecation 警告。
- **④ 运行验证（均 PASS）：** 三护栏 `test --tests "*MixinBooterIntegrationTest" --tests "*EarlyMixinBytecodeSafetyTest" --tests "*CoremodHierarchyBytecodeSafetyTest"` 通过（4/2/1 用例，0 skipped、0 failures）。`runServerAutoTestMatrix` 通过（exit 0，38s）：服务端以 11.13 完整启动（8 mods 装载）；`[MixinBooter]: Loading early loader io.alexjoest.stackupup.StackUpUpCore` + `Adding [mixins.stackupup.early.json]`（early 路径按 10.7 语义工作）；`[MixinBooter]: Loading late loader [io.alexjoest.stackupup.bootstrap.StackUpUpLateMixinLoader]`（12 个目标 mod 未装的 late 配置按既有规则跳过）；`[MixinBooter]: Initializing MixinExtras...` + `[MixinExtras|Service]: ... version=0.5.5`（Cleanroom fork 由 MixinBooter 自动初始化，UNKNOWN ② 闭环）；`[CleanMix]: SpongePowered MIXIN Subsystem Version=0.8.7 ... Service=MixinBooter`（minVersion "0.8" 满足，UNKNOWN ① 闭环）；兼容探针 5 项通过（combined_inv_wrapper/inv_wrapper/ranged_wrapper/sided_inv_wrapper/slot_item_handler_limit），GT 矩阵与未装 mod 探针按规则 skip；无 mixin 注入失败或类加载错误；探针结束自动停服。新观察：`FML.ModTracker` 提示 dev 存档 mixinbooter 10.7→11.13（存档版本提示，非失败）；CleanMix 从 11.13 jar 成功初始化 Fernflower（10.7 运行时因缺 `org/jetbrains/java/decompiler` 类失败），`.mixin.out` 反编译导出可用。
- **新发现（refmap 生成回归，11.x 首次实跑）：** 迁移后 `compileJava` 报 `以下选项未被任何处理程序识别: '[outRefMapFile, reobfSrgFile, outSrgFile]'`；`build/tmp/mixins/` 为空；`build/resources/main/mixins.StackUpUp.refmap.json` 被 processResources 同步删除（无新文件可拷）；`build/libs/*.jar` 不含 refmap；运行日志 `[CleanMix]: Reference map 'mixins.StackUpUp.refmap.json' for mixins.stackupup.early.json could not be read`（dev 可忽略，生产混淆环境不可忽略；10.7 基线日志无此警告且含 `Remapping refMap ... using remapper chain`）。根因（本机缓存与官方 Maven 实测）：11.13 jar `META-INF/services/javax.annotation.processing.Processor` 仅注册 `com.llamalad7.mixinextras.ap.MixinExtrasAP`，jar 内无 `org/spongepowered/tools/obfuscation/MixinObfuscationProcessor{Injection,Targets}`（仅 `tools/agent/*`）；10.7 jar 同一 service 注册 3 个处理器（含 2 个 obfuscation AP，即 refmap 生成器）；11.13 POM 声明的独立 `com.cleanroommc:cleanmix:0.7.1`（官方 Maven 实测下载，非仅凭 build.gradle 分析）含 obfuscation AP 类与同名 service 注册，但本项目 `api(mixin)`/`annotationProcessor(mixin)` 均为 `isTransitive=false` 且 11.13 fat jar 未内嵌 AP 类 → AP 链只剩 MixinExtrasAP。该回归由版本升级本身引起（第一步仅升版本、仍保留外部 mixinextras 0.5.0 时即出现同样警告），恢复外部 mixinextras 不能修复（其 AP 同为 MixinExtrasAP）。影响：13 个 mixin 配置均引用 `"refmap": "mixins.StackUpUp.refmap.json"`，生产混淆 jar 缺 SRG→混淆名映射；dev（SRG 命名）不受影响，本次运行验证全部通过即证明。修复假设（**未实施**，红线「不新增依赖」）：将 `com.cleanroommc:cleanmix:0.7.1` 以 annotationProcessor（仅编译期）加入恢复 obfuscation AP，或调整 isTransitive 策略；属协调者/后续任务决策。停止条件对照：8.6「refmap 证据未闭合」停止条件命中，本实施任务按用户授权继续执行并如实记录，不自行宣布迁移验收。
- **UNKNOWN 账本更新：** ① 已闭环（见 ④）；② 已闭环（见 ④）；③ `required-after:mixinbooter@[10.0,)` 服务端装载满足（8 mods 装载成功），生产 manifest（`MixinConfigs` 注册模型）行为仍 UNKNOWN（属后续可选清理项）；④ `CoremodClassFilter.java` 跳过前缀是否需扩展至 `com/llamalad7/` 未实测，本次运行无相关错误 → 保持 UNKNOWN；8.7 新增 ⑤ 无 `TweakClass` 启动路径：已实证替代路径生效（`Service=MixinBooter` 的 CleanMix 平台服务完成装载）；8.7 新增 ⑥ 运行时 ASM 5.2/9.8 并存：dev 运行正常但转换链实际使用的 ASM 套数未审计 → UNKNOWN。第三方容量写入缺口维持既有台账不变。
- **验证清单与剩余风险：** 全部命令及结果见上（含一次 JDK 21 失败及原因）；未执行：`runClient`（任务只要求服务端矩阵）、late mixin 真实第三方模组装载（本地无对应 mod，仅验证排队/跳过逻辑）、独立复核（当时协调者说要指派，没做）。剩余风险：refmap 生成回归（生产混淆 jar 正确性，修复方案待协调者决策，本任务未绕过）；客户端路径 11.13 行为未跑；迁移现场（build 产物、日志）按现状保留，未清理、未回滚。
- **refmap 修复实施记录（2026-08-09 追加，按 8.6.1 修复假设方案 a 实施并验证）：** 修复方式——`gradle/libs.versions.toml` 新增 `cleanmix = "0.7.1"` 版本与库条目；`build.gradle.kts` useMixins 块新增 `annotationProcessor(libs.cleanmix)`（仅编译期 AP，不进 implementation/runtime）。方案对照：ForgeDevEnv 模板 `build.gradle:138-144`（11.13）为 `annotationProcessor mixin`（transitive 默认，AP 路径经传递解析自然含 cleanmix 0.7.1）；本项目显式声明 cleanmix 等价，且避免了方案 b（`api(mixin)` 改 `isTransitive=true` 会把 cleanmix/mixinextras-common/gson 2.8.0/guava 21.0 带入编译与运行时 classpath）。证据：mixinbooter-11.13 POM（本地缓存 `e304bc6ac61a…/mixinbooter-11.13.pom`）compile scope 声明 `com.cleanroommc:cleanmix:0.7.1`；cleanmix-0.7.1.jar（maven.cleanroommc.com 实测下载）`META-INF/services/javax.annotation.processing.Processor` 注册 `MixinObfuscationProcessorInjection`/`MixinObfuscationProcessorTargets`（refmap 生成器，10.7 jar 同款）；其 POM 依赖全为 runtime scope（guava 21.0、gson 2.2.4、asm-tree/commons/util 9.8）→ 纯编译期 AP。编译验证：`JAVA_HOME=/c/Users/25761/.jdks/jbr-25.0.4 ./gradlew --no-daemon compileJava` 通过，javac 注记 `SpongePowered MIXIN Annotation Processor Version=0.8.7` + `Writing refmap to build/tmp/mixins/mixins.StackUpUp.refmap.json`，8.6.1 的「以下选项未被任何处理程序识别: [outRefMapFile, reobfSrgFile, outSrgFile]」警告消失（cleanmix AP 识别 RFG 传入的 outRefMapFile 选项，证明 11.x 仍沿用 outRefMapFile 机制）；产物 `build/tmp/mixins/mixins.StackUpUp.refmap.json`（11906 B）含 SRG 映射（`CommandGiveMixin` `execute → Lnet/minecraft/command/CommandGive;func_184881_a(...)V`），`processResources` 后 `build/resources/main/mixins.StackUpUp.refmap.json` 与 tmp 逐字节一致（cmp IDENTICAL，10.7 基线同款双份布局）。依赖边界验证：`dependencies --configuration annotationProcessor` 含 `com.cleanroommc:cleanmix:0.7.1`（guava 21.0→24.1.1-jre、gson 2.2.4→2.8.6 版本升级合并，asm 9.8 tree/commons/util 随行）；`dependencies --configuration runtimeClasspath` 无 cleanmix → 红线「cleanmix 只进 annotationProcessor、不进 implementation/runtime」满足。运行验证：`runServerAutoTestMatrix` 通过（BUILD SUCCESSFUL，44s），`run/logs/debug.log:272` `[CleanMix]: Remapping refMap mixins.StackUpUp.refmap.json using remapper chain`（恢复 10.7 基线行为，8.6.1 的 `could not be read` 警告消失）。全量验证：`test`（BUILD SUCCESSFUL，31s）+ 三护栏 `CoremodHierarchyBytecodeSafetyTest`/`EarlyMixinBytecodeSafetyTest`/`MixinBooterIntegrationTest` + `spotlessCheck`（BUILD SUCCESSFUL，52s）全通过；`runClient` 未执行（任务只要求服务端矩阵）。剩余风险：真实生产混淆（notch 命名）环境的 refmap 消费未实测（dev/SRG 命名证据已覆盖 func_ 映射与运行装载）；AP 路径上 asm-debug-all 5.2 与 cleanmix 传递 asm 9.8 并存，本次编译无异常，AP 实际装载的 ASM 套数未单独审计（维持 8.7 UNKNOWN ⑥ 的运行时口径）。迁移与修复现场（build 产物、日志）按现状保留，未清理、未回滚；本记录不自行宣布迁移验收，剩下的限制项照旧列在 `t14.7-发布前矩阵.md` 里。
### 8.7 T14.0 来源与版本账本复核（2026-08-08 追加，历史快照）

本节是 T14.0「来源与版本账本复核」在迁移实施前的窄复核产出：只交叉核对当时的 T9 账本、构建配置、本地产物、DeepWiki/官方来源，不改依赖、不改 build 文件、不改 Mixin。它保留 10.7 与旧 loader 的历史证据，不代表当前构建；此处记录的迁移目标 11.13/0.7.1/`IMixinConnector` 及后续收口见 §8.6.1、§8.8，当前版本已由 §8.9 升为 11.17/0.7.2。复核方式为 DeepWiki MCP（https://mcp.deepwiki.com/mcp，HTTP JSON-RPC/SSE）、官方 Maven（maven.cleanroommc.com / Maven Central）、官方 GitHub（页面/raw，GitHub REST API 被限流）与本机 gradle 缓存、build/ 产物交叉核对。

**T9 账本历史复核结论：** T9-R1/T9-R3 的版本决定、坐标、发布事实、回滚边界与当时的 4 项 UNKNOWN 与本复核逐条一致；以下仅保留当时的证据精度与 2 项新 UNKNOWN。迁移前构建触点复核：`gradle/libs.versions.toml:20` `mixinbooter = "10.7"`、`:21` 外部 MixinExtras 版本 `"0.5.0"`、`:29` `io.github.llamalad7:mixinextras-common`；`build.gradle.kts:53-54` `modUtils.enableMixins("zone.rong:mixinbooter:${...}")` 字符串插值、`:57-58` 对外部 MixinExtras 的 `compileOnly`/`annotationProcessor` 别名声明、`:59-62` AP 追加 `asmDebugAll`/`guava`/`gson`/mixin（与 10.7 tag 上游 AP 集合一致）。这些触点已由当前实现更新，勿据此判断现状。

**迁移前本地产物历史账本（10.7 基线，全部实际核对）：**

- `zone.rong:mixinbooter:10.7` 位于 `~/.gradle/caches/modules-2/files-2.1/zone.rong/mixinbooter/10.7/`：jar（`3fdfffe4…/mixinbooter-10.7.jar`，5564093 B，1665 条目）、sources jar（`3b37a075…`）、POM（`7ec886c0…`，**无任何依赖声明**，运行时内容全部内嵌）。MANIFEST.MF 全字段：`TweakClass: org.spongepowered.asm.launch.MixinTweaker`、`FMLCorePlugin: zone.rong.mixinbooter.MixinBooterPlugin`、`FMLCorePluginContainsFMLMod: true`、`ForceLoadAsMod: false`、`Premain-Class`/`Agent-Class: org.spongepowered.tools.agent.MixinAgent`、`Can-Redefine-Classes`/`Can-Retransform-Classes: true`。jar 内容统计：`org/spongepowered/` 1064 类（嵌入 UniMix/Sponge Mixin 核心）、`com/llamalad7/mixinextras/` 类（**内嵌 LlamaLad7 MixinExtras 0.5.0**，含 `MixinExtrasBootstrap`、`ap/MixinExtrasAP`）、`org/objectweb/asm/`、`zone/rong/mixinbooter/` 26 类（含 `fix/mixinextras/MixinExtrasFixer`）。`META-INF/services/`：`javax.annotation.processing.Processor` → `com.llamalad7.mixinextras.ap.MixinExtrasAP` + `org.spongepowered.tools.obfuscation.MixinObfuscationProcessorInjection/Targets`；`IMixinService` → `org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapper`；`IMixinServiceBootstrap` → `…LaunchWrapperBootstrap`；另有 `IGlobalPropertyService`、`IObfuscationService`。条目时间戳：sponge 类 2025-01-08、mixinextras 类 2025-07-27、jar 打包 2025-09-30。与上游 10.7 tag build.gradle（https://raw.githubusercontent.com/CleanroomMC/MixinBooter/10.7/build.gradle，`enableMixins('com.github.CleanroomMC:UniMix:9d4b487ed3')`、`embed 'io.github.llamalad7:mixinextras-common:0.5.0'`）一致：这是迁移前按该 tag 构建的内嵌形态，不代表当前 jar。
- `io.github.llamalad7:mixinextras-common:0.5.0` 位于 `~/.gradle/caches/modules-2/files-2.1/io.github.llamalad7/mixinextras-common/0.5.0/`：jar（`286da960…`）、sources jar（`8db1dbef…`）、POM（`e6bfe617…`，MIT，无依赖）。jar 的 MANIFEST 仅 `Manifest-Version: 1.0`。Maven Central metadata（https://repo1.maven.org/maven2/io/github/llamalad7/mixinextras-common/maven-metadata.xml）版本 0.4.0→0.5.4，`0.5.0` 存在，最新 0.5.4（lastUpdated 2026-04-15）。
- `build/` 产物：`build/resources/main/mixins.StackUpUp.refmap.json` 与 `build/tmp/mixins/mixins.StackUpUp.refmap.json` **逐字节一致**（refmap 由迁移前 AP 链生成，含 SRG `func_` 映射，如 `CommandGiveMixin` 的 `execute → func_184881_a`）；`build/resources/main/` 下 20 个 mixin 配置 JSON（early 1 + late 19）。AP 链（编译期）为迁移前 `build.gradle.kts:57-62` 的 mixinextras 0.5.0 + asm-debug-all 5.2 + guava + gson + mixinbooter 内嵌 AP。
- **候选版本不在当时本地缓存：** `~/.gradle/caches/modules-2/files-2.1/` 下无 `com.cleanroommc`（cleanmix 0.7.1、mixinextras-common 0.5.5），无 `mixinbooter/11.13`；11.13 候选 jar 仅当时在线下载核对（见下），不构成当时本地运行证据。
**10.7 vs 11.13 历史对照表（版本/坐标/职责/early-late/manifest/CleanMix/Extras/Sponge）：**

| 维度 | 迁移前 10.7（历史本地产物） | 迁移目标 11.13（官方 Maven 产物，2026-08-05 发布） |
|---|---|---|
| 坐标 | `zone.rong:mixinbooter:10.7` | `zone.rong:mixinbooter:11.13`（坐标不变；metadata lastUpdated 20260805224202，GitHub releases 页 11.13 = Aug 5 22:42，与 T9-R3 一致） |
| 职责 | Forge 1.12.2 bridge/bootstrap + 配置发现 + 兼容修复（UniMix 0.15.3 内嵌） | 同职责，改基于 CleanMix 0.7.1（11.13 POM：`com.cleanroommc:cleanmix:0.7.1`、`com.cleanroommc:mixinextras-common:0.5.5`、`gson:2.8.0`、`guava:21.0`、`asm-debug-all:5.2`，无 relocation；cleanmix metadata 0.2.6→0.7.1，lastUpdated 2026-08-05 22:24，与 11.13 同批次发布） |
| early/late | `IEarlyMixinLoader`/`ILateMixinLoader` + `Context`（项目使用点见 8.6） | 接口仍存在但 `@Deprecated`（main 分支源码已核实，见 8.6）；DeepWiki 索引声称"未弃用"为陈旧索引，不作 11.x 证据 |
| manifest | `TweakClass`（MixinTweaker）+ `FMLCorePlugin` + `FMLCorePluginContainsFMLMod: true` + `ForceLoadAsMod` + Premain/Agent | 11.13 jar manifest 实测：`FMLCorePlugin: zone.rong.mixinbooter.MixinBooterPlugin` + `Premain/Agent` + `Can-Redefine/Retransform` + **`MixinConfigs: mixin.mixinbooter.init.json`**；**无 `TweakClass`**、**无 `FMLCorePluginContainsFMLMod`** |
| MixinConnector | 10.7 历史基线无项目 connector，未扫描 manifest connector | 项目当前通过 manifest `MixinConnector` 注册 `StackUpUpMixinConnector`；CleanMix 的 `MixinPlatformAgentDefault`/`MixinConnectorManager` 扫描与 dev 装载见 §8.8 |
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

**历史 UNKNOWN 账本逐条（2026-08-08 快照；11.13/0.7.1 中期收口见 §8.6.1、§8.8；当前版本为 11.17/0.7.2，升级与验证见 §8.9）：**

- ① **CleanMix 0.7.1 对 `minVersion: "0.8"` 配置的兼容性：已闭环。** §8.6.1 的 11.13 服务端日志显示配置成功装载且无 minVersion 错误；编译期 Mixin AP 也正常运行。原缺口和停止条件仅保留为历史记录。
- ② **11.13 MixinExtras provider 初始化：已闭环。** §8.6.1 记录 `[MixinBooter]: Initializing MixinExtras`、Cleanroom fork `0.5.5` 版本日志及 `com.llamalad7.mixinextras.*` 编译解析成功。原缺口仅保留为迁移前账本。
- ③ **manifest 与 `required-after`：dev/SRG 路径已闭环，生产 notch 路径仍 UNKNOWN。** 当前 manifest 已注册 `MixinConnector`，`required-after:mixinbooter@[10.0,)` 的 11.13 服务端装载已验证；生产混淆 jar 的完整 FML/coremod/connector 装载仍未实测。
- ④ **`CoremodClassFilter.java:17-24` 对 `com/llamalad7/` 的跳过前缀：仍 UNKNOWN。** 当前跳过表事实与 11.13 类分布仍按历史复核记录；没有独立字节码/启动证据证明该包是否进入 coremod 早期链，不能写成无影响。
- ⑤ **11.13 无 `TweakClass` 的启动路径：dev 服务端已闭环，客户端和生产路径未闭环。** §8.6.1/§8.8 的 CleanMix `Service=MixinBooter`、connector 成功装载和服务端矩阵证明当前 dev 替代路径可用；`runClient` 与生产 notch 环境仍未执行。
- ⑥ **11.13 运行时 ASM 版本并存：仍 UNKNOWN。** 11.13/cleanmix-0.7.1 不内嵌 ASM，POM 分别带 asm-debug-all 5.2 与 asm 9.8；本次编译和 dev 服务端运行无异常，但转换链实际选用的 ASM 套数未单独审计。11.17/0.7.2 沿用同一形态（POM 仍带 asm-debug-all 5.2，cleanmix 0.7.2 带 asm 9.x），升级后的实测见 §8.9。

**无源码不可判定与迁移行为边界：** 当前 11.17/cleanmix-0.7.2/Cleanroom MixinExtras 0.5.5 已有 dev/SRG 编译和服务端加载证据（§8.9），11.13/0.7.1 时期的证据见 §8.6.1、§8.8；但第三方容量写入路径、生产混淆环境的 connector/refmap 消费及高风险 Mixin 行为仍按既有证据缺口处理，第三方 jar 缺源码处为**无源码不可判定**。`t14.7-发布前矩阵.md` 里还留着没查清的限制项，所以不能把迁移实施记录写成发布通过。第三方容量写入目标缺口维持既有台账（`compatibility-decision-record.md:156-163`、`mixin-生态与注入最佳实践.md:97-101`），本复核不重列。DeepWiki 全部查询只作架构/职责佐证，不作版本化运行事实；GitHub API 限流与 DeepWiki 未索引均已按上表逐条记录，未把失败项写成通过。
### 8.8 IMixinConnector 装载入口迁移（2026-08-10 追加）

本节记录把 mixin 装载入口从 deprecated 的 `IEarlyMixinLoader`/`ILateMixinLoader` 迁移到 `IMixinConnector`（MixinBooter 11 官方路径）的真实实施记录。只换装载入口；下列 validator/旧 loader 校验描述属于当时的中间版本，后续已删除或由 connector 的现行决策函数替换，不能当作当前实现。

- **实施变更（逐文件）：**
  - 新建 `src/main/kotlin/io/alexjoest/stackupup/bootstrap/StackUpUpMixinConnector.kt`：`class StackUpUpMixinConnector : IMixinConnector`。**当时中间版本记录**：`connect()` 曾在 early `Mixins.addConfiguration` 前调用 `MixinConfigValidator.requireCoreConfigValid`，late 曾调用 `validateConfigs`/`logProblems`；这些自定义 validator 调用及其双向校验后来由提交 `30a5976` 删除。**当前事实**：early 由 `connectEarly` 依据 `StackUpUpCore.ensureConflictState()` 和 `shouldQueueEarly` 决定是否 `Mixins.addConfiguration`（当前文件 `:46-55`、`:179-190`）；late 由 `connectLate` 遍历当前 `modules`，`shouldQueue` 对未知配置、mod 缺失和 toggle 关闭分别作决策/INFO 或 ERROR 记录（`:58-67`、`:196-215`），不保留 validator。
  - `src/main/kotlin/io/alexjoest/stackupup/StackUpUpCore.kt`：移除 `IEarlyMixinLoader` 实现与 `getMixinConfigs()`；`ensureConflictState()` 由 private 改 internal（connector 与 `getASMTransformerClass` 共用同一冲突判定）；IFMLLoadingPlugin 其余（`getASMTransformerClass`/`injectData`/`COREMOD_ACTIVE_PROPERTY`）不变。
  - 删除 `src/main/kotlin/io/alexjoest/stackupup/bootstrap/StackUpUpLateMixinLoader.kt`。
  - `build-logic/convention/src/main/kotlin/minecraft.gradle.kts`：jar manifest 新增 `MixinConnector` → `io.alexjoest.stackupup.bootstrap.StackUpUpMixinConnector`（useMixins 门控；属性名取自 CleanMix 0.7.1 `Constants.ManifestAttributes.MIXINCONNECTOR = "MixinConnector"`，`MixinPlatformAgentDefault.accept/prepare` 读取）。
  - 测试（历史迁移记录）：`StackUpUpLateMixinLoaderSkipLogTest` 后改名为 `StackUpUpMixinConnectorSkipLogTest`（6 用例，谓词注入替代 Context）；`MixinBooterIntegrationTest` 重写为当前 connector 模块表/结构测试。`MixinConfigValidatorTest` 与 `MixinConfigRegistrationAlignmentTest` 属 validator 中间实现，后续已删除；当前不应把它们列为现行测试。

- **关键证据（11.13 源码 `/tmp/mb11src` 与 cleanmix-0.7.1 实测）：**
  - 注册链：`ModDiscoverer.discover()`（MixinBooterPlugin 构造期）扫描 mods 目录与 LaunchClassLoader URL 上的 jar，记录 manifest 带 `MixinConfigs`/`MixinConnector` 的 jar → `MixinBooterService.getMixinContainers()` → `MixinPlatformManager.inject()`（injectData 内 `platform.inject()`，MixinBooterPlugin.java:60）再扫描容器 → `MixinPlatformAgentDefault.prepare()` 读 manifest 属性 → `addConnector` → `MixinConnectorManager.inject()` 实例化并调 `connect()`。
  - **dev 环境可用性（已实证）**：RFG 2.0.2 `MCPTasks.java` 的 `runServer`/`runClient` classpath 均含 `task.classpath(taskJar)`（:598-601/:610-613），即 `build/libs/StackUpUp-0.2.4-dev.jar` 以 jar 形式在 dev 运行时 classpath 上，会被 ModDiscoverer 扫描；运行日志 `[CleanMix]: Successfully loaded Mixin Connector [io.alexjoest.stackupup.bootstrap.StackUpUpMixinConnector]` 实证 dev 发现成功。
  - **Kotlin object 不可用于 connector（与任务预设的 object 写法偏离，须记录）**：`MixinConnectorManager.loadConnectors()` 用 `connectorClass.getDeclaredConstructor().newInstance()` 且**不调用 setAccessible**（MixinConnectorManager.java:82）；Kotlin object 编译为私有构造器 → `newInstance()` 抛 IllegalAccessException → connector 永不装载。故实现为普通 `class`（公共无参构造，javap 实证 `public io.alexjoest.stackupup.bootstrap.StackUpUpMixinConnector()`），与旧 `ILateMixinLoader`（class + 公共无参构造）同一约束。任务原文写 `object`，此处以源码证据为准偏离并记录。
  - `Context`（zone.rong.mixinbooter）已 deprecated（11.0 起），connect() 无 Context 参数；mod 在场判断改用 `zone.rong.mixinbooter.service.ModDiscoverer.isModPresent(String)`（public static，源码实证），与旧 `Context.isModPresent`（内部即 `presentMods.contains`，presentMods 来自 `ModDiscoverer.getPresentMods()`）同一数据源。
  - 当前全部 17 个 mixin 配置（early 1 + late 16）声明 `"target": "@env(DEFAULT)"`；11.13 CleanMix 模型 DEFAULT 配置统一在 DEFAULT 阶段（EnvironmentStateTweaker → gotoPhase(DEFAULT)，位于 injectData 之后）装载，connect() 在 injectData 内 add 早/晚配置无行为差异（运行日志实证：early 配置 `Preparing config mixins.stackupup.early.json (20)` + 全部目标 APPLY）。
- **验证结果（实际执行）：**
  - `test` 全量：BUILD SUCCESSFUL（含重写后 `StackUpUpMixinConnectorSkipLogTest` 6/6、`MixinBooterIntegrationTest` 5/5 等，0 failures）。`MixinConfigRegistrationAlignmentTest` 是 validator 中间版本测试，后来已删除，不属于当前验证结果。三护栏用例含在 `test` 内（`MixinBooterIntegrationTest`/`EarlyMixinBytecodeSafetyTest`/`CoremodHierarchyBytecodeSafetyTest` 全部通过）。
  - `spotlessCheck`：FAILED，但仅剩**预存违规** `src/main/kotlin/io/alexjoest/stackupup/rules/field/RuleFieldContextProvider.kt`（enum 单行化，jj 工作区未改动该文件，租约外不修）；本次迁移新增/改写的全部文件通过 spotless。
  - deprecation 警告：`compileKotlin compileTestKotlin --rerun-tasks` 全量重编，`IEarlyMixinLoader`/`ILateMixinLoader`/`Context` 警告 0 残留（8.6.1 记录的 6 主 + 15 测处已消除）；剩余 2 条警告均为既有无关项（StackLimitHooksTest 旧重载、WrapperCapacityDiagnosticTest 空性）。
  - `runServerAutoTestMatrix`：**最近一次（2026-08-10）通过**——`run/logs/autotest-report.txt` 最终状态 `PASS`、`run/logs/latest.log` 同批次（无 `autotest-failed.marker`）。2026-08-09 本迁移首次运行时曾失败于 `inv_wrapper_limit`/`sided_inv_wrapper_limit` 槽位上限=64 预期=10000；**该失败已被 §3.12 推翻**：其为探针预期问题（转发 wrapper 探针期望仍按旧语义 `getSlotLimit == compat`），§3.12 已把预期改为 `delegateInventoryStackLimit(inventory)` 并实测通过（见 §3.12 取代关系）。迁移装载链本身的证据：`Successfully loaded Mixin Connector`、early 配置 20 mixins 准备并 APPLY（ItemMixin/ContainerMixin/SlotLimitMixin/SlotItemHandlerMixin/ForgeItemHandlerLimitMixin×2/VanillaInventoryLimitMixin×9 等）、15 个 late 配置按 mod 缺失逐条 INFO 跳过、combined/ranged/slot_item_handler 三探针 10000 通过。

- **剩余风险/UNKNOWN：** ① 2026-08-09 首次运行的两探针失败已由 §3.12 修正探针预期并实测通过，不再是当前失败项；此后代码改动未经矩阵重跑，当前工作副本的矩阵结果未重新取得；② `spotlessCheck` 的 RuleFieldContextProvider 预存违规未修（租约外）；③ 生产混淆 jar 的 connector 装载（notch 环境）未实测（dev/SRG 证据已覆盖 manifest 注册与装载）。

### 8.9 MixinBooter 11.17 + CleanMix 0.7.2 成对升级（2026-09-15）

本节记录 11.13/0.7.1 → 11.17/0.7.2 的成对升级实施与实测。升级已完成并通过编译/测试/refmap 验证；带真实第三方模组的服务端矩阵在**换用正确的 1.12.2 AE2 产物**（`ae2-uel-v0.56.7.jar`）后 PASS（2026-09-15 21:48:10，`run/logs/autotest-report.txt` 最终状态 `PASS`：IngotSteel/PlateSteel/DustSteel 解析=1024 实际=1024 插槽=10000 存入=128 剩余=0，VacuumTube 解析=512 实际=512，兼容探针无失败；`latest.log` 装载 31 mods，`mixinbooter.log` 显示 `mixins.stackupup.late.ae2.json` 的 AppEng* 注入 APPLY）。升级过程中曾因使用**非 1.12.2 的 AE2 产物**得到 FAIL，该段失败记录与更正一并保留在下文。

- **实际变更：** `gradle/libs.versions.toml:20` `mixinbooter "11.13" → "11.17"`、`:23` `cleanmix "0.7.1" → "0.7.2"`（`:21-22` 注释同步为 11.17/0.7.2）；`build.gradle.kts:60-61` 注释同步。`build.gradle.kts:53` 的 `modUtils.enableMixins("zone.rong:mixinbooter:${libs.versions.mixinbooter.get()}", ...)` 原本已是目录版本插值，无写死版本可改；`build.gradle.kts:63` 的 `annotationProcessor(libs.cleanmix)` 按任务要求保留不动。未改 mixin 实现、未改 AE2 jar、未新增依赖。
- **坐标与 POM 证据（本机 gradle 缓存实测）：** `mixinbooter-11.17.pom`（缓存 `c12432ed…`）声明 `com.cleanroommc:cleanmix:0.7.2`、`com.cleanroommc:mixinextras-common:0.5.5`、`gson:2.8.0`、`guava:21.0`、`org.ow2.asm:asm-debug-all:5.2`（均 compile scope），无 relocation。`cleanmix-0.7.2.jar`（`b8793a2f…`，716 条目）`META-INF/services/javax.annotation.processing.Processor` 注册 `MixinObfuscationProcessorInjection`/`MixinObfuscationProcessorTargets`（refmap 生成器仍在）；其 POM 依赖 guava 21.0、gson 2.2.4、asm-tree/commons/util 9.8（均 runtime）。`mixinbooter-11.17.jar`（`3ab489a2…`，1469 条目）service 仅注册 `MixinExtrasAP`、jar 内**无** obfuscation AP、**无** `org/objectweb/asm`（`org/spongepowered/asm/lib` 仅 9 个转发类），与 11.13 同形态 → cleanmix AP 仍需编译期显式挂载。上游 `CleanroomMC/CleanMix@master` `build.gradle` 的 `asmVersion` 默认 `9.0`。
- **验证 1（PASS，refmap 未丢）：** `JAVA_HOME=/c/Users/25761/.jdks/jbr-25.0.4 ./gradlew --no-daemon compileJava` → BUILD SUCCESSFUL（3m41s）；javac 注记 `SpongePowered MIXIN Annotation Processor Version=0.8.7` 与 `Writing refmap to build/tmp/mixins/mixins.StackUpUp.refmap.json`。清空 `build/tmp/mixins` 后重跑 `compileJava processResources` 再次生成：`build/tmp/mixins/mixins.StackUpUp.refmap.json`（11906 B，mtime 2026-09-15 21:06:58）与 `build/resources/main/mixins.StackUpUp.refmap.json`（11906 B）`cmp` IDENTICAL，含 58 处 `func_` SRG 映射（`mappings` 16 键）。`test`/`spotlessCheck` 本轮亦 BUILD SUCCESSFUL。
- **验证 2（PASS）：** `./gradlew --no-daemon test` → BUILD SUCCESSFUL（53s）。
- **验证 3（PASS，换用正确 1.12.2 AE2 产物后）：** `runServerAutoTestMatrix` 通过（2026-09-15 21:48:10，BUILD SUCCESSFUL）。`run/logs/autotest-report.txt` 最终状态 `PASS`：矩阵 IngotSteel/PlateSteel/DustSteel `原版基线=64 解析=1024 实际=1024 插槽=10000 存入=128 剩余=0`、VacuumTube `解析=512 实际=512`、`兼容探针: 无失败`；`run/logs/latest.log`（21:48:11）`successfully loaded 31 mods`；`run/logs/mixinbooter.log:37-40` 显示 `Preparing mixins.stackupup.late.ae2.json (4)` 且 `AppEngAdaptorItemHandlerMixin`/`AppEngInternalAEInventoryMixin`/`AppEngInternalInventoryMixin` 三个 mixin 已加入 classloader（注入 APPLY）。此次使用的工作副本 `local-dev-mods/ae2-uel-v0.56.7.jar` 为正确 1.12.2 产物。
- **过程中的一次 FAIL（使用错误的 AE2 产物，已更正，不作为当前事实）：** 中间若干次矩阵运行（20:09、20:31、20:47、21:03 等）失败于 `org.spongepowered.asm.launch.MixinInitialisationError: Error initialising mixin config ae2.mixins.json` → `java.lang.IllegalArgumentException: The requested compatibility level JAVA_11 could not be set. Level is not supported by the active JRE or ASM version (Java 1.8, ASM 5.2 (ASM5))`。经复核，这些运行使用的是 `appliedenergistics2-forge-15.4.10`——其 `META-INF/mods.toml` 为 `loaderVersion="[44,)"`（Forge 44+ 对应 **MC 1.19**，1.12.2 用 `mcmod.info`），manifest `MixinConfigs: ae2.mixins.json` 指向的配置是 `compatibilityLevel: JAVA_11`。**该 jar 不是本项目的 1.12.2 目标产物**，因此该 FAIL 是选错产物所致，不代表 11.17/0.7.2 的问题。换回 `ae2-uel-v0.56.7.jar`（`mcmod.info` mcversion=1.12.2、`FMLCorePlugin: appeng.core.AE2ELCore`、manifest 无 `MixinConfigs`、jar 内不含 `ae2.mixins.json`）后矩阵 PASS（21:48，数值见上）。
- **补充观察（非本次崩溃的根因）：** 另一次运行（工作副本 `local-dev-mods/` 为 gitignored 且缺失时）只装载 4 个 mod、AE2 未在场，`run/logs/autotest-report.txt`（2026-09-15 20:05:06）记 `最终状态: PASS`——该次未覆盖目标场景，不构成 AE2 路径的证据。
- **ASM 版本观察（真实观察，非上述错版 AE2 崩溃的根因）：** 运行期 classpath 上唯一的 ASM 是 `asm-debug-all:5.2`。对 `runServer` 任务全部 74 个 classpath jar 逐一扫描 `org/objectweb/asm/Opcodes`，只有 `asm-debug-all-5.2.jar` 命中；cleanmix 0.7.2 携带的 asm 9.8 只存在于 **annotationProcessor** 配置（`api(mixin)` 与 `annotationProcessor(mixin)` 均为 `isTransitive=false`，cleanmix 又只挂 AP），因此 9.8 不进 `runtimeClasspath`。`dependencyInsight --configuration runtimeClasspath --dependency asm-debug-all` 显示来源为 `runtimeClasspath`；进一步实测该 5.2 来自 RFG 自身：RFG 2.0.2 源码 `MCPTasks.java:1023-1056` 对 MC 1.12.2 显式 `deps.add(PATCHED_MC_CFG, "org.ow2.asm:asm-debug-all:5.2")`（配置名 `patchedMinecraft`），并经 `MCPTasks.java:916` 的 `runtimeClasspath.extendsFrom(patchedMinecraft)` 进入运行 classpath，而非 `build-logic/convention/src/main/kotlin/localDevMods.gradle.kts:88`。Mixin 的 ASM 探测（`org.spongepowered.asm.util.asm.ASM` 读 `org/objectweb/asm/Opcodes`）因此只看到 5.2（`Detected ASM Version : ASM 5.2 (ASM5)`）。这是当前 dev 运行期的 ASM 版本事实，与 1.12.2 目标产物路径无冲突。
- **`exclude module: 'asm-debug-all'` 实测记录（均已还原，未留在分支）：** ① 仅从 `runtimeClasspath` 排除 `asm-debug-all` → 错版 AE2 场景下崩溃不变（`patchedMinecraft` 仍带入 5.2，探针实测运行 classpath 仍只有 5.2）；② 同时从 `runtimeClasspath` + `patchedMinecraft` 排除 → `:compilePatchedMcJava` 以 `java.lang.NullPointerException`（Gradle `ConstantsTreeVisitor.getBinaryClassName`）FAILED，构建直接破坏；③ ② 基础上再加 `asm`/`asm-tree`/`asm-commons`/`asm-util`/`asm-analysis` 9.8 运行依赖 → 同样 `compilePatchedMcJava` NPE（且违反本任务「不新增依赖」）；④ `api(mixin)` 改 `isTransitive=true` → 该场景崩溃不变（ASM 5.2 仍先命中）。结论：`exclude module: 'asm-debug-all'` 这条上游做法在本项目 RFG 2.0.2 布局下不可行——要么不生效，要么打掉 RFG 编译 patched MC 所需的 ASM。**若将来引入使用 `compatibilityLevel: JAVA_11` 的 1.12.2 第三方 mod，才需要处理该运行期 ASM 版本问题（引入运行期 asm 9.x 或调整 RFG `patchedMinecraft` 的 ASM 选择）；当前 1.12.2 目标产物（`ae2-uel-v0.56.7.jar` 等）不触发。**
- **剩余风险与未覆盖：** `runClient` 未跑（任务只要求服务端矩阵）；工作副本内的 `local-dev-mods/` 为 gitignored 的本地诊断复制，不随分支进版本库，因此**评审员在其他检出上跑矩阵时须自行放入正确 1.12.2 的 mod jar**（尤其 AE2 必须是 `ae2-uel-v0.56.7.jar` 一类 `mcmod.info`/1.12.2 产物，不能用 1.19 的 `appliedenergistics2-forge-*`，否则会得到上文的 JAVA_11 FAIL）；同样 gitignored 的 `run/config/stackupup.cfg` 需令 `maxStackSize=10000`，否则规则解析值会被配置上限（默认 64）钳到 64、矩阵数值形态不同。生产混淆 notch 环境未实测。独立复核未做，本记录不宣布升级验收通过。
