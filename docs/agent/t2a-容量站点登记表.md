# T2a 容量站点登记表

> 任务：T2a「容量补丁准入规则」（docs/agent/重构任务清单.md 当前状态表）；编译期三分类与 patch 目标登记表，只登记事实与准入规则，不修改生产目标。
> 状态：PARTIAL（登记表已产出并复核；部分项 UNKNOWN 见正文，独立复核待指派，未完成前不得宣称 PASS）。证据基线：当前工作副本（T4a 已应用：inventory-write 通道已移除，`VanillaInventoryWriteMixin` 已删除、`StackLimitHooks.resolveInventoryWriteLimit` 已不存在，`rg` 复核无命中）+ `build/rfg/minecraft-src/java/` 为 Forge/vanilla 反编译源码；第三方模组 jar 缺失，写入路径一律 **无源码不可判定**（缺失 jar 见 §5）；生产代码路径一律使用 repo-relative 形式（`src/main/java/...`、`src/main/kotlin/...`）；所有行号与工作副本一致（2026-08-08 `rg`/`sed` 复核）。

## 1. 三分类定义（判定共同准则见 AGENTS.md「容量与 remainder 不变量」）

- **自洽**：真实写入在落盘前重新读取同一上限来源，或使用与广告值相同的底层来源。
- **断链**：只改变广告值，真实写入不读取该值，也没有可靠 remainder。
- **转发**：查询或写入转发给 delegate；包装器本身不是可安全抬高的独立容量来源。

判定只依据目标对象的实际写入路径源码，不得依据类名、`== 64` 哨兵或第三方主动表态（AGENTS.md「容量与 remainder 不变量」节）。第三方无源码一律 **无源码不可判定**（共同准则见 §1）；`EntityEquipmentInvWrapper` 按 P0 事实 a 分开读取 Forge wrapper 与 vanilla 实体路径后判定。

## 2. early mixin 容量站点（src/main/java/io/alexjoest/stackupup/mixin/early/）

「写入路径」列为真实写入/合并面；「证据」列同时给出我方注入点与 vanilla/Forge 源码行号。vanilla/Forge 源码前缀 `build/rfg/minecraft-src/java/net/minecraft`（以下简写 `.../net/minecraft`）。

### 2.0 物品自身上限（底层来源站点）

| 站点 | 目标类 | 方法（完整 descriptor） | 写入路径 | 分类 | 证据（file:line） | 缺失 jar |
| --- | --- | --- | --- | --- | --- | --- |
| ItemMixin | net.minecraft.item.Item | `getItemStackLimit(Lnet/minecraft/item/ItemStack;)I`（`remap=false`） | 物品层上限单一来源；消费面为 `ItemStack.getMaxStackSize` 与全部合并/写入路径 | 自洽（底层来源站点；广告与各写入面的 maxStackSize 计算同源） | src/main/java/io/alexjoest/stackupup/mixin/early/ItemMixin.java:12-24；src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt:58-75（applyDynamicStackLimit）、:106-122（cacheResolvedItemLimit/lookupResolvedItemLimit） | — |
| ItemStackMixin | net.minecraft.item.ItemStack | `getMaxStackSize()I` | 堆叠层广告；消费 `lookupResolvedItemLimit` 缓存（ItemMixin 经 `cacheResolvedItemLimit` 写入）或回退 `applyDynamicStackLimit`；消费面同 ItemMixin | 自洽（与 ItemMixin 同源） | src/main/java/.../mixin/early/ItemStackMixin.java:11-19；src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt:106-122（lookupResolvedItemLimit） | — |

### 2.1 `VanillaInventoryLimitMixin`（`getInventoryStackLimit()I`，编译期表内 12 个 vanilla 类）

站点：`VanillaInventoryLimitMixin.stackupup$replaceCompatibilityLimit`（src/main/java/.../mixin/early/VanillaInventoryLimitMixin.java:43-50），目标方法均为 `getInventoryStackLimit()I`，`require = 0`（同文件 :46）；运行时 `original == 64` 哨兵已按 T11 删除（t2b §6.3），当前实现直接返回全局兼容上限。

| 目标类 | 写入路径 | 分类 | 证据（file:line） |
| --- | --- | --- | --- |
| TileEntityChest、TileEntityDispenser、TileEntityShulkerBox | 继承 `TileEntityLockableLoot#setInventorySlotContents`，落盘前重读 `getInventoryStackLimit()` 夹取 | 自洽 | `.../net/minecraft/tileentity/TileEntityLockableLoot.java:155-162` |
| TileEntityFurnace | 自有 setter 落盘前重读上限夹取 | 自洽 | `.../net/minecraft/tileentity/TileEntityFurnace.java:97-110` |
| TileEntityBrewingStand、TileEntityHopper | 自有 setter（BrewingStand :337、Hopper :100），落盘路径同 TileEntityLockableLoot 语义 | 自洽 | `.../net/minecraft/tileentity/TileEntityBrewingStand.java:337`、`.../net/minecraft/tileentity/TileEntityHopper.java:100` |
| EntityMinecartContainer | 自有 setter 落盘前重读上限夹取 | 自洽 | `.../net/minecraft/entity/item/EntityMinecartContainer.java:111-118` |
| InventoryPlayer | `addResource` 落盘前重读上限夹取（:346-348）；`canMergeStacks` 以同一上限门控合并（:67-69）；setter（:610）为朴素写入 | 自洽（两条主写入路径消费同一来源；A5 的 clamp 回调以 `resolveInventoryClampLimit` 收紧） | `.../net/minecraft/entity/player/InventoryPlayer.java:67-69`、`:346-348`、`:610`、`:864` |
| InventoryBasic | setter 落盘前重读上限夹取；insert 合并上限同源 | 自洽（子类 InventoryEnderChest 继承该实现，`InventoryEnderChest.java:9`，经继承获得扩展） | `.../net/minecraft/inventory/InventoryBasic.java:97-102`、`:143-149`、`:273-275` |
| InventoryLargeChest | 查询转发上箱 `upperChest.getInventoryStackLimit()`（:203-205）；写入按 index 分发给两半箱各自 setter（:188-196），两半各自 clamp | 转发（查询与写入均委托两半箱，包装器不是独立容量来源）；两半上限不一致时广告（上箱）与下箱写入夹取脱节 = 条件断链，由 T2b+T11 处置（先证明两半不一致，再确认不在目标表） | `.../net/minecraft/inventory/InventoryLargeChest.java:188-196`、`:203-205` |
| InventoryMerchant | 自有 setter 落盘前重读上限夹取 | 自洽 | `.../net/minecraft/inventory/InventoryMerchant.java:99-105`、`:202` |
| InventoryCrafting | setter 朴素写入（:176-180，不夹取）；常规写入量经合并路径（ContainerMixin 收紧）先行受限 | 自洽（经合并路径前置收紧，与广告同源）；直接 putStack 旁路不读取该值属原版语义残余风险 | `.../net/minecraft/inventory/InventoryCrafting.java:176-180`、`:185-187`；src/main/java/.../mixin/early/ContainerMixin.java:14-26 |
| InventoryCraftResult | setter 朴素写入（:150-154）；结果堆叠量受配方结果上限约束 | 自洽（写入量不依赖该广告值；广告值被 slot/合并链消费） | `.../net/minecraft/inventory/InventoryCraftResult.java:150-154`、`:158-160` |

