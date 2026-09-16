package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import io.alexjoest.stackupup.StackUpUpConfig;
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
 *
 * <p>例外（条件上限）：{@link InventoryCrafting} 与 {@link InventoryCraftResult} 是合成容器
 * （工作台网格 + 合成结果槽），大堆叠下 shift 合成会一次搬运极大量物品，导致卡顿与误操作。
 * 这两个类的 {@code getInventoryStackLimit()} 不改用全局兼容上限，而是取
 * {@code min(全局兼容上限, 配置项 general.craftingSlotLimit)}（即用户要求的
 * "size > 配置 ? 配置 : size" 语义）：默认 64 与原版一致，但会把这个上限施加在合成容器上，
 * 因此当全局兼容上限被抬高时，合成槽位仍按默认 64 收敛（这正是本配置项的意图）。
 * 配置值以 1 为下界兜底（等价于 {@code coerceAtLeast(1)}）：上限 0 会让 Container
 * 的搬运/拆堆按 0 计算并静默丢弃入站堆叠。
 * 该值只是广告/交互层上限：{@code InventoryCrafting#setInventorySlotContents} 是朴素写入，
 * {@code putStackInSlot}、配方书批量合成等路径不读取它，因此不是槽位封死。
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
        int compatibilityLimit = StackLimitHooks.getCompatibilityStackSize();
        if ((Object) this instanceof InventoryCrafting || (Object) this instanceof InventoryCraftResult) {
            int configuredLimit = StackUpUpConfig.activeCraftingSlotLimit;
            return Math.max(1, Math.min(compatibilityLimit, configuredLimit));
        }
        return compatibilityLimit;
    }
}
