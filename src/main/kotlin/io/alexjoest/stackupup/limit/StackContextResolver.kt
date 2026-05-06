package io.alexjoest.stackupup.limit

import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack

/**
 * 把运行时 ItemStack 规范化为统一的规则上下文。
 *
 * 这里只有规则求值真正需要的信息。
 * 当当前规则集不依赖矿辞时，可以跳过 oreDict 查询，避免热路径额外开销。
 */
object StackContextResolver {
    @JvmStatic
    fun fromStack(stack: ItemStack, baseLimit: Int, includeOreNames: Boolean = true): StackContext? {
        if (stack.isEmpty) {
            return null
        }

        val registryName = stack.item.registryName ?: return null
        val type = if (stack.item is ItemBlock) "block" else "item"
        val item = stack.item
        val tabLabel = item.creativeTab?.tabLabel ?: ""
        return StackContext(
            itemId = registryName.toString(),
            modId = registryName.namespace,
            metadata = stack.metadata,
            type = type,
            baseLimit = baseLimit,
            oreNames = if (includeOreNames) RuleRuntime.oreDictIndex().getOreNames(stack) else emptySet(),
            tab = tabLabel,
        )
    }
}