### 2.2 槽位与 handler 广告面

| 站点 | 目标类 | 方法（完整 descriptor） | 写入路径 | 分类 | 证据（file:line） | 缺失 jar |
| --- | --- | --- | --- | --- | --- | --- |
| SlotLimitMixin | net.minecraft.inventory.Slot | `getItemStackLimit(Lnet/minecraft/item/ItemStack;)I` | `Slot.putStack` → `inventory.setInventorySlotContents`（:97；多数 vanilla 库存落盘前重读同一上限夹取）；广告值再取 `min(dynamic, inventory.getInventoryStackLimit())`（`invLimit > 0` 时；非正值路径安全性当前未被保证，AGENTS.md） | 自洽（广告被库存真实上限夹取；写入面同源） | src/main/java/.../mixin/early/SlotLimitMixin.java:12-28；`.../net/minecraft/inventory/Slot.java:97`（putStack）、`:113-120`（getSlotStackLimit/getItemStackLimit）；src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt:161-172（resolveDynamicSlotLimit） | — |
| SlotItemHandlerMixin.getSlotStackLimit | net.minecraftforge.items.SlotItemHandler | `getSlotStackLimit()I` | `putStack` → `handler.setStackInSlot`（Forge SlotItemHandler.java:89）；insert 路径由 handler 自算 limit（ItemStackHandler.java:88,164） | 转发（广告转发自 `itemHandler.getSlotLimit(index)`，Forge SlotItemHandler.java:109）。**T3 §3.5：已移除独立动态上限注入（原 `Math.max(original, compat)` 会把广告抬到 handler 真实上限之上），仅保留 @Shadow 读取；槽位广告自然跟随 handler 侧真实来源** | src/main/java/.../mixin/early/SlotItemHandlerMixin.java:28-29（仅 @Shadow）；`.../net/minecraftforge/items/SlotItemHandler.java:89`、`:109` | — |
| SlotItemHandlerMixin.getItemStackLimit | net.minecraftforge.items.SlotItemHandler | `getItemStackLimit(Lnet/minecraft/item/ItemStack;)I` | 广告值经 handler 侧 `insertItem(..., true)` simulate 求得（:118-134），再由 `resolveItemHandlerSlotLimit` 按 `min(slotLimit, itemLimit)` 收敛 | 自洽（基于真实 simulate 结果，与写入面同源）。**T3 §3.5：保留**；旧 `Math.max(original, dynamic)` 分支已不存在，当前实现只经 `resolveItemHandlerSlotLimit` 按 `min(slotLimit, itemLimit)` 收敛（决策记录 §2） | src/main/java/.../mixin/early/SlotItemHandlerMixin.java:26-39；`.../net/minecraftforge/items/SlotItemHandler.java:118-134`；src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt:175-189 | — |
| ContainerMixin | net.minecraft.inventory.Container | `mergeItemStack(Lnet/minecraft/item/ItemStack;IIZ)Z` 内 `Slot.getSlotStackLimit()I` 调用 | 合并写入前置：wrap 取 `min(declaredSlotLimit, inventory.getInventoryStackLimit())` 后经 `resolveDynamicSlotLimit` 收紧；merge 写入上界 `maxSize = min(slot.getSlotStackLimit(), stack.getMaxStackSize())`，随后 `setCount`/`shrink` 落库 | 自洽（merge 写入上界与广告同源，夹取后 ≤ 真实库存上限） | src/main/java/.../mixin/early/ContainerMixin.java:14-26；src/main/kotlin/io/alexjoest/stackupup/ContainerInsertHooks.kt:8-13；`.../net/minecraft/inventory/Container.java:610`（mergeItemStack）、`:644-655`（maxSize 与 setCount/shrink）、`:700-706`（空槽 putStack 路径） | — |

### 2.3 `ForgeItemHandlerLimitMixin`（`getSlotLimit(I)I`，T3 收敛后 2 个 Forge 类，原 6 类）

站点：`ForgeItemHandlerLimitMixin.stackupup$replaceCompatibilityLimit`（src/main/java/.../mixin/early/ForgeItemHandlerLimitMixin.java:43-46，`@Mixin` 列表 :33-39）。T3 收敛（决策记录 §3.5）后目标集合为 2 个自洽类（ItemStackHandler、EntityEquipmentInvWrapper）；四个转发 wrapper 已移出注入（下行标注）。`:45` 的 `original == 64` 值检查是目标类的取值语义（装甲槽 1 必须保持），不再是「该目标是否可 patch」的准入判据（§3.5）；§6 规则 7 的哨兵行号已按 T11/T3 后状态刷新，见 §9.3。

| 目标类 | 写入路径 | 分类 | 证据（file:line） |
| --- | --- | --- | --- |
| net.minecraftforge.items.ItemStackHandler | `insertItem` 写入前用 `getStackLimit = min(getSlotLimit, itemMax)` 重读同一上限（:88、:162-164）；`getSlotLimit` 自有字段 64（:157-160） | 自洽（写入前读取同一上限来源） | `.../net/minecraftforge/items/ItemStackHandler.java:79-100`、`:157-164` |
| net.minecraftforge.items.wrapper.EntityEquipmentInvWrapper | `insertItem` 内 `limit = getStackLimit(slot, stack)`（:95），`simulate == false` 时经 `setItemStackToSlot`/`grow` 落库（:108-120），达上限返回 remainder（:122）；`getSlotLimit` 装甲槽 1 / 其余 64（:162-165），`getStackLimit = min(getSlotLimit, maxStackSize)`（:168-171） | 自洽（P0 事实 a：非「无余量必吞」，Forge wrapper 层上限计算与 remainder 闭合；装甲槽返回 1 不被提升）。vanilla 实体 setter 路径已由 T3 闭合（决策记录 §3.5：`EntityLiving.java:1012-1022`、`EntityPlayer.java:2432-2449`、`EntityArmorStand.java:155-167` 为无截断列表直写），不得由 wrapper 数值推导 | `.../net/minecraftforge/items/wrapper/EntityEquipmentInvWrapper.java:86-122`、`:162-171`；`.../net/minecraft/entity/EntityLivingBase.java:1852-1856` |
| net.minecraftforge.items.wrapper.InvWrapper | `getSlotLimit` 转发 `inv.getInventoryStackLimit()`（:202-204）；写入转发 wrapped `IInventory` 的 `setInventorySlotContents`（其 clamp 重读同一来源） | 转发（查询转发；包装器不是独立容量来源，安全依赖 wrapped inventory 写入面）。**T3 §3.5：已移出 ForgeItemHandlerLimitMixin 注入，不再作为独立容量来源**；**T10 §3.6：已移出 AE2 白名单，走 `min(64, getSlotLimit)` 限流分片**；保留登记供写入面核对 | `.../net/minecraftforge/items/wrapper/InvWrapper.java:73-135`（insertItem）、`:202-204` |
| net.minecraftforge.items.wrapper.SidedInvWrapper | `getSlotLimit` 转发 `inv.getInventoryStackLimit()`（:231-233）；写入转发 inv | 转发。**T3 §3.5：已移出 ForgeItemHandlerLimitMixin 注入**；**T10 §3.6：已移出 AE2 白名单，走 `min(64, getSlotLimit)` 限流分片**；保留登记供写入面核对 | `.../net/minecraftforge/items/wrapper/SidedInvWrapper.java:88-148`、`:231-233` |
| net.minecraftforge.items.wrapper.CombinedInvWrapper | `getSlotLimit` 按 index 转发子 handler（:128-133）；写入/insertItem 转发（:83-88、:109-114） | 转发。**T3 §3.5：已移出 ForgeItemHandlerLimitMixin 注入**；保留登记供写入面核对 | `.../net/minecraftforge/items/wrapper/CombinedInvWrapper.java:109-114`、`:128-133` |
| net.minecraftforge.items.wrapper.RangedWrapper | `getSlotLimit` 转发 compose（:98-102）；insertItem 转发（:66-70） | 转发。**T3 §3.5：已移出 ForgeItemHandlerLimitMixin 注入**；保留登记供写入面核对 | `.../net/minecraftforge/items/wrapper/RangedWrapper.java:66-70`、`:98-102` |

