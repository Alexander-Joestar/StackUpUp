package io.alexjoest.stackupup.mixin.late.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * 方案 A（2026-08-08）：热路径零逻辑。
 *
 * 本类保留为 AE2 late config 的入口保险丝（mixins.stackupup.late.ae2.json 仍引用该类），但不包含任何注入：
 * `AdaptorItemHandler#addItems` 对 `IItemHandler#insertItem` 的调用原样执行，我方以零分支、零分配介入。
 *
 * 为何不用 @WrapOperation + operation.call：`Operation.call(Object...)` 是 varargs，javac 会为每次调用生成
 * Object[4] 数组与 Integer/Boolean 装箱（见编译产物字节码），违背"热路径零分配"约束；直接不包裹则原调用
 * 零开销执行。"不吞"由原版/Forge remainder 契约结构性保证（超量返回 remainder，由 AE2 侧自行回收），
 * 运行期由边界探针 DevAutomationServerDriver#probeBoundaryLimit 验证。
 */
@Pseudo
@Mixin(targets = "appeng.util.inv.AdaptorItemHandler", remap = false)
abstract class AppEngAdaptorItemHandlerMixin {
}
