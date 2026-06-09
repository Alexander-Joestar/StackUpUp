package io.alexjoest.stackupup.rules.field

import io.alexjoest.stackupup.limit.StackContext

/**
 * 字段缓存键提取器。
 */
internal fun interface RuleFieldCacheKeyExtractor {
    fun extract(context: StackContext): String
}