补充（不在本 mixin 目标内、同属 Forge 面）：`VanillaDoubleChestItemHandler#getSlotLimit` 转发箱体 `getInventoryStackLimit`（`VanillaDoubleChestItemHandler.java:184-187`，底层两半由 §2.1 覆盖）；`EmptyHandler#getSlotLimit` 返回 0（`EmptyHandler.java:66`，不扩容）。

### 2.4 `InventoryPlayerAddResourceMixin`（resolveInventoryClampLimit 两调用方，P0 事实 c）

| 站点 | 目标类 | 方法（完整 descriptor） | 写入路径 | 分类 | 证据（file:line） | 缺失 jar |
| --- | --- | --- | --- | --- | --- | --- |
| stackupup$useMergeLimit | net.minecraft.entity.player.InventoryPlayer | `canMergeStacks(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z` 内 `getInventoryStackLimit()I` 调用 | clamp 值门控 `canMergeStacks` 合并判定（InventoryPlayer.java:67-69）；合并量在 `addResource` 内以同一上限夹取（:346-348）；hook 内重新读取 `inventory.getInventoryStackLimit()` 与 `stack.maxStackSize` 取 min | 自洽（clamp 值与写入夹取同源）。**必须保留（P0 事实 c）** | src/main/java/.../mixin/early/InventoryPlayerAddResourceMixin.java:12-22（调用点 :21）；src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt:192-203（resolveInventoryClampLimit 定义） | — |
| stackupup$usePickedStackLimit | net.minecraft.entity.player.InventoryPlayer | `addResource(ILnet/minecraft/item/ItemStack;)I` 内 `InventoryPlayer.getInventoryStackLimit()I` 调用 | 同上：`addResource` 落盘夹取（InventoryPlayer.java:346-348） | 自洽（同上）。**必须保留（P0 事实 c）** | src/main/java/.../mixin/early/InventoryPlayerAddResourceMixin.java:35-44（调用点 :43）；src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt:192-203 | — |
| stackupup$usePickedItemLimit（相关非容量站点，附注） | net.minecraft.entity.player.InventoryPlayer | `addResource(ILnet/minecraft/item/ItemStack;)I` 内 `ItemStack.getMaxStackSize()I` 调用 | 不改上限值，仅把取源统一为入站 `source.getMaxStackSize()` | 自洽（与 §2.0 同源）；非 clamp 注入点，不纳入 §6 未登记目标失败规则 | src/main/java/.../mixin/early/InventoryPlayerAddResourceMixin.java:24-33 | — |

### 2.5 命令与实体合并站点

| 站点 | 目标类 | 方法（完整 descriptor） | 写入路径 | 分类 | 证据（file:line） | 缺失 jar |
| --- | --- | --- | --- | --- | --- | --- |
| CommandGiveMixin | net.minecraft.command.CommandGive | `execute` 内 `Item.getItemStackLimit()I`（无参旧变体）表达式 | 命令创建量（CommandGive.java:56 parseInt 上限、:58 创建 ItemStack）随后经 `addItemStackToInventory` → `addResource`（:74 → InventoryPlayer.java:346-348 夹取）写入 | 自洽（写入面再次以同一上限夹取；前提是 InventoryPlayer 上限同被扩展，两 patch 需同时生效，否则夹取回 64 产生余量丢弃）。注意与有参重载 `getItemStackLimit(ItemStack)I` 区分 | src/main/java/.../mixin/early/CommandGiveMixin.java:11-20；`.../net/minecraft/command/CommandGive.java:56`、`:58`、`:74` | — |
| CommandReplaceItemMixin | net.minecraft.command.CommandReplaceItem | `execute` 内 `Item.getItemStackLimit()I`（无参旧变体）表达式 | 命令创建量（CommandReplaceItem.java:118 parseInt 上限、:120 创建）写入 `iinventory.setInventorySlotContents`（:152）或 `entity.replaceItemInInventory`（:163） | 分类依赖目标类型：Tile/`TileEntityLockableLoot` 系目标经 setter clamp 重读同一来源 = 自洽；`InventoryPlayer`/实体目标 setter 为朴素写入、无 remainder（InventoryPlayer.java:610 无夹取）——若目标上限未被同源扩展则为断链（广告提升但写入面不读取）。实体子类 setter 容量由 T3/T13.2 闭合 | src/main/java/.../mixin/early/CommandReplaceItemMixin.java:11-20；`.../net/minecraft/command/CommandReplaceItem.java:118-120`、`:152`、`:163`；`.../net/minecraft/entity/player/InventoryPlayer.java:610` | — |
| EntityItemMergeMixin | net.minecraft.entity.item.EntityItem | `combineItems(Lnet/minecraft/entity/item/EntityItem;)Z` 内 `ItemStack.getMaxStackSize()I` 调用 | 实体掉落物合并：合并量取两边 maxStackSize 最大值后 `grow` 落库（EntityItem.java:258 上限判定、:268 grow） | 自洽（合并写入上限与广告同源，取两者较大值） | src/main/java/.../mixin/early/EntityItemMergeMixin.java:11-22；`.../net/minecraft/entity/item/EntityItem.java:258`、`:268` | — |
| ServerRecipeBookHelperMixin（转移量站点） | net.minecraft.util.ServerRecipeBookHelper | `func_194324_a(I,Z)I` 内常量 64 | 合成台转移量上限（:181 `i = 64`，:193-196 `i < 64` 时 +1）；实际按件转移 `func_194325_a`（:238、:252） | 自洽（转移量随后受 ingredient `getMaxStackSize` 下限收紧 :132、:155；按件写入经 slot 面同源约束） | src/main/java/.../mixin/early/ServerRecipeBookHelperMixin.java:11-17；`.../net/minecraft/util/ServerRecipeBookHelper.java:132`、`:155`、`:181-196`、`:238`、`:252` | — |

