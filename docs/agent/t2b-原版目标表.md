# T2b 原版目标表（getInventoryStackLimit()I 编译期显式表）

> 任务：T2b+T11「原版目标表与登记护栏」（docs/agent/重构任务清单.md T2b+T11 节）。
> 目标：原版 `getInventoryStackLimit()I` 安全目标改为**编译期显式表**；删除运行时 `original == 64` 目标判断。
> 本文是原版目标的登记表：只登记事实与准入规则，不修改生产目标；`src/main/java/.../mixin/early/VanillaInventoryTargets.java`
> 与 `VanillaInventoryLimitMixin` 的 `@Mixin` 列表是机器可读实现，本文档与它们双向一致（登记护栏）。
> 证据基线：`build/rfg/minecraft-src/java/net/minecraft/` 为 Forge/vanilla 反编译源码（前缀简写 `.../net/minecraft`）；
> 所有行号 2026-08-08 用 `rg -n` 复核；生产代码路径使用 repo-relative 形式。

## 1. 全量枚举：`getInventoryStackLimit()I` 实现者（16 处 + 接口声明 1 处）

判定只依据真实写入路径源码，不得依据类名、`== 64` 哨兵或第三方主动表态（AGENTS.md「容量不变量」节；T2a §1）。

| 目标类 | 声明（file:line） | 返回逻辑 | setter 写入路径 | 处置 |
| --- | --- | --- | --- | --- |
| IInventory | `.../net/minecraft/inventory/IInventory.java:39` | 接口声明 | — | 非目标（接口） |
| TileEntityChest | `.../net/minecraft/tileentity/TileEntityChest.java:160-163` | 64 | 继承 `TileEntityLockableLoot#setInventorySlotContents`（:155-166）：落盘前重读 `this.getInventoryStackLimit()` 夹取 | 表内（自洽） |
| TileEntityDispenser | `.../net/minecraft/tileentity/TileEntityDispenser.java:158-161` | 64 | 继承 `TileEntityLockableLoot#setInventorySlotContents`（:155-166）夹取 | 表内（自洽） |
| TileEntityShulkerBox | `.../net/minecraft/tileentity/TileEntityShulkerBox.java:209-212` | 64 | 继承 `TileEntityLockableLoot#setInventorySlotContents`（:155-166）夹取；`getItems` 自有 :377 | 表内（自洽） |
| TileEntityFurnace | `.../net/minecraft/tileentity/TileEntityFurnace.java:220-223` | 64 | 自有 setter（:97-110）落盘前重读上限夹取 | 表内（自洽） |
| TileEntityHopper | `.../net/minecraft/tileentity/TileEntityHopper.java:154-157` | 64 | 自有 setter（:100-109）落盘前重读上限夹取 | 表内（自洽） |
| TileEntityBrewingStand | `.../net/minecraft/tileentity/TileEntityBrewingStand.java:348-351` | 64 | 自有 setter（:330-336）**朴素写入不夹取**（`brewingItemStacks.set` 后无上限判定） | 表内（写入无夹取 = 不截断，守恒恒成立；T2a §2.1 的「落盘路径同 TileEntityLockableLoot 语义」表述不准确，以本行源码为准） |
| EntityMinecartContainer | `.../net/minecraft/entity/item/EntityMinecartContainer.java:165-168` | 64 | 自有 setter（:111-119）落盘前重读上限夹取；`markDirty` 空实现 | 表内（自洽） |
| InventoryPlayer | `.../net/minecraft/entity/player/InventoryPlayer.java:864-867` | 64 | `addResource` 落盘前重读上限夹取（:346-348）；`canMergeStacks` 以同一上限门控合并（:67-69）；setter（:610-）为朴素写入 | 表内（自洽，两条主写入路径消费同一来源；A5 clamp 回调见 T2a §2.4） |
| InventoryBasic | `.../net/minecraft/inventory/InventoryBasic.java:273-276` | 64 | 自有 setter（:143-149）落盘前重读上限夹取；`insertItem` 合并上限同源（:97 `min(getInventoryStackLimit, maxStackSize)`） | 表内（自洽；子类 InventoryEnderChest 经继承获得扩展） |
| InventoryMerchant | `.../net/minecraft/inventory/InventoryMerchant.java:202-205` | 64 | 自有 setter（:99-106）落盘前重读上限夹取；槽 0/1 变更触发 `resetRecipeAndSlots`（:241-） | 表内（自洽） |
| InventoryCrafting | `.../net/minecraft/inventory/InventoryCrafting.java:185-188` | 64 | 自有 setter（:176-180）**朴素写入不夹取**；常规写入量经合并路径（ContainerMixin 收紧）先行受限 | 表内（写入无夹取 = 不截断；直接 putStack 旁路不读取该值属原版语义残余风险，T2a 同记） |
| InventoryCraftResult | `.../net/minecraft/inventory/InventoryCraftResult.java:158-161` | 64 | 自有 setter（:150-154）**朴素写入不夹取**；结果堆叠量受配方结果上限约束 | 表内（写入量不依赖该广告值；广告值被 slot/合并链消费） |
| InventoryLargeChest | `.../net/minecraft/inventory/InventoryLargeChest.java:203-206` | **转发上箱** `upperChest.getInventoryStackLimit()` | setter 按 index 分发两半箱各自 setter（:188-196）；两半各自 clamp | **不在表**（转发；两半上限不一致时广告=上箱与下箱写入夹取脱节 = 条件断链，见 §3.1） |
| TileEntityBeacon | `.../net/minecraft/tileentity/TileEntityBeacon.java:459-462` | **1** | 自有 setter（:384-390）只写 `payment` 槽（index==0），无夹取 | **不在表**（主动收紧类，见 §3.2） |
| （匿名子类）ContainerEnchantment 的 tableInventory | `.../net/minecraft/inventory/ContainerEnchantment.java:52-55` | 64（override） | 继承 `InventoryBasic#setInventorySlotContents`（:143-149），虚分派到本 override | **不在表**（override 旁路基类 patch；自洽于 64，见 §3.3） |
| TileEntityLockableLoot | （抽象类，不实现该方法） | — | setter（:155-166）是 TileEntityChest/Dispenser/ShulkerBox 的公共写入面；`getItems` 抽象（:220） | 非目标（抽象类本身无 `getInventoryStackLimit` 声明） |

