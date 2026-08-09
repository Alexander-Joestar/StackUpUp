package io.alexjoest.stackupup.mixin.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NuclearCraft Distributor 溢出掉落与裁减移除（用户明确：不裁不丢，靠 distributeItems 自然排空）。
 *
 * 目标类 `nc.multiblock.distributor.Distributor`（mods-under-test/NuclearCraft/src/main/java/
 * nc/multiblock/distributor/Distributor.java）：
 * <ul>
 *   <li>`cullInventory()Z`（:204-216）：存量超过 itemStackLimit 时 `setCount(itemStackLimit)` 裁减
 *       （:211）并 `dropOverflow` 掉落（:214）。触发场景是容量收缩事件（部件拆装 refreshCapacity :192、
 *       NBT 装载 syncDataFrom :532），非投喂。本 mixin 在 HEAD 取消并返回 false：不裁不丢，超限存量保留，
 *       靠 `distributeItems`（:289-352，每 tick 向 outlets 推送直到排空）自然排空；入口满限时 remainder
 *       由投喂方路径自行退回。</li>
 *   <li>`dropOverflow(Ljava/util/List;)V`（:218-226）：两个调用点——`cullInventory` :214 与 `onAssimilate`
 *       :131（多块结构合并时 insertIntoInventory 的剩余）。用户要求"不允许掉落"同样适用于合并场景，两处都拦；
 *       HEAD 取消一处注入覆盖全部调用点。合并场景剩余物不再掉落（原栈在 vanilla 合并路径本就随被吸收方
 *       对象弃置，见 decision-record §3.10 取证）。</li>
 * </ul>
 *
 * cullInventory 返回值 `changed`（refreshCapacity :192 `changed |= cullInventory()`）只影响同步频率，
 * 恒 false 不影响功能。注入器选择：整体控制流接管按项目决策（compatibility-decision-record.md §8.3）
 * 属高风险写法，但目标证据完整（mod 源码逐行核对）、有 mod gate + @Pseudo + require=0 缺失诊断；
 * 两方法体除裁减/掉落外无其他副作用（逐行核对），取消不波及其他副作用。失败方向安全：注入不生效时
 * 维持 NC 原行为，不会引入新的物品丢失。
 */
@Pseudo
@Mixin(targets = "nc.multiblock.distributor.Distributor", remap = false)
abstract class NuclearCraftDistributorNoDropMixin {

    @Inject(method = "cullInventory()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void stackupup$noCullInventory(CallbackInfoReturnable<Boolean> cir) {
        // 不裁不丢：保留超限存量，返回 false（不触发同步），靠 distributeItems 自然排空。
        cir.setReturnValue(false);
    }

    @Inject(method = "dropOverflow(Ljava/util/List;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void stackupup$noDropOverflow(CallbackInfo ci) {
        // 不允许掉落：覆盖 cullInventory（:214）与 onAssimilate 合并（:131）两个调用点。
        ci.cancel();
    }
}
