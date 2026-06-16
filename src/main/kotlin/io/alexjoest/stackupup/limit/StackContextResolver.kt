package io.alexjoest.stackupup.limit

import io.alexjoest.stackupup.rules.compile.RuntimeContextRequirements
import io.alexjoest.stackupup.rules.field.StackContextFields
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack

/**
 * 把运行时 ItemStack 规范化为统一的规则上下文。
 *
 * 这里只有规则求值真正需要的信息。
 * 可选字段由 RuntimeContextRequirements 中的 provider plan 按需采集。
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
        val type = if (stack.item is ItemBlock) "block" else "item"
        val fields = StackContextFields()
        requirements.providers.forEach { it.collect(stack, fields) }
        return StackContext(
            itemId = registryName.toString(),
            modId = registryName.namespace,
            metadata = stack.metadata,
            type = type,
            baseLimit = baseLimit,
            oreNames = fields.oreNames,
            tab = fields.tab,
            material = fields.material,
        )
    }
}