### 2.6 非容量站点（early 目录内，不进入三分类登记）

序列化/发包/渲染/掉落支撑链，非容量广告或写入站点：PacketBufferMixin、PacketUtilMixin（StackCountCodec 序列化）、NetHandlerPlayServerMixin（创造发包校验 `isValidCreativeStackPacket`，StackLimitHooks.kt:153-159）、NetHandlerPlayClientMixin（客户端槽同步）、RenderEntityItemMixin / RenderItemMixin（渲染）、ItemStackNbtMixin（NBT 大数量读写）、InventoryHelperMixin（掉落拆分，`spawnItemStack` HEAD 取消后自拆）。

## 3. late mixin 容量站点（src/main/java/io/alexjoest/stackupup/mixin/late/，**当前 25 个 .java 文件**，审计当时 18）

全部为 `@Pseudo` 第三方目标，经 `StackUpUpMixinConnector` 按 `ModDiscoverer.isModPresent(module.modId)` 条件排队装载（src/main/kotlin/io/alexjoest/stackupup/bootstrap/StackUpUpMixinConnector.kt:58-70、:220-239；旧 `StackUpUpLateMixinLoader` 已删除）；单个已登记配置的禁用入口是 MixinBooter `config/mixinbooter.cfg` 的 `general.blacklistedConfigs`，修改后需重启。对应模组 jar 缺失（§5），写入路径一律 **无源码不可判定**；「证据」列只给出我方 mixin 的可观察注入点，不得冒充第三方写入路径证据。方法 descriptor 仅为我方注解声明，未核实第三方实际签名（重载区分以我方注解写法为准）。

