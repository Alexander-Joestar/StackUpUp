package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.entity.item.EntityMinecartContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.InventoryMerchant;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.tileentity.TileEntityShulkerBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 原版 {@code getInventoryStackLimit()I} 安全目标：目标集合是编译期显式表
 * （{@code @Mixin} 列表 = {@link VanillaInventoryTargets#TARGETS}，登记表 docs/agent/t2b-原版目标表.md §2），
 * 不再在运行时按原值是否为 64 判断目标是否可 patch（哨兵判断已删除，见 t2b §6.3）。
 * 表内目标全部原生返回 64 且写入面自洽（setter 落盘前重读同一上限夹取，或无夹取不截断），
 * 因此替换返回值不需要再检查原值。表外目标（条件断链的转发箱、主动收紧类等）不在此列表，
 * 排除原因见 t2b §3。
 */
@Mixin({
    TileEntityDispenser.class,
    TileEntityChest.class,
    TileEntityFurnace.class,
    TileEntityBrewingStand.class,
    TileEntityHopper.class,
    TileEntityShulkerBox.class,
    EntityMinecartContainer.class,
    InventoryPlayer.class,
    InventoryBasic.class,
    InventoryMerchant.class,
    InventoryCrafting.class,
    InventoryCraftResult.class
})
abstract class VanillaInventoryLimitMixin {
    @ModifyReturnValue(
        method = VanillaInventoryTargets.METHOD_DESCRIPTOR,
        at = @At("RETURN"),
        require = 0
    )
    private int stackupup$replaceCompatibilityLimit(int original) {
        return StackLimitHooks.getCompatibilityStackSize();
    }
}
