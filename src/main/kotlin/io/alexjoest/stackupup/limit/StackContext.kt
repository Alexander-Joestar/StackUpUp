package io.alexjoest.stackupup.limit

import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack

/**
 * 统一的堆叠规则求值上下文。
 *
 * 所有“某个物品栈当前允许多少堆叠”的计算，
 * 都先收敛为这个结构，再交给规则引擎处理。
 *
 * 身份字段 `modId` 与 `type` 由 [itemId] 的命名空间和 [ItemStack] 每次访问就地推导；
 * 开销大的可选字段 [oreNames]、[tab]、[material] 只在首次访问时计算。
 * 没有物品栈时（例如只关心 itemId/meta 的调用方）[type] 取 `item`，其余三个可选字段为空。
 */
class StackContext(val itemId: String, val metadata: Int, val baseLimit: Int, private val stack: ItemStack? = null) {
    val modId: String get() = itemId.substringBefore(':')

    val type: String get() = if (stack != null && stack.item is ItemBlock) "block" else "item"

    val oreNames: Set<String> by lazy(LazyThreadSafetyMode.NONE) {
        stack?.let { RuleRuntime.oreDictIndex().getOreNames(it) } ?: emptySet()
    }

    val tab: String by lazy(LazyThreadSafetyMode.NONE) {
        stack?.item?.creativeTab?.tabLabel.orEmpty()
    }

    val material: String by lazy(LazyThreadSafetyMode.NONE) {
        stack?.let { GregTechMaterialResolver.resolveMaterial(it) } ?: ""
    }
}
