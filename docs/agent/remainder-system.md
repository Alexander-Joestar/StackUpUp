# Remainder 系统

大堆叠环境下，容器写入截断与余量恢复机制的技术说明。

## 问题背景

Mod 的 `Slot.putStack()` → `IInventory.setInventorySlotContents()` 实现在写入时，经常把超过原版 64 上限的数量**静默截断**。被截断的多余数量既不写入槽位、也不返还给调用方，导致吞物品。

典型触发路径：

1. 玩家光标持有 200 个物品，点击空槽
2. Vanilla `slotClick` 调用 `slot.putStack(cursorStack)`
3. Mod 的 `setInventorySlotContents` 收到 stack.count=200，内部 clamp 到 64
4. 多余 136 个物品凭空消失

## 解决方案架构

```
slotClick / mergeItemStack / SWAP
        │
        ▼
  Slot.putStack(stack)           ← @WrapOperation 拦截 (11 ordinals in slotClick + 1 in mergeItemStack)
        │
        ▼
  setInventorySlotContents(clamp) ← 模组侧自选动作
        │
        ▼
  ContainerInsertHooks.remainderAfterPut()
        │  ├ 读取 slot.stack（实际 = inventory.getStackInSlot）
        │  └ 对齐当前库存，区分"同类截断"和"被完全替换"
        │
        ▼
  remainder > 0?
      ├ → PICKUP: 直接返回到光标
      ├ → QUICK_CRAFT: ThreadLocal → setItemStack 合并
      ├ → SWAP: ThreadLocal → setItemStack 合并
      └ → mergeItemStack: sourceStack.grow(remainder)
```

## 核心文件

| 文件 | 职责 |
|------|------|
| `ContainerMixin.java` | 11 个 ordinals 覆盖 slotClick 全部 putStack + 1 个 mergeItemStack putStack |
| `ContainerInsertHooks.kt` | 余量计算——`remainderAfterPut` / `remainderCountAfterEmptyPut` |
| `RemainderGuard.kt` | 运行时守卫，测试中关闭 remainder |
| `ClientSlotSyncHooks.kt` | 客户端容器显示副本恢复 |
| `NetHandlerPlayClientMixin.java` | 客户端网络包处理后的显示修复 |

## 注入点详解（已覆盖 slotClick 全部 11 个 putStack 调用）

### mergeItemStack

| Ordinal | 方法 | 用途 |
|---------|------|------|
| unique | `restoreRemainderAfterMergePut` | 包裹 mergeItemStack 内的 putStack，余量回填到 sourceStack |

### slotClick — QUICK_CRAFT（拖动）

| Ordinal | 方法 | 行为 |
|---------|------|------|
| 0 | `restoreRemainderAfterClickPut_0` | 拖动循环内的 putStack；余量 → `pendingDragRemainder` |

恢复点：`setItemStack` ordinal 0 — 同时合并 QUICK_CRAFT 和 SWAP 余量到光标。

### slotClick — PICKUP（普通点击）

| Ordinal | 方法 | 行为 |
|---------|------|------|
| 1 | `restoreRemainderAfterClickPut_1` | 空槽写入 splitStack；余量直接恢复到光标 |
| 2 | `restoreRemainderAfterClickPut_2` | putStack(EMPTY)，忽略 |
| 3 | `restoreRemainderAfterClickPut_3` | decrStackSize 后 putStack(EMPTY)，忽略 |
| 4 | `restoreRemainderAfterClickPut_4` | 光标物品替换槽位物品（SWAP 语义）；余量 → `pendingSwapRemainder` |
| 5 | `restoreRemainderAfterClickPut_5` | decrStackSize 清空后 putStack(EMPTY)，忽略 |

### slotClick — SWAP (hotbar)

| Ordinal | 方法 | 行为 |
|---------|------|------|
| 6 | `restoreRemainderAfterClickPut_6` | putStack(EMPTY)，忽略 |
| 7 | `restoreRemainderAfterClickPut_7` | splitStack 后写入；余量 → `pendingSwapRemainder` |
| 8 | `restoreRemainderAfterClickPut_8` | 完整物品写入；余量 → `pendingSwapRemainder` |
| 9 | `restoreRemainderAfterClickPut_9` | splitStack 后写入（有物品）；余量 → `pendingSwapRemainder` |
| 10 | `restoreRemainderAfterClickPut_10` | 完整物品写入（有物品）；余量 → `pendingSwapRemainder` |

