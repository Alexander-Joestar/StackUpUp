package io.alexjoest.stackupup.rules.parse

import io.alexjoest.stackupup.rules.RuleMessageKey

/**
 * item 字面量语法的唯一事实源（T7.2 一次定型）。
 *
 * 1.12.2 的 `ResourceLocation.splitObjectName` 只按第一个冒号分割（`indexOf(58)`），
 * 第一个冒号之后整段都是 path（可含冒号）。因此 item 字面量不按冒号段数推断 meta：
 * - `@整数`：精确 meta；
 * - `@*`：任意 meta（等价于不约束 meta）；
 * - 无 `@`：整串按 item ID pattern 处理，不约束 meta；`namespace:path:part` 保持原值。
 *
 * 非法写法（`@abc`、`@-1`、`@` 前无 item ID、`@` 后不是整数或 `*`）返回 [Invalid]，
 * 由调用层决定抛出带位置（列号）的错误；禁止静默回落为 pattern。
 */
internal sealed interface ItemLiteralSyntax {
    data class Valid(val itemIdPattern: String, val meta: Int?, val metaAny: Boolean) : ItemLiteralSyntax

    data class Invalid(val reasonKey: RuleMessageKey, val reasonArgs: List<Any> = emptyList()) : ItemLiteralSyntax

    companion object {
        fun parse(literal: String): ItemLiteralSyntax {
            val atIndex = literal.indexOf('@')
            if (atIndex < 0) {
                return Valid(itemIdPattern = literal, meta = null, metaAny = false)
            }
            if (atIndex == 0) {
                return Invalid(RuleMessageKey.ITEM_META_MISSING_ITEM_ID)
            }
            val itemIdPattern = literal.substring(0, atIndex)
            val metaSpec = literal.substring(atIndex + 1)
            if (metaSpec == "*") {
                return Valid(itemIdPattern = itemIdPattern, meta = null, metaAny = true)
            }
            val meta = metaSpec.toIntOrNull()
                ?: return Invalid(RuleMessageKey.ITEM_META_NOT_INTEGER, listOf(metaSpec))
            if (meta < 0) {
                return Invalid(RuleMessageKey.ITEM_META_NEGATIVE, listOf(metaSpec))
            }
            return Valid(itemIdPattern = itemIdPattern, meta = meta, metaAny = false)
        }
    }
}
