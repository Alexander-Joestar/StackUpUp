package io.alexjoest.stackupup.core

import net.minecraft.launchwrapper.IClassTransformer

/**
 * 当前保留的最小动态兼容层。
 *
 * 这层只负责：
 * 1. 过滤显然无关的基础运行时类
 * 2. 为运行时发现目标生成补丁计划
 * 3. 把补丁计划应用到字节码
 */
class DynamicCompatTransformer : IClassTransformer {
    override fun transform(name: String?, transformedNameIn: String?, basicClass: ByteArray?): ByteArray? {
        if (basicClass == null) {
            return null
        }

        val internalName = transformedNameIn ?: name
        if (internalName.isNullOrEmpty() || CoremodClassFilter.shouldSkip(internalName)) {
            return basicClass
        }

        val transformedName = toDotClassName(internalName)
        val plan = DynamicCompatPlanBuilder.build(transformedName, basicClass)
        return if (plan.hasPatches) {
            BytecodePatchApplier.apply(basicClass, plan.patches)
        } else {
            basicClass
        }
    }
}