| 站点（mixins.* 配置） | 目标类 | 我方注入的方法/点 | 分类 | 证据（file:line） | 缺失 jar |
| --- | --- | --- | --- | --- | --- |
| AppEngInternalInventoryMixin（ae2.json） | appeng.tile.inventory.AppEngInternalInventory | `<init>*` 常量 64→compat（`@ModifyConstant`，require=0）；`getInventoryStackLimit()I` 注入已不存在 | 无源码不可判定（mixin 行为：构造期常量替换；第三方内部写入未核实） | src/main/java/.../mixin/late/AppEngInternalInventoryMixin.java:10-16 | appliedenergistics2 |
| AppEngInternalAEInventoryMixin（ae2.json） | appeng.tile.inventory.AppEngInternalAEInventory | 同上（`@ModifyConstant`，require=0）；`getInventoryStackLimit()I` 注入已不存在 | 无源码不可判定（同上） | src/main/java/.../mixin/late/AppEngInternalAEInventoryMixin.java:10-16 | appliedenergistics2 |
| AppEngPatternTermMixin（ae2.json） | appeng.container.implementations.ContainerPatternTerm / ContainerExpandedProcessingPatternTerm / ContainerWirelessPatternTerminal | `<init>*` 后经反射调用第三方 `setStackLimit(int)` 提升 patternSlotIN | 无源码不可判定（反射注入槽位上限；反射路径违反 AGENTS.md 反射禁令，属既有实现问题，记录不背书，不在本任务修复） | src/main/java/.../mixin/late/AppEngPatternTermMixin.java:24-55 | appliedenergistics2 |
| AppEngAdaptorItemHandlerMixin（ae2.json） | appeng.util.inv.AdaptorItemHandler | 方案 A 后**不含任何注入**（类体为空，仅作 late config 入口保险丝）；`Ae2ItemHandlerInsertLimiter.insertCapped` 保留为纯工具类，仅测试护栏使用，不在热路径上 | 无注入站点（原限流 wrap 已随方案 A 移除，决策记录 §3.8）；被包装的 AE2 侧行为无源码不可判定 | src/main/java/.../mixin/late/AppEngAdaptorItemHandlerMixin.java:18-20；src/main/java/io/alexjoest/stackupup/core/Ae2ItemHandlerInsertLimiter.java:23-63、:82-97 | appliedenergistics2 |
| BrandonsCoreInventoryLimitMixin（brandonscore.json） | com.brandon3055.brandonscore.blocks.TileInventoryBase | `<init>` 写 `stackLimit` 字段（Shadow :20-21）；`getInventoryStackLimit()I` 返回值 64→compat。其注释称 setter 截断检查重读该上限，属第三方声称，非我方写入路径证据 | 无源码不可判定 | src/main/java/.../mixin/late/BrandonsCoreInventoryLimitMixin.java:20-21、:30-38 | brandonscore |
| TileEntityInventoryBaseMixin（actuallyadditions.json） | de.ellpeck.actuallyadditions.mod.tile.TileEntityInventoryBase | `getMaxStackSize` 内常量 64→compat | 无源码不可判定 | src/main/java/.../mixin/late/TileEntityInventoryBaseMixin.java:12-15 | actuallyadditions |
| SimpleInventoryMixin（cyclopscore.json 与 storagenetwork.json 共用） | org.cyclops.cyclopscore.inventory.SimpleInventory | `getInventoryStackLimit()I` 返回值 64→compat | 无源码不可判定（两个 mod 配置共用同一 mixin） | src/main/java/.../mixin/late/SimpleInventoryMixin.java:16-19 | cyclopscore、storagenetwork |
| EnderIOMachineInventoryLimitMixin（enderio.json） | crazypants.enderio.base.machine.baselegacy.AbstractInventoryMachineEntity / crazypants.enderio.machines.machine.enchanter.TileEnchanter | `getInventoryStackLimit()I` 返回值 64→compat | 无源码不可判定 | src/main/java/.../mixin/late/EnderIOMachineInventoryLimitMixin.java:22-25 | enderio |
| EnderIOSlottedInventoryLimitMixin（enderio.json） | crazypants.enderio.machines.machine.soul.TileSoulBinder / slicensplice.TileSliceAndSplice / farm.TileFarmStation | `getInventoryStackLimit(I)I`（带槽重载，与无参重载区分）返回值 64→compat | 无源码不可判定 | src/main/java/.../mixin/late/EnderIOSlottedInventoryLimitMixin.java:23-26 | enderio |
| IEInventoryHandlerMixin（immersiveengineering.json） | blusunrize.immersiveengineering.common.util.inventory.IEInventoryHandler | `getSlotLimit(I)I` 返回值 64→compat；`insertItem` 内 `IIEInventory.getSlotLimit(I)I` 表达式 64→compat | 无源码不可判定（写入路径的表达式虽被改，外围 IE 逻辑未核实） | src/main/java/.../mixin/late/IEInventoryHandlerMixin.java:17-32 | immersiveengineering |
| IEMachineSlotLimitMixin（immersiveengineering.json） | 16 个 blusunrize.immersiveengineering.common.blocks.*.TileEntity*（CokeOven、BlastFurnace×2、AlloySmelter、ArcFurnace、Squeezer、Fermenter、Mixer、Refinery、Assembler、AutoWorkbench、BottlingMachine、Belljar、Toolbox、WoodenCrate、ModWorkbench） | `getSlotLimit(I)I` 返回值 64→compat | 无源码不可判定 | src/main/java/.../mixin/late/IEMachineSlotLimitMixin.java:36-39（目标表 :11-31） | immersiveengineering |
| InvSlotMixin（ic2.json） | ic2.core.block.invslot.InvSlot | `<init>*` 常量 64→compat | 无源码不可判定 | src/main/java/.../mixin/late/InvSlotMixin.java:12-15 | ic2 |
| TileInventoryMixin（mantle.json 与 storagenetwork.json 共用） | slimeknights.mantle.tileentity.TileInventory | `<init>*` 常量 64→compat | 无源码不可判定 | src/main/java/.../mixin/late/TileInventoryMixin.java:12-15 | mantle、storagenetwork |
| TileMechanicalMachineMixin（integrateddynamics.json） | org.cyclops.integrateddynamics.core.tileentity.TileMechanicalMachine | `<init>` 常量 64→compat | 无源码不可判定 | src/main/java/.../mixin/late/TileMechanicalMachineMixin.java:12-15 | integrateddynamics |
| CommonTileInventoryMixin（limelib.json） | mrriegel.limelib.tile.CommonTileInventory | `<init>(I)V` 常量 64→compat | 无源码不可判定 | src/main/java/.../mixin/late/CommonTileInventoryMixin.java:12-15 | limelib |
| NetworkNodeStorageMonitorMixin（refinedstorage.json） | com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeStorageMonitor | `extract` 内常量 64→compat | 无源码不可判定（提取侧限流，非落库夹取） | src/main/java/.../mixin/late/NetworkNodeStorageMonitorMixin.java:12-15 | refinedstorage |
| ItemGridHandlerMixin（refinedstorage.json） | com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandler | `onExtract(Lnet/minecraft/entity/player/EntityPlayerMP;Ljava/util/UUID;II)V` 内 `Math.min(JJ)J`（wrap → `expandDefaultExtractLimit`） | 无源码不可判定（提取上界；`StackLimitHooks.kt:206-207` 有我方实现源码） | src/main/java/.../mixin/late/ItemGridHandlerMixin.java:13-24；src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt:206-207 | refinedstorage |
| ItemGridHandlerPortableMixin（refinedstorage.json） | com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandlerPortable | 同上 `onExtract` 内 `Math.min(JJ)J` | 无源码不可判定 | src/main/java/.../mixin/late/ItemGridHandlerPortableMixin.java:13-24；src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt:206-207 | refinedstorage |
| ColossalChestsTileMixin（colossalchests.json，审计后新增） | org.cyclops.colossalchests.tileentity.TileColossalChest | `constructInventory`/`constructInventoryDebug` 内常量 64→compat（`@ModifyConstant`，require=0，字段级源头修正） | 无源码不可判定（mixin 行为：构造期常量替换；mod 源码逐行核对见 CDR §3.11-A） | src/main/java/.../mixin/late/ColossalChestsTileMixin.java:28-41 | colossalchests |
| EnderIOInventorySlotLimitMixin（enderio.json，审计后新增） | com.enderio.core.common.inventory.InventorySlot | `getMaxStackSize()I` 返回值 64→compat（`@ModifyReturnValue`，require=0） | 无源码不可判定 | src/main/java/.../mixin/late/EnderIOInventorySlotLimitMixin.java:26-36 | enderio |
| EnderIOInventoryNoDropMixin（enderio.json，审计后新增） | crazypants.enderio.base.machine.baselegacy.AbstractInventoryMachineEntity | `setInventorySlotContents(ILnet/minecraft/item/ItemStack;)V` 内 `ItemStack.shrink(I)V` 与 `Block.spawnAsEntity(...)V` 两个调用点（`@WrapOperation` 刻意不调 original，require=0；抑制语义，非容量抬高） | 非容量站点（不改变上限广告；取消 mod 溢出掉落/裁减，失败方向安全） | src/main/java/.../mixin/late/EnderIOInventoryNoDropMixin.java:38-61 | enderio |
| GregTechMetaItemMixin（gregtech.json，审计后新增） | gregtech.api.items.metaitem.MetaItem | `getItemStackLimit(Lnet/minecraft/item/ItemStack;)I` 返回值经规则求值（`@ModifyReturnValue`，require=0） | 无源码不可判定（直呼面归一；per-meta 显式覆盖原样保留，CDR §3.11-B） | src/main/java/.../mixin/late/GregTechMetaItemMixin.java:30-50 | gregtech |
| GregTechMetaPrefixItemMixin（gregtech.json，审计后新增） | gregtech.api.items.materialitem.MetaPrefixItem | 同上（`@ModifyReturnValue`，require=0） | 无源码不可判定（同上） | src/main/java/.../mixin/late/GregTechMetaPrefixItemMixin.java:25-45 | gregtech |
| NuclearCraftTileInventoryLimitMixin（nuclearcraft.json，审计后新增） | 4 个 nc.tile.*Inventory 抽象基类（TileInventory / TileFluidInventory / TileEnergyInventory / TileEnergyFluidInventory） | 以 mixin 类方法体直接覆写 `getInventoryStackLimit()I`（无注入注解；接口 default 不能直接 patch，字节码证据 CDR §3.9） | 无源码不可判定（广告值替换；NC 夹取点动态读该方法，CDR §3.10-B） | src/main/java/.../mixin/late/NuclearCraftTileInventoryLimitMixin.java:25-39 | nuclearcraft |
| NuclearCraftDistributorNoDropMixin（nuclearcraft.json，审计后新增） | nc.multiblock.distributor.Distributor | `cullInventory()Z` 与 `dropOverflow(Ljava/util/List;)V` 各一处 `@Inject(HEAD, cancellable)`（require=0；不裁不丢） | 非容量站点（不改变上限广告；取消裁减/掉落，CDR §3.10-B） | src/main/java/.../mixin/late/NuclearCraftDistributorNoDropMixin.java:33-47 | nuclearcraft |
| RebornCoreInventoryMixin（techreborn.json，当前新增） | reborncore.common.util.Inventory | `getInventoryStackLimit()I` 返回值替换为兼容上限（`@ModifyReturnValue`，require=0） | 无源码不可判定（Tech Reborn 通过 RebornCore 共享该容量入口；真实 setter/handler 仍需第三方源码或运行证据） | src/main/java/.../mixin/late/techreborn/RebornCoreInventoryMixin.java:9-16 | techreborn、reborncore |

## 4. 动态兼容层（历史中间层，已删除；旧 core/ 配置面）

> 本节保留删除提交 `9cff8e7` 前的动态 ASM 审计证据，不描述当前运行时。当前 `StackUpUpCore.getASMTransformerClass()` 在 `src/main/kotlin/io/alexjoest/stackupup/StackUpUpCore.kt:77` 返回 `emptyArray()`；`DynamicCompatTargetProfile`、`DynamicCompatMethodProbe`、`DynamicCompatTargetClassifier`、`CompatibilityLimitPatch`、`DynamicCompatTransformer` 与 `FixedCompatTargets` 均已删除，当前不存在动态 ASM 注册、profile、probe 或 skip 表。

