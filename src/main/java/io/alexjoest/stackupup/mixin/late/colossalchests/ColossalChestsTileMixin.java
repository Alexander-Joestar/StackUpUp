package io.alexjoest.stackupup.mixin.late.colossalchests;

import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Colossal Chests 1.12.2（1.7.3，mods-under-test/master-1.12 的 origin/master-1.12 分支）库存栈上限
 * 字段级源头修正。
 *
 * `TileColossalChest.constructInventory()`（:232-238）与 `constructInventoryDebug()`（:240-248）共 4 处
 * `new IndexedInventory/LargeInventory(size, name, 64)` 的第三参即栈上限（:236/:237/:241/:242），
 * 是 TE 库存容量的唯一构造来源：`getInventory()`（:422-433）在 inventory 为 null 或尺寸变化时懒重建，
 * NBT 持久化不存上限字段（readFromNBT :267-284 只处理客户端跳过），`setSize` 迁移（:186-200）走
 * `setInventorySlotContents` 夹取（上限变大后无截断）——全部路径最终都经过本构造点，一个 handler 覆盖
 * 4 处字面量即收敛广告值。`:211` 的 `new LargeInventory(0, "invalid", 0)` 是 0 上限占位，不在范围
 * （intValue=0 不匹配 64）。
 *
 * 与既有 `SimpleInventoryMixin`（cyclopscore 配置，使用期 clamp）并存一致：前者是使用期夹取，
 * 这里是字段级源头修正；容量不变量（对外广告 ≤ 真实写入容量）保持。
 *
 * 只替换上限广告值，不触碰写入副作用：setSize 迁移截断、IndexedInventory.createIndex（:138-162 无栈校验）、
 * onInventoryChanged/hash、NBT 持久化均保持原语义。@Pseudo + require=0：mod 缺失或目标方法不在时
 * 静默跳过（可选目标，失败方向安全）。
 */
@Pseudo
@Mixin(targets = "org.cyclops.colossalchests.tileentity.TileColossalChest", remap = false)
abstract class ColossalChestsTileMixin {
    @ModifyConstant(
        method = {"constructInventory", "constructInventoryDebug"},
        constant = @Constant(intValue = 64),
        require = 0,
        remap = false
    )
    private int stackupup$expandInventoryStackLimit(int original) {
        // 两方法内 64 字面量全部是栈上限第三参（源码逐行核对），无条件替换为兼容堆叠上限。
        return StackLimitHooks.getCompatibilityStackSize();
    }
}