### 通用保护

| 方法 | 用途 |
|------|------|
| `limitOutsideDropToDefaultSize` | 光标超过 64 时只 drop 64 个，剩余留在光标 |
| `delayCursorShrinkUntilSlotGrowth` | 延迟光标 shrink，等槽位 grow 确认实际接受数量后执行 |
| `applyDragRemainderToCursor` | setItemStack ordinal 0 — 合并 pendingDragRemainder + pendingSwapRemainder |

## 余量计算

`ContainerInsertHooks` 提供两层计算：

| 方法 | 用途 |
|------|------|
| `remainderCountAfterEmptyPut` | 兼容旧接口，只对"原空槽"做单一检测 |
| `remainderAfterPut` | 通用检测：无论槽位之前是否为空，按写入后实际库存反推被截数量 |

核心逻辑：

1. 读取 `slot.stack`（= inventory.getStackInSlot）获取写入后真实状态
2. 若槽位为空 → 全部未接受（remainder = attemptedCount）
3. 若槽位有同类物品 + NBT 一致 → accepted = min(storedCount, attemptedCount)
4. 若槽位有不同物品 → 说明被完全替换，未接受原物品（remainder = attemptedCount）

## 客户端显示副本修复

`NetHandlerPlayClientMixin` 使用 `ClientSlotSyncHooks` 修复客户端显示：

- `handleSetSlot` → `container.putStackInSlot()` 后 → `restoreContainerSlotStackCount()`
- `handleWindowItems` → `container.setAll()` 后 → `restoreContainerSlotStackCounts()`

逻辑：

1. 若客户端槽位显示副本为空，但服务端同步包携带了非空大堆叠，则直接按传入栈重建本地槽位显示副本。
2. 若客户端槽位已有同类物品、但数量低于服务端传入的 `transmittedCount`，则直接把本地 `slot.stack.count` 补回去。

这解决了两类常见问题：

1. 某些 Mod 的客户端容器副本在 `putStackInSlot` / `setAll` 后被错误清空；
2. 某些 Mod 的客户端副本仍保留物品类型，但数量被自行截断到 64。

## 模组侧兼容——late mixin

对于已知在 `setInventorySlotContents` 中截断的 Mod，通过 late mixin 扩展其 `getInventoryStackLimit()` 返回值，从源头避免截断：

| Mixin | 目标类 |
|-------|--------|
| `BrandonsCoreInventoryLimitMixin` | `BrandonsCore TileInventoryBase` |
| `EnderIOMachineInventoryLimitMixin` | `AbstractInventoryMachineEntity`, `TileEnchanter` |
| `EnderIOSlottedInventoryLimitMixin` | `TileSoulBinder`, `TileSliceAndSplice`, `TileFarmStation` |
| `TileInventoryMixin` | `Mantle TileInventory` |
| `SimpleInventoryMixin` | `CyclopsCore SimpleInventory` |
| `AppEngInternalInventoryMixin` | `AE2 AppEngInternalInventory` |
| `AppEngInternalAEInventoryMixin` | `AE2 AppEngInternalAEInventory` |

这些 late mixin 通过 `@ModifyReturnValue` 把 `getInventoryStackLimit()` 的返回从 64 提升到 `StackLimitHooks.getCompatibilityStackSize()`（全局兼容上限）。

Ender IO 的普通机器类同时存在 `getInventoryStackLimit()` 与 `getInventoryStackLimit(int)`，mixin 必须使用 `getInventoryStackLimit()I` 这类完整 descriptor，避免 overloaded selector 在 late 阶段静默漏打。对应源码测试会锁住这一点。

### 兼容性开关

每个 mod 的 late mixin 都可以通过 `config/stackupup.cfg` → `compatibility` 分类下的布尔开关独立启停。