非实现者（同名不同方法或仅使用，不登记）：`Slot#getSlotStackLimit` 转发 `inventory.getInventoryStackLimit()`（`.../net/minecraft/inventory/Slot.java:115`）；
`Container#calcRedstone` 内使用（`.../net/minecraft/inventory/Container.java:848`）；`getSlotStackLimit` 系列 override
（ContainerBeacon/ContainerBrewingStand/ContainerHorseInventory/ContainerPlayer/GuiContainerCreative，方法与目标方法不同名）。

## 2. 编译期显式表（12 项）

表内目标 = 编译期 `@Mixin` 列表 = `VanillaInventoryTargets.TARGETS`（`src/main/java/io/alexjoest/stackupup/mixin/early/VanillaInventoryTargets.java`），
三者必须双向一致（登记护栏测试 `VanillaInventoryTargetsGuardTest`）。目标方法完整 descriptor 一律
`getInventoryStackLimit()I`（无参变体；与 `getSlotStackLimit()I`、`getItemStackLimit(...)I` 区分）。

| 目标类（全限定名，护栏测试按此格式解析本文档） | 原生返回值 | 分类（T2a 三分类） |
| --- | --- | --- |
| net.minecraft.tileentity.TileEntityDispenser | 64 | 自洽 |
| net.minecraft.tileentity.TileEntityChest | 64 | 自洽 |
| net.minecraft.tileentity.TileEntityFurnace | 64 | 自洽 |
| net.minecraft.tileentity.TileEntityBrewingStand | 64 | 自洽（写入无夹取，不截断） |
| net.minecraft.tileentity.TileEntityHopper | 64 | 自洽 |
| net.minecraft.tileentity.TileEntityShulkerBox | 64 | 自洽 |
| net.minecraft.entity.item.EntityMinecartContainer | 64 | 自洽 |
| net.minecraft.entity.player.InventoryPlayer | 64 | 自洽 |
| net.minecraft.inventory.InventoryBasic | 64 | 自洽 |
| net.minecraft.inventory.InventoryMerchant | 64 | 自洽 |
| net.minecraft.inventory.InventoryCrafting | 64 | 自洽（写入无夹取，不截断） |
| net.minecraft.inventory.InventoryCraftResult | 64 | 自洽（写入无夹取，不截断） |

