package io.alexjoest.stackupup.mixin.late.enderio;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * EnderIO legacy 机器溢出掉落移除（用户明确：不允许掉落、塞不下就不塞、不吞）。
 *
 * 目标方法 `AbstractInventoryMachineEntity.setInventorySlotContents(ILnet/minecraft/item/ItemStack;)V`
 * （mods-under-test/EnderIO-1.5-1.12/.../baselegacy/AbstractInventoryMachineEntity.java:183-190，
 * EnderIO-CEu 同文件 :182-190，两版签名一致）：
 * <pre>
 *   inventory[slot] = contents.copy();
 *   if (inventory[slot].getCount() > getInventoryStackLimit(slot)) {
 *     inventory[slot].setCount(getInventoryStackLimit(slot));  // 槽内夹取到上限：写入面既有行为，保留
 *     contents.shrink(getInventoryStackLimit(slot));           // 本 mixin 使其不生效：剩余物留在调用方传入栈
 *     Block.spawnAsEntity(world, pos, contents);               // 本 mixin 使其不生效：不掉落
 *   }
 *   markDirty();
 * </pre>
 *
 * 语义：槽内仍夹取到上限（不动 setCount 夹取分支）；`contents.shrink` 与 `spawnAsEntity` 被替换为 no-op——
 * 剩余物既不从调用方传入栈中消失（不吞），也不掉落（不掉落），由调用方继续持有。对 EnderIO 内部路径
 * （LegacyMachineWrapper.doInsertItem 已夹取并返回 remainder）该分支本不可达；对第三方直调 IInventory
 * setter 的路径则把"是否继续处理剩余物"的决定权留在调用方栈上，符合"塞不下就不塞"。
 *
 * 注入器选择：两个动作都是"保留原调用上下文但抑制执行"，按项目决策（compatibility-decision-record.md §8.3）
 * 不引入 Redirect 类注入器，使用 @WrapOperation 且刻意不调用 original（抑制语义，非链式吞调用）；目标为明确可选
 * 目标（EnderIO mod gate + @Pseudo + require=0 结构化缺失诊断）。失败方向安全：注入不生效时维持 EnderIO
 * 原掉落行为，不会引入新的物品丢失。两个调用点在方法内各仅出现一次（:187/:188），无需 ordinal。
 */
@Pseudo
@Mixin(targets = "crazypants.enderio.base.machine.baselegacy.AbstractInventoryMachineEntity", remap = false)
abstract class EnderIOInventoryNoDropMixin {

    @WrapOperation(
        method = "setInventorySlotContents(ILnet/minecraft/item/ItemStack;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;shrink(I)V"),
        require = 0
    )
    private void stackupup$keepRemainderInCallerStack(ItemStack instance, int amount, Operation<Void> original) {
        // 抑制 contents.shrink：剩余物留在调用方传入栈中（不吞、不掉落）。
    }

    @WrapOperation(
        method = "setInventorySlotContents(ILnet/minecraft/item/ItemStack;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;spawnAsEntity(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/item/ItemStack;)V"
        ),
        require = 0
    )
    private void stackupup$noOverflowDrop(World world, BlockPos pos, ItemStack contents, Operation<Void> original) {
        // 抑制 Block.spawnAsEntity：溢出部分不掉落为掉落物实体。
    }
}
