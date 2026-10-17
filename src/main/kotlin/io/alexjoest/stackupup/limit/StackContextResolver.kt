package io.alexjoest.stackupup.limit

import io.alexjoest.stackupup.rules.compile.RuntimeContextRequirements
import net.minecraft.item.ItemStack

/**
 * 把运行时 ItemStack 规范化为统一的规则上下文。
 *
 * 这里只有规则求值真正需要的信息；可选字段由 [StackContext] 在首次访问时按需解析。
 */
object StackContextResolver {
    @JvmStatic
    fun fromStack(
        stack: ItemStack,
        baseLimit: Int,
        requirements: RuntimeContextRequirements = RuntimeContextRequirements.DEFAULT_STACK_CONTEXT,
    ): StackContext? {
        if (stack.isEmpty) {
            return null
        }

        val registryName = stack.item.registryName ?: return null
        return StackContext(
            itemId = registryName.toString(),
            modId = registryName.namespace,
            metadata = stack.metadata,
            baseLimit = baseLimit,
            stack = stack,
            requirements = requirements,
        )
    }
}
