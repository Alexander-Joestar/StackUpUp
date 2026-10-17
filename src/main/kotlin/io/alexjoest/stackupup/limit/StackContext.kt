package io.alexjoest.stackupup.limit

import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack

/**
 * 统一的堆叠规则求值上下文。
 *
 * 所有“某个物品栈当前允许多少堆叠”的计算，
 * 都先收敛为这个结构，再交给规则引擎处理。
 *
 * 从 [ItemStack] 构造时，可选字段只在首次访问时计算；直接传入的字段值仍可用于
 * 测试和不需要物品栈的调用方。
 */
class StackContext(
    val itemId: String,
    val modId: String,
    val metadata: Int,
    type: String = "",
    val baseLimit: Int,
    oreNames: Set<String> = emptySet(),
    tab: String = "",
    material: String = "",
    private val stack: ItemStack? = null,
) {
    private val directType = type
    private val directOreNames = oreNames
    private val directTab = tab
    private val directMaterial = material

    val type: String by lazy(LazyThreadSafetyMode.NONE) {
        if (stack != null) {
            if (stack.item is ItemBlock) "block" else "item"
        } else {
            directType
        }
    }

    val oreNames: Set<String> by lazy(LazyThreadSafetyMode.NONE) {
        if (stack != null) RuleRuntime.oreDictIndex().getOreNames(stack) else directOreNames
    }

    val tab: String by lazy(LazyThreadSafetyMode.NONE) {
        if (stack != null) stack.item.creativeTab?.tabLabel.orEmpty() else directTab
    }

    val material: String by lazy(LazyThreadSafetyMode.NONE) {
        if (stack != null) GregTechMaterialResolver.resolveMaterial(stack) else directMaterial
    }
}