```hocon
compatibility {
    B:ae2=true
    B:brandonsCore=true
    B:actuallyAdditions=true
    B:cyclopsCore=true
    B:enderIo=true
    B:ic2=true
    B:mantle=true
    B:refinedStorage=true
}
```

用途：当排查某个 mod 的兼容性问题时，可以在配置里单独关闭对应补丁，而无须移除 mixin 文件。所有开关默认启用，修改后需重启 Minecraft（`@Config.RequiresMcRestart`）。

`ConfigFileSanitizer` 和 `EarlyCompatConfigReader` 都对 `stackupup.cfg` 使用纯文本顶层解析。前者保留 `general`、`client`、`compatibility`，移除旧版本遗留的未知根分类；后者只读取 `compatibility` 段里的 `B:` 布尔开关，并缓存第一次读取结果。扫描器会忽略 BOM、行尾 `#` / `//` 注释，并在统计花括号时跳过引号内内容，同时尽量保留原始换行风格。这样不会在早期启动或单元测试环境中依赖 Forge `Configuration(File)` 的 `FMLInjectionData`。

## RemainderGuard

用于测试隔离：

```kotlin
RemainderGuard.withoutRemainder {
    slot.putStack(attemptedStack)
    // 这里不会被 remainder 干涉
}
```

| 测试 | 覆盖范围 |
|------|---------|
| `ContainerInsertHooksTest` | 余量计算各边界条件 |
| `ClientSlotSyncHooksTest` | 客户端同步恢复的守卫逻辑 |
| `RemainderGuardTest` | 守卫的开关与嵌套行为 |

## 已知限制

1. `InventoryBasic.setInventorySlotContents` 内部自带 `this.getInventoryStackLimit()` clamp，因此用 `InventoryBasic` 作为测试背板时，最大实测数量上限为 64。
2. 余量恢复仅在 `Slot.putStack` 被正确拦截时有效；若模组覆写了整个 `Container.slotClick` 或自定义了内部流程，余量无法被检测。
3. 客户端显示副本修复优先处理 `SPacketSetSlot` / `SPacketWindowItems` 后的容器副本异常，但不替代服务端真实库存修复；若 tile 实体自身在 `getStackInSlot` 内再次做动态截断，仍需对应 mod 的 late mixin 扩展库存上限。
4. 11 个 ordinal 覆盖依赖于 vanilla `Container.slotClick` 的字节码布局；若 Minecraft 版本升级或 Java 编译器改变 `putStack` 出现次序，需重新核对 ordinals。
5. `remainderAfterPut` 在热路径上先做“尝试数量是否超过当前槽位与库存真实上限”的快速路径判断，低于上限时直接返回 0，不做 NBT 比对。
6. `ContainerMixin` 在 `slotClick` 的 HEAD 和 RETURN 都会清理 ThreadLocal 状态，避免异常路径或跨点击残留影响下一次交互。
7. 这个快速路径是 remainder 体系的主要性能保障，不能删成“总是进完整比较”。
8. 若模组库存写入时把传入参数栈变成“剩余数量”（例如自己丢出/保留溢出物），`remainderAfterPut` 会认为溢出已由库存侧处理，不再二次回填，避免 EIO 这类路径发生重复物品。

## 槽位上限与库存容量的关系

核心原则：**动态槽位上限不应超过库存的实际承受能力。**

`SlotLimitMixin` 在计算 `getItemStackLimit` 时，最终返回值取 `min(dynamicLimit, inventory.getInventoryStackLimit())`。
这保证了两点：

1. 若库存已被 late mixin 扩展到 255（如 BrandonsCore、EIO），槽位上限跟随扩展到 255，玩家可单次放入大堆叠。
2. 若库存仍为原版 64（late mixin 未装载或被关闭），槽位上限保持 64，vanilla 不会过量投入，remainder 也无需介入。

同理，`SlotItemHandlerMixin.getSlotStackLimit` 仅在 handler 侧的实际 limit > 64 时才提升上报值。
若 handler 仍返回 64，槽位向 vanilla 如实报告 64，避免 handler 截断/丢弃与 remainder 恢复形成冲突。
> **已过时**