- **历史中间状态（已删除）** `DynamicCompatTargetProfile`（历史路径 `src/main/java/io/alexjoest/stackupup/core/DynamicCompatTargetProfile.java:12-18`）：INVENTORY = `net.minecraft.inventory.IInventory`，候选方法名 `{getInventoryStackLimit, func_70297_j_}`；ITEM_HANDLER = `net.minecraftforge.items.IItemHandler`，候选方法名 `{getSlotLimit}`；SLOT = `net.minecraft.inventory.Slot`，候选方法名 `{getItemStackLimit, func_178170_b, getSlotStackLimit, func_75219_a}`。该 profile 是目标类型 + 候选方法名配置，**不是完整单一事实源**（AGENTS.md「Mixin、ASM 与 core/early 约束」第 3 条）；descriptor 未在配置面区分（`getItemStackLimit` 存在 `(Lnet/minecraft/item/ItemStack;)I` 与旧无参两种）。
- **历史中间状态（已删除）** `DynamicCompatMethodProbe`（历史路径 `DynamicCompatMethodProbe.java:33-46`）：只按方法名扫描当前类直接声明的方法，**不按 descriptor 确认签名**。
- **历史中间状态（已删除）** `DynamicCompatTargetClassifier`（历史路径 `DynamicCompatTargetProfile.java:60-81`）：`FixedCompatTargets.contains` 优先跳过；Slot 子类 → SLOT；IItemHandler 实现 → ITEM_HANDLER；IInventory 实现 → INVENTORY。
- **历史中间状态（已删除）** `CompatibilityLimitPatch`（历史路径 `src/main/java/io/alexjoest/stackupup/core/CompatibilityLimitPatch.java:39-53、:56-86`）：**ITEM_HANDLER profile 直接返回空（:43-46）——历史动态层实际上不会 patch 任何 `getSlotLimit` 声明类**；SLOT/INVENTORY 仅把对应方法内的 `bipush 64` 替换为 `getCompatibilityStackSize()`（:56-86）。
- **历史中间状态（已删除）** 注册：`DynamicCompatTransformer`（历史路径 `DynamicCompatTransformer.java:16-37`）曾经经 `StackUpUpCore.getASMTransformerClass` 注册。
- **历史中间状态（已删除）** `FixedCompatTargets` 固定跳过表（历史路径 `src/main/java/io/alexjoest/stackupup/core/FixedCompatTargets.java:29-61`），26 项：
  - 原版 IInventory 14 项：TileEntityDispenser、TileEntityChest、TileEntityFurnace、TileEntityBrewingStand、TileEntityHopper、TileEntityShulkerBox、EntityMinecartContainer、InventoryPlayer、InventoryBasic、InventoryEnderChest、InventoryLargeChest、InventoryMerchant、InventoryCrafting、InventoryCraftResult（与 §2.1 的 early mixin 目标一一对应；InventoryEnderChest 经继承 InventoryBasic 实现覆盖，`InventoryEnderChest.java:9`）；
  - late mixin 独占 3 项：AppEngInternalInventory、AppEngInternalAEInventory、SimpleInventory（probeCovered=true）；
  - Forge handler/wrapper 9 项：SlotItemHandler（probeCovered=true）、ItemStackHandler、VanillaDoubleChestItemHandler、EntityEquipmentInvWrapper、EmptyHandler、InvWrapper（probeCovered=true）、SidedInvWrapper（probeCovered=true）、CombinedInvWrapper（probeCovered=true）、RangedWrapper（probeCovered=true）。
  - 注记：跳过表条目不等于「被显式 mixin 覆盖」——VanillaDoubleChestItemHandler 与 EmptyHandler 无对应 mixin，动态层亦跳过，保持原值（保守不抬）；Forge 子类继承覆盖：EntityArmorInvWrapper/EntityHandsInvWrapper extends EntityEquipmentInvWrapper，PlayerInvWrapper extends CombinedInvWrapper，PlayerArmorInvWrapper/PlayerMainInvWrapper/PlayerOffhandInvWrapper extends RangedWrapper——基类 mixin 修改被子类继承。
  - 消费方：`all()`/`probeTargets()` 仅被测试与 dev 探针引用。

## 5. 缺失第三方 jar 台账（P0 基线：`local-dev-mods/` 不存在、`run/mods/` 为空）

late 目标 **当前 16 个模组** jar 均缺失（文件名/版本未登记，不得补猜）：`appliedenergistics2`（AE2）、`brandonscore`、`actuallyadditions`、`cyclopscore`、`enderio`、`ic2`、`mantle`、`refinedstorage`、`storagenetwork`、`integrateddynamics`、`limelib`、`immersiveengineering`、`nuclearcraft`、`colossalchests`、`gregtech`、`techreborn`（通过 `reborncore`，其对应 jar 同样缺失；**审计当时为 12 个**，nuclearcraft/colossalchests/gregtech/techreborn 为审计后新增）。当前工作副本 `run/mods/`、`local-dev-mods/` 均为空（gitignored），无任何可读第三方字节码/源码。Forge 自身 wrapper 与 vanilla 反编译源码不属缺失项（§2/§4 已按 repo-relative 源码核验）。注：CDR §3.10/§3.11 的 Colossal Chests / GregTech / NuclearCraft 源码审阅使用本工作副本之外的本地 `../mods-under-test/` 副本，仓库内无可运行 jar，故上述条目仍计入「jar 缺失」台账（缺失的是可运行 jar，非源码副本）。

## 6. patch 目标集合与登记表比对规则（未登记目标失败规则）

1. **未登记目标失败而非警告**：任何现行 early/late mixin 中新增/改动的目标，若不在本登记表 §2/§3 内，登记护栏测试必须失败（Fail Fast），不得静默继续（T2a 目标见 docs/agent/重构任务清单.md「DAG」T2a 节点；失败机制落地由 T2b+T11 执行）。历史 §4 动态 ASM 目标不再参与当前登记双向校验。
2. **登记表与实现双向一致**：表内目标必须有对应实现证据（file:line）；实现中存在但表内缺失 = 未登记目标 = 失败。
3. **descriptor 必须完整**：重载方法必须写完整 descriptor（如 `getInventoryStackLimit()I` vs `getInventoryStackLimit(I)I`、`getItemStackLimit()I` vs `getItemStackLimit(Lnet/minecraft/item/ItemStack;)I`），缺失 descriptor 的目标不得进入构建。
4. **三分类判定更新**：目标分类变更必须附写入路径源码证据；`无源码不可判定` 条目只能因补齐对应 jar/源码或运行时证据（T12.5 报告）升级，不得按类名/注释补结论。
5. **历史动态层边界（已删除）**：历史 `DynamicCompatMethodProbe` 只按方法名探测，无法按 descriptor 区分重载；该缺口保留作删除前审计证据。当前没有动态 ASM，重载目标必须走显式 mixin 与完整 descriptor 登记；不得恢复隐式 probe/skip 机制。
6. **守恒底线**：自洽站点在模拟抬高状态下必须满足 `storedDelta + remainderCount == inserted`（只读审计公式，不是写入后补偿算法）；真实写入后禁止重算余量、回填、重试、补偿。
7. **`== 64` 哨兵待替换**：§2.1/§2.2/§2.3 及 8 个 late mixin 中现存的 `original == 64` 判据（VanillaInventoryLimitMixin.java:47、ForgeItemHandlerLimitMixin.java:31、SlotItemHandlerMixin.java:26；late 侧 AppEngInternalInventory/AppEngInternalAEInventory/BrandonsCoreInventoryLimit/EnderIOMachineInventoryLimit/EnderIOSlottedInventoryLimit/IEInventoryHandler/IEMachineSlotLimit/SimpleInventory 八个 mixin）是本表要取代的准入判据；本表登记完成即不得再以哨兵作为「该目标是否可 patch」的依据。
8. **例外**：非容量站点（§2.4 的 usePickedItemLimit、§3 的 AppEngAdaptorItemHandlerMixin〔方案 A 后已无注入，仅保留类入口〕、§2.6）不受规则 1 约束，但须在表中注明性质。

