package io.alexjoest.stackupup.limit

import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack

/**
 * 统一的堆叠规则求值上下文。
 *
 * 所有“某个物品栈当前允许多少堆叠”的计算，
 * 都先收敛为这个结构，再交给规则引擎处理。
 *
 * 身份字段 `modId` 不再作为构造参数传入，而是由 [itemId] 的命名空间推导并在首次访问时计算；
 * 从 [ItemStack] 构造时，其余可选字段只在首次访问时计算；直接传入的字段值仍可用于
 * 测试和不需要物品栈的调用方。
 */
class StackContext(
    val itemId: String,
    val metadata: Int,
    val baseLimit: Int,
    private val stack: ItemStack? = null,
    private val directType: String = "",
    private val directOreNames: Set<String> = emptySet(),
    private val directTab: String = "",
    private val directMaterial: String = "",
) {
    // 推导一次即缓存：快路径命中时按 modId 计算缓存键，重复 substring 会破坏零分配契约
    // （StackLimitServiceTest.fastPathHit_shouldNotAllocateObjects）。
    val modId: String by lazy(LazyThreadSafetyMode.NONE) { itemId.substringBefore(':') }

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
