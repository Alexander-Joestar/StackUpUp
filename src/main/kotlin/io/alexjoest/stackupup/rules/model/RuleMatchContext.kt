package io.alexjoest.stackupup.rules.model

import io.alexjoest.stackupup.limit.StackContext

/**
 * 旧规则匹配上下文兼容壳；新规则求值直接使用 `StackContext`。
 */
@Deprecated("Rule matching now evaluates StackContext directly.")
data class RuleMatchContext(
    val itemId: String,
    val modId: String,
    val meta: Int,
    val baseSize: Int,
    val type: String,
    val oreNames: Set<String>,
    val tab: String = "",
    val material: String = "",
) {
    /**
     * 把旧调用方传入的匹配上下文转换为当前运行时上下文。
     */
    fun toStackContext(): StackContext = StackContext(
        itemId = itemId,
        modId = modId,
        metadata = meta,
        type = type,
        baseLimit = baseSize,
        oreNames = oreNames,
        tab = tab,
        material = material,
    )
}
