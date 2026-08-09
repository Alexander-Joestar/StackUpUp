package io.alexjoest.stackupup.rules.field

import io.alexjoest.stackupup.rules.RuleMessages
import io.alexjoest.stackupup.rules.parse.ItemLiteralSyntax

/** 用途：把规则字面量（含通配）编译为匹配器节点。 */
internal object RuleLiteralMatcherCompiler {
    /**
     * 字符串 pattern 编译：无通配 → 精确匹配节点；含通配 → 锚定正则节点。
     */
    fun compileStringMatcher(pattern: String): StringMatcher {
        if ('*' !in pattern) {
            return ExactStringMatcher(pattern)
        }

        val regex = buildString(pattern.length * 2) {
            append('^')
            for (char in pattern) {
                when (char) {
                    '*' -> append(".*")
                    '.', '(', ')', '[', ']', '{', '}', '+', '?', '^', '$', '|', '\\' -> {
                        append('\\')
                        append(char)
                    }
                    else -> append(char)
                }
            }
            append('$')
        }.let(::Regex)
        return WildcardStringMatcher(regex)
    }

    /**
     * item 字面量解析：itemId pattern + 可选精确 meta。
     *
     * 字面量语法已在解析阶段一次定型并校验；这里只接受 Valid，
     * 非法输入按 fail-fast 直接抛错，不再静默回落为 pattern。
     */
    fun parseItemPattern(literal: String): ItemPattern {
        val itemLiteral = when (val parsed = ItemLiteralSyntax.parse(literal)) {
            is ItemLiteralSyntax.Valid -> parsed
            is ItemLiteralSyntax.Invalid -> throw RuleMessages.exception(parsed.reasonKey, *parsed.reasonArgs.toTypedArray())
        }
        return ItemPattern(
            itemIdMatcher = compileStringMatcher(itemLiteral.itemIdPattern),
            meta = itemLiteral.meta,
        )
    }

    internal data class ItemPattern(val itemIdMatcher: StringMatcher, val meta: Int?)
}
