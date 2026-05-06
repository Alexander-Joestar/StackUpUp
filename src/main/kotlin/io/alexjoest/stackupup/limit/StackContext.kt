package io.alexjoest.stackupup.limit

/**
 * 统一的堆叠规则求值上下文。
 *
 * 所有“某个物品栈当前允许多少堆叠”的计算，
 * 都先收敛为这个结构，再交给规则引擎处理。
 */
data class StackContext(
    val itemId: String,
    val modId: String,
    val metadata: Int,
    val type: String,
    val baseLimit: Int,
    val oreNames: Set<String>,
    val tab: String = "",
) {
    fun toIdentity(): StackIdentity = StackIdentity(itemId, modId, metadata, type)
}
