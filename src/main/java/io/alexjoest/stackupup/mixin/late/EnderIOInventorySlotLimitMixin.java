package io.alexjoest.stackupup.mixin.late;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * EnderIO（CEu 内置 EnderCore）`com.enderio.core.common.inventory.InventorySlot#getMaxStackSize`
 * 是槽容量单一事实源（endercore/src/main/java/com/enderio/core/common/inventory/InventorySlot.java:233-235
 * 返回 limit，构造默认 `limit > 0 ? limit : 64` :96）：
 * - 能力槽：`getSlotLimit(int)` 委托它（:246-248），机器 Side/AbstractCapabilityMachineEntity:134 直接读它；
 * - GUI：`EnderSlot#getSlotStackLimit` 委托它（EnderSlot.java:86）；
 * - 写入面：`insertItem` 以 `min(getMaxStackSize(), stack.getMaxStackSize())` 计算可写量（:120/:139），
 *   广告值与真实写入容量同源，容量不变量保持。
 *
 * 只替换「默认 64」：显式 limit（电容槽 1 等）与 TileCrafter 匿名覆写（TileCrafter.java:96-101
 * `bufferStacks ? 64 : 1`）原样保留——Mixin 只改写目标类目标方法的方法体字节码，子类覆写方法有
 * 独立字节码并遮蔽基类实现，基类注入对覆写不生效（本次不改 TileCrafter 的缓冲槽语义）。
 *
 * @Pseudo + require=0 兼容旧 EnderIO 1.5-1.12（无 com.enderio.core.common.inventory.InventorySlot
 * 类，目标缺失时静默跳过）与 EnderCore 0.5.76/0.5.81。
 */
@Pseudo
@Mixin(targets = "com.enderio.core.common.inventory.InventorySlot", remap = false)
abstract class EnderIOInventorySlotLimitMixin {
    @Unique
    private static final int VANILLA_STACK_LIMIT = 64;

    @ModifyReturnValue(method = "getMaxStackSize()I", at = @At("RETURN"), require = 0)
    private int stackupup$expandInventorySlotLimit(int original) {
        return original == VANILLA_STACK_LIMIT ? StackLimitHooks.getCompatibilityStackSize() : original;
    }
}