## 7. 新增目标自检清单

新增/修改任何容量 patch 目标前逐项核对：

- [ ] 目标类与完整方法 descriptor 已写入本登记表（当前 §2/§3 对应分组；已删除的历史 §4 不参与当前登记）；
- [ ] 已给出真实写入路径源码证据（vanilla/Forge 用 `build/rfg/minecraft-src/` 行号；第三方无源码则标 `无源码不可判定` 并列入 §5 缺失 jar）；
- [ ] 三分类判定已按共同准则（§1）完成，未用 `== 64` 哨兵、类名或第三方表态代替证据；
- [ ] 若为第三方目标：对应模组 jar 已补入 `local-dev-mods/`/`run/mods/` 或已有 T12.5 运行时证据，否则不得升级分类；
- [ ] 若为重载方法：descriptor 已区分（无参与有参、带槽与不带槽）；
- [ ] 未触碰 `resolveInventoryClampLimit` 及其两个调用方（§2.4，P0 事实 c）；
- [ ] 未把 `EntityEquipmentInvWrapper` 写成「无余量必吞」（P0 事实 a，§2.3 ForgeItemHandlerLimitMixin 行）；
- [ ] 登记护栏测试已覆盖该目标（未登记即失败），未引入「警告后继续」路径；
- [ ] 自洽目标已具备或已计划守恒测试（`stored + remainderCount == inserted`，模拟态）。

## 8. 特别标注（P0 事实，只引用）

- **P0 事实 c**：`resolveInventoryClampLimit`（src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt:192-203）的两个库存注入回调——`InventoryPlayerAddResourceMixin.stackupup$useMergeLimit`（src/main/java/.../mixin/early/InventoryPlayerAddResourceMixin.java:21）与 `stackupup$usePickedStackLimit`（同文件 :43）——**必须保留**（删除或改动前必须逐一定位，AGENTS.md；本表 §2.4 两行即这两个调用方）。P0 台账中记录的内部委派 `resolveInventoryWriteLimit`（旧 StackLimitHooks.kt:174-191）在当前工作副本中已被 T4a 移除（`rg -n "resolveInventoryWriteLimit" src/main` 无命中；`VanillaInventoryWriteMixin` 已删除），因此当前调用面只有上述两个直接 clamp 注入点。
- **P0 事实 a**：`EntityEquipmentInvWrapper` 不是无余量必吞——上限计算（`EntityEquipmentInvWrapper.java:86-105`、`:168-171`）、真实写入（`:108-120`）、remainder 返回（`:122`）均可由源码确认；本表 §2.3 该行判定基于此。vanilla 实体 setter 路径（`EntityLivingBase#setItemStackToSlot` 抽象声明，`EntityLivingBase.java:1852-1856`）已由 T3 闭合（决策记录 §3.5：`EntityLiving.java:1012-1022`、`EntityPlayer.java:2432-2449`、`EntityArmorStand.java:155-167` 为无截断列表直写）。

## 9. T13 回填：证据来源标注与收缩刷新（2026-08-08 追加）

> 任务：T13「覆盖面收缩与回填」的 T2 登记表回填部分（T12.5 第三种状态的关键动作；决策记录 §3.7 配套）。证据来源三态：`源码` = build/rfg 反编译源码行号（已有关联保持）；`运行时` = T12.5 守恒报告事件（run/logs/stackupup-conservation.jsonl，注明事件号与 handler 类名）；`UNKNOWN` = 无源码第三方（保持无源码不可判定，红线：不得按类名/注释补猜，AGENTS.md 与 §6 规则 4）。本回填只追加标注与刷新 T3/T10 后的判定引用，未修改任何生产代码、测试或构建配置（写租约：本文档与 compatibility-decision-record.md）。

### 9.1 运行时报告事件（run/logs/stackupup-conservation.jsonl，2026-08-09 03:20 落盘，schema v1）

| 事件号 | callSite | handlerClassName | slot | simulate | offered | storedDelta | remainderCount | balanced |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| #1 | DevAutomationServerDriver#probeTarget | net.minecraftforge.items.ItemStackHandler | 0 | false | 128 | 128 | 0 | true |
| #2 | Ae2ItemHandlerInsertLimiter#insertCapped | example.TruncatingThirdPartyHandler | 2 | false | 128 | 64 | 0 | false |
| #3 | DevAutomationServerDriver#probeTarget | net.minecraftforge.items.ItemStackHandler | 0 | true | 128 | 0 | 128 | true |

汇总（run/logs/stackupup-conservation-summary.json）：`totalEvents=3`、`simulateEvents=1`、`unbalancedRealEvents=1`、`unbalanced=[{handlerClassName:"example.TruncatingThirdPartyHandler",callSite:"Ae2ItemHandlerInsertLimiter#insertCapped",eventCount:1}]`，与 JSONL 逐条一致。

- 事件 #1/#3：offered=128 与 DevAutomationConfig 默认 count=128（src/main/kotlin/io/alexjoest/stackupup/dev/DevAutomationConfig.kt:91）一致，属开发自动验收探针投喂（`DevAutomationServerDriver#probeTarget`，DevAutomationServerDriver.kt:264-277）。
- 事件 #2：类名在项目源码/测试/dev 探针中无任何声明（`rg -n "example.TruncatingThirdPartyHandler" src dev` 无命中），`run/mods/` 与 `local-dev-mods/` 均为空，**归属 UNKNOWN（疑似探针合成名或外部合成类，决策记录 §3.7 同记）**。该事件不归属任何登记站点，不得据其升级任何 late/Forge 条目。

### 9.2 逐站点证据来源标注