与 T2a §2.1 的差异：T2a 标题「14 个 vanilla 类」指 `FixedCompatTargets` 原版 IInventory 跳过表 14 项（含
InventoryEnderChest 继承项与 InventoryLargeChest 转发项）；early mixin `@Mixin` 列表由 13 类降为 12 类——
**InventoryLargeChest 按 T2a §2.1 预告（"由 T2b+T11 处置"）移出**。`FixedCompatTargets` 与 `DynamicCompatTargetProfile`
不在本任务租约内，未改动。

## 3. 排除类与缺口（明确非吞物 bug）

### 3.1 InventoryLargeChest：转发，不在表

`getInventoryStackLimit()` 转发 `upperChest`（`InventoryLargeChest.java:203-206`）；写入按 index 分发两半箱
（:188-196）。两半均为 TileEntityChest 时广告与写入同源（自洽）；两半上限不一致时（模拟抬高一半），
广告 = 上箱上限，下箱写入夹取 = 下箱上限，二者脱节（条件断链）。因此该包装器**不是可安全抬高的独立容量来源**，
不得入表。实际游戏中两半恒为同类 TileEntityChest，断链不出现；测试以「上箱抬高 + 下箱原值」构造证明断链并证明不在表。
`VanillaDoubleChestItemHandler#getSlotLimit` 同样转发箱体（Forge 源码 `VanillaDoubleChestItemHandler.java:184-187`），
底层两半由表内目标覆盖。

### 3.2 TileEntityBeacon：主动收紧，不在表

`getInventoryStackLimit()` 返回 1（`TileEntityBeacon.java:459-462`）——**主动收紧类**，抬高其广告违反
「对外广告容量不得大于真实写入容量」方向（此处是收紧，仍不得入表：广告=1 且写入无夹取，自洽）。setter（:384-390）
只写 `payment` 槽且无夹取。**这不是吞物 bug**：写入不夹取即不截断，广告与写入都在 1 槽语义内自洽。

### 3.3 ContainerEnchantment 匿名子类：override 旁路，不在表

`ContainerEnchantment` 的 `tableInventory` 是 `InventoryBasic` 匿名子类，override `getInventoryStackLimit()` 返回 64
（`ContainerEnchantment.java:52-55`）。Mixin 按类应用，匿名子类（`ContainerEnchantment$1`）**不在**
`VanillaInventoryLimitMixin` 目标内；其继承的 `InventoryBasic#setInventorySlotContents` 虚分派到本 override——
**广告（Slot → 本 override = 64）与写入夹取（本 override = 64）同源自洽于 64**，因此不是吞物 bug，也不在表。
已知缺口：若未来删除该 override，基类 patch 将立即对 enchant 表生效（届时需按 §6 重新登记）。

## 4. 守恒测试规格（T2a §6 规则 6 落地）

测试 `VanillaInventoryConservationTest`（模拟态，结构说明见各方法注释；行为测试调用真实 vanilla 写入代码）：

1. **模拟抬高**：以匿名子类 override `getInventoryStackLimit()` 返回抬高值 `RAISED_LIMIT = 512`
   （模拟运行时 mixin 返回 `getCompatibilityStackSize()` 的效果）；物品上限 `OFFERED = 10240`
   （`object : Item() { override fun getItemStackLimit(stack) = 10240 }`，模拟 ItemMixin 抬高物品层）。
2. **表内目标逐一覆盖**：TileEntityChest、TileEntityDispenser、TileEntityFurnace、TileEntityHopper、
   TileEntityShulkerBox、TileEntityBrewingStand、EntityMinecartChest（EntityMinecartContainer 抽象，
   以其具体子类为行为代理；`EntityMinecartContainer` 本身是 mixin 目标，patch 经继承生效）、InventoryPlayer
   （`addItemStackToInventory` 路径）、InventoryBasic、InventoryMerchant（写槽 2 避开 `resetRecipeAndSlots`）、
   InventoryCrafting（`TestContainer` 为 eventHandler）、InventoryCraftResult。
