package io.alexjoest.stackupup.rules.field

/**
 * 字段缓存键提取器。
 *
 * 热路径只在存在缓存键字段时创建轻量上下文，避免为了构造缓存键提前创建完整 RuleMatchContext。
 */
internal fun interface RuleFieldCacheKeyExtractor {
    fun extract(context: RuleFieldCacheContext): String
}

internal data class RuleFieldCacheContext(
    val itemId: String,
    val modId: String,
    val metadata: Int,
    val type: String,
    val baseLimit: Int,
    val tab: String,
    val material: String,
)