| 站点/分组 | 证据来源 | 依据 |
| --- | --- | --- |
| §2.0 ItemMixin、ItemStackMixin | 源码 | 行号已关联（§2.0 表）；无对应 JSONL 事件 |
| §2.1 VanillaInventoryLimitMixin 12 类 + InventoryEnderChest 继承项 | 源码 | 行号已关联（§2.1 表；t2b §2 显式表）；探针只投喂 ItemStackHandler，无 vanilla 站点运行时事件 |
| §2.1 InventoryLargeChest（转发，出表） | 源码 | 行号已关联（§2.1 表；t2b §3.1） |
| §2.2 SlotLimitMixin | 源码 | 行号已关联 |
| §2.2 SlotItemHandlerMixin.getSlotStackLimit | 源码（T3 收缩） | 独立动态上限注入已移除（决策记录 §3.5）；当前仅 @Shadow 读取（SlotItemHandlerMixin.java:28-29） |
| §2.2 SlotItemHandlerMixin.getItemStackLimit | 源码（T3 保留） | 行号已刷新（SlotItemHandlerMixin.java:31-38） |
| §2.2 ContainerMixin | 源码 | 行号已关联 |
| §2.3 ItemStackHandler | 源码 + 运行时 | 源码（ItemStackHandler.java:88、:157-165 等）；运行时事件 #1（真实写 128 全量落库、余量 0，balanced）与 #3（simulate，仅记录不计守恒） |
| §2.3 EntityEquipmentInvWrapper | 源码（P0 事实 a） | 行号已关联；vanilla 实体 setter 已由 T3 闭合（§3.5）；无运行时事件 |
| §2.3 InvWrapper / SidedInvWrapper / CombinedInvWrapper / RangedWrapper | 源码（已移出注入） | T3 §3.5 移出 ForgeItemHandlerLimitMixin；InvWrapper/SidedInvWrapper 另由 T10 §3.6 移出 AE2 白名单；无运行时事件 |
| §2.4 InventoryPlayerAddResourceMixin 两调用点 | 源码 | 行号已关联（P0 事实 c，必须保留）；无运行时事件 |
| §2.5 CommandGive / CommandReplaceItem / EntityItemMerge / ServerRecipeBookHelper | 源码 | 行号已关联；无运行时事件 |
| §2.6 非容量站点 | 不标注 | 非容量广告/写入站点，不进入证据标注范围（§6 规则 8） |
| §3 late mixin（**当前 25 个 .java**，审计当时 18） | UNKNOWN（无源码） | 缺失 jar（§5）；JSONL 无事件对应任一登记目标类（#2 类名非任何登记目标，不得据其升级）；审计后新增 7 个（CDR §3.10/§3.11）同属无源码面 |
| §4 动态兼容层（profile/probe/patch/FixedCompatTargets，历史中间层，已删除） | 历史源码证据 | 删除前行号已关联（§4）；当前无运行时事件、无动态 ASM 注册，`StackUpUpCore.getASMTransformerClass()` 返回空数组 |

### 9.3 T3/T10 收缩后的判定引用刷新（2026-08-08）

- §2.3 标题「6 个 Forge 类」→ 2 个自洽目标（ItemStackHandler、EntityEquipmentInvWrapper），四个转发 wrapper 移出注入（决策记录 §3.5）；`ForgeItemHandlerLimitMixin.java:29-32` 刷新为 `:33-46`（`@Mixin` 列表 :33-39、handler :43-46）；`:45` 值检查为取值语义，非准入判据。
- §2.2 getSlotStackLimit 行：独立动态上限注入已移除（§3.5），仅保留 @Shadow；证据行号刷新为 SlotItemHandlerMixin.java:28-29。
- §2.3 EntityEquipmentInvWrapper 行：vanilla 实体 setter 由 T3 闭合（EntityLiving.java:1012-1022、EntityPlayer.java:2432-2449、EntityArmorStand.java:155-167，§3.5），不再标注「由 T3/T13.2 另行闭合」。
- §6 规则 7 所列哨兵行号（VanillaInventoryLimitMixin.java:47、ForgeItemHandlerLimitMixin.java:31、SlotItemHandlerMixin.java:26）为 T11/T3 前状态：T11 已删除 VanillaInventoryLimitMixin 哨兵（t2b §5）；T3 后 ForgeItemHandlerLimitMixin 的值检查是取值语义（§3.5）、SlotItemHandlerMixin 哨兵随独立注入移除。规则 7「不得再以哨兵作为准入判据」的口径不变，具体行号以本节与 t2b 为准。
- §3 四个 AE2 行（AppEngAdaptorItemHandlerMixin / AppEngInternalAEInventoryMixin / AppEngInternalInventoryMixin / AppEngPatternTermMixin）：按方案 A（决策记录 §3.8）与当前工作副本刷新——AppEngAdaptorItemHandlerMixin 已无任何注入（类体为空），AppEngInternalAEInventoryMixin / AppEngInternalInventoryMixin 各只保留 `<init>*` 的 `@ModifyConstant`（`getInventoryStackLimit` 注入已不存在），AppEngPatternTermMixin 保持 `<init>*` 的 `@Inject` + 反射提升；文件行号同步为 `:18-20`、`:10-16`、`:10-16`、`:24-55`。

### 9.4 2026-09 计数 lane 复核（配置/文件/模组数刷新）

- §3 标题文件数与 §5 缺失 jar 台账按当前工作副本刷新：late **26 个 .java 文件**（审计当时 18）、缺失模组 **16 个**（审计当时 12；新增 nuclearcraft、colossalchests、gregtech、techreborn）。§3 表补登审计后新增的 late mixin 行（ColossalChestsTile / EnderIOInventorySlotLimit / EnderIOInventoryNoDrop / GregTechMetaItem ×2 / NuclearCraftTileInventoryLimit / NuclearCraftDistributorNoDrop / RebornCoreInventory），来源 CDR §3.10/§3.11 与当前 Tech Reborn 兼容配置。
- §3 表内 early/late 计数不与 t14.1 §1 冲突：当前 17 个 `mixins.stackupup*.json`（early 1 + late 16），47 注册项 / 45 个 @Mixin 类；本表 §3 只登记容量/非容量站点，不重复配置数口径。
- 未改变任何分类判定：审计后新增 7 项中 5 项为容量站点、2 项（EnderIONoDrop、NCDistributorNoDrop）已在表中标注「非容量站点」；全部第三方条目维持 **无源码不可判定**（§5）。

## 10. 复核记录

- 作者（本登记表建立/重写代理）：只做只读取证 + 本文档写入；未修改任何生产代码、测试或构建配置。
- T13 回填（§9，2026-08-08 文档收口代理执行）：只做只读取证（JSONL/summary、决策记录 §3.5/§3.6、当前工作副本源码行号复核）+ 本文档与 compatibility-decision-record.md 写入；未修改任何生产代码、测试或构建配置。
- 2026-09 计数 lane：只做只读取证 + 本文档写入（§3 标题/表、§5、§9.2、§9.4）；未修改任何生产代码、测试或构建配置。
- 本文档重写原因：先前草稿引用 T4a 前状态（`VanillaInventoryWriteMixin`、`resolveInventoryWriteLimit`、旧 StackLimitHooks.kt 行号），与当前工作副本不符；本次按 T4a 后现状逐行复核重写。
- 独立复核：待指派（未完成独立复核前，本登记表不得宣称 PASS）。
