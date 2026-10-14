package io.alexjoest.stackupup.mixin.late.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * AE2 late config 的入口保险丝（mixins.stackupup.late.ae2.json 仍引用该类），不包含任何注入。
 * `AdaptorItemHandler#addItems` 对 `IItemHandler#insertItem` 的调用原样执行，我方不在热路径增加分支或分配。
 * 不使用 @WrapOperation + operation.call：`Operation.call(Object...)` 是 varargs，包装会为每次调用增加
 * 参数数组和装箱；直接不包裹则原调用零开销执行。`DevAutomationServerDriver#probeBoundaryLimit`
 * 只测试通用 `ItemStackHandler`，不是 AE2 adaptor；AE2 实际 remainder 行为仍为 UNKNOWN。
 */
@Pseudo
@Mixin(targets = "appeng.util.inv.AdaptorItemHandler", remap = false)
abstract class AppEngAdaptorItemHandlerMixin {
}
