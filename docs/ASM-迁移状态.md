# ASM 迁移状态

## 当前原则

当前迁移遵循四条原则：

1. 规则内核优先统一到 `ItemStack + metadata + OreDictionary`
2. 固定目标优先迁到 `MixinBooter + Mixin`
3. 真动态目标暂时保留最小 ASM
4. 所有迁移以 `runServerAutoTestMatrix` 为主回归入口

## 已迁到 Mixin 的固定目标

### 原版与通用路径

1. `Item#getItemStackLimit(ItemStack)`
2. `ItemStack#getMaxStackSize()`
3. `CommandGive`
4. `CommandReplaceItem`
5. `PacketBuffer`
6. `PacketUtil`
7. `NetHandlerPlayServer`
8. `InventoryHelper`
9. `ServerRecipeBookHelper`

### late mixin 固定目标

1. `slimeknights.mantle.tileentity.TileInventory`
2. `ic2.core.block.invslot.InvSlot`
3. `appeng.tile.inventory.AppEngInternalInventory`
4. `appeng.tile.inventory.AppEngInternalAEInventory`
5. `de.ellpeck.actuallyadditions.mod.tile.TileEntityInventoryBase`
6. `com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandler`
7. `com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandlerPortable`
8. `com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeStorageMonitor`
9. `org.cyclops.cyclopscore.inventory.SimpleInventory`

## 当前保留的 ASM 边界

1. 动态发现的 `IInventory`
2. 动态发现的 `IItemHandler`
3. 动态发现的 `Slot`
4. 少量底层协议 / 序列化补丁

原因：

1. 目标集合只能在运行时确定
2. 静态列举 mixin 漏网风险更高
3. 当前动态层已经通过方法声明探测尽量减少误伤

## 当前收口结果

1. `DynamicCompatTransformer` 已收敛为 adapter
2. 动态补丁入口统一收口到 `CompatibilityLimitPatch.planFor(...)`
3. `FixedCompatTargets` 改为单一事实源
4. `ClassHierarchyRepository` 只保留父类与接口查询
5. `DynamicCompatMethodProbe` 保持单次 profile-aware 扫描

## 已清理的旧 ASM 文件

以下旧文件已被 mixin 取代并移除：

1. `NetHandlerPlayServerPatch.kt`
2. `PacketBufferWriterSplice.kt`
3. `PacketUtilWriterSplice.kt`
4. `RenderEntityItemPatch.kt`
5. `RenderEntityItemSplice.kt`
6. `RenderItemPatch.kt`

## 当前主回归入口

```bash
./gradlew runServerAutoTestMatrix
```

当前重点样例：

1. `gregtech:meta_ingot@324`
2. `gregtech:meta_plate@324`
3. `gregtech:meta_dust@324`
4. `gregtech:meta_item_1@516`

## 关键护栏

1. `DynamicCompatEarlyPathBytecodeTest`
2. `DynamicCompatTargetClassifierTest`
3. `CompatibilityLimitPatchTest`
4. `DynamicCompatTransformerTest`
5. `EarlyMixinBytecodeSafetyTest`
6. `MixinBooterIntegrationTest`
7. `ItemStackPatchTest`
8. `MaxStackConstantPatchTest`

## 当前判断

1. 固定目标继续优先迁到 mixin
2. 动态边界暂不为“零 ASM”而强行重写
3. 继续收缩时必须以回归和字节码安全测试为前提