3. **夹取类断言**（chest/dispenser/furnace/hopper/shulker/minecart/basic/merchant/player）：
   `stored == min(offered, limit)` 且 `stored + remainder == offered`（`remainder = offered - stored` 为只读
   审计公式，非写入后补偿），即写入夹取恰好等于广告上限，无额外丢失；同时做原值基线
   （无 override，limit=64）对照 `stored == 64`，证明夹取跟随上限值而非写死 64。
4. **无夹取类断言**（brewing/crafting/craftResult）：`stored == offered`，证明写入不截断（不产生吞物）。
5. 不变量红线：不删除 `resolveInventoryClampLimit` 两个调用方（T2a §2.4，本任务未触碰）。

## 5. 登记护栏测试规格（T2a §6 规则 1/2 落地）

测试 `VanillaInventoryTargetsGuardTest`（结构检查，非行为验证；读取源码与本文档做字符串级比对）：

1. `VanillaInventoryTargets.TARGETS`（机器可读登记）与 `VanillaInventoryLimitMixin` 的 `@Mixin` 列表**双向一致**
   （实现中存在但表内缺失 = 未登记目标 = 失败；表内存在但实现缺失 = 登记悬空 = 失败）。
2. 本文档 §2 目标表（每行以 `| net.minecraft.<全限定类名> |` 开头，全文档仅该表如此）与 `TARGETS` **双向一致**，
   且数量恰为 12。
3. 排除断言：`InventoryLargeChest`、`TileEntityBeacon` 不在 `TARGETS`，且不在 mixin 源中；`EXCLUDED` 常量表包含两者。
4. 哨兵删除断言：mixin 源不再包含 `original == 64` 与 `VANILLA_STACK_LIMIT`，且引用 `VanillaInventoryTargets`。
5. 目标方法 descriptor 断言：mixin 源 `method = VanillaInventoryTargets.METHOD_DESCRIPTOR`（`getInventoryStackLimit()I`）。

## 6. 与 T2a 的比对规则

1. 本表 §2 的 12 项与 T2a §2.1 的 early mixin 目标（13 类，即旧 `@Mixin` 列表）一一对应，唯一差异为
   InventoryLargeChest（转发）出表；该出表是 T2a §2.1 已预告的 T2b+T11 处置结果，不是新分类变更。
2. T2a §6 规则 1（未登记目标失败）与规则 2（登记表与实现双向一致）由 §5 护栏测试落地；新增/改动 early
   原版目标必须同步三处：本文档 §2、`VanillaInventoryTargets`、`@Mixin` 列表，缺一即护栏失败。
3. T2a §6 规则 6（守恒底线）由 §4 测试落地；规则 7（`== 64` 哨兵待替换）对 `VanillaInventoryLimitMixin` 已落地
   （哨兵删除）；ForgeItemHandlerLimitMixin/SlotItemHandlerMixin 及 late 侧哨兵不在本任务租约。
4. 例外（T2a §6 规则 8）：非容量站点不受护栏约束，本文不涉及。
5. 本表行号与 T2a §2.1 不一致处（如 TileEntityBrewingStand setter 语义）以本文档为准（§1 源码行号 2026-08-08 复核）。

## 7. 剩余风险

- `@ModifyReturnValue(require = 0)` 保持原值：若目标类方法名解析失败，Mixin 静默跳过（原实现即如此）；
  表内目标的方法存在性由本表 §1 反编译源码证据 + `runServerAutoTest` 运行时验证覆盖，未改 require 语义。
- `InventoryCrafting`/`InventoryCraftResult`/`TileEntityBrewingStand` 的朴素写入旁路（直接 putStack 不读取上限）
  是原版语义残余风险（T2a 同记），表内保留基于「写入无夹取 = 不截断」；若未来出现截断性写入面须重新分类。
- ContainerEnchantment 匿名子类 override 若被上游删除，基类 patch 立即生效（§3.3），属已知缺口非吞物 bug。
- 动态层 `FixedCompatTargets`（含 InventoryLargeChest 跳过项）与本表的关系未在本任务改动，后续 T 任务如需联动须重新核对。

## 8. 复核记录

- 作者（实现代理）：只读取证 + 本文档/`VanillaInventoryTargets`/`VanillaInventoryLimitMixin`/新增测试写入；
  未修改 `resolveInventoryClampLimit` 及其调用方、DSL、`FixedCompatTargets`、late mixin。
- 独立复核：待指派（未完成独立复核前，本文档不得宣称 PASS）。
