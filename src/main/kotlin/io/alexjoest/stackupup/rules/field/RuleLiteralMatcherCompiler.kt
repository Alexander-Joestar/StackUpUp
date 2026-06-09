package io.alexjoest.stackupup.rules.field

import io.alexjoest.stackupup.limit.StackContext

internal object RuleLiteralMatcherCompiler {
    fun compileItemMatcher(literal: String): (StackContext) -> Boolean {
        if (literal == "*") {
            // "item = *" 匹配所有可堆叠物品（原版 baseSize > 1）
            return { context -> context.baseLimit > 1 }
        }
        val itemLiteral = parseItemLiteral(literal)
        val itemIdMatcher = compileStringMatcher(itemLiteral.itemIdPattern)
        return { context ->
            itemIdMatcher(context.itemId) && (itemLiteral.meta == null || itemLiteral.meta == context.metadata)
        }
    }

    fun compileStringMatcher(pattern: String): (String) -> Boolean {
        if ('*' !in pattern) {
            return { actual -> actual == pattern }
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
        return regex::matches
    }

    private fun parseItemLiteral(literal: String): ItemLiteral {
        extractMetaLiteral(literal)?.let { return it }

        val lastColon = literal.lastIndexOf(':')
        if (lastColon <= literal.indexOf(':')) {
            return ItemLiteral(itemIdPattern = literal, meta = null)
        }

        val meta = literal.substring(lastColon + 1).toIntOrNull()
            ?: return ItemLiteral(itemIdPattern = literal, meta = null)
        return ItemLiteral(
            itemIdPattern = literal.substring(0, lastColon),
            meta = meta,
        )
    }

    private fun extractMetaLiteral(literal: String): ItemLiteral? {
        val separatorIndex = literal.lastIndexOf('@')
        if (separatorIndex <= 0) {
            return null
        }

        val meta = literal.substring(separatorIndex + 1).toIntOrNull()
            ?: return ItemLiteral(itemIdPattern = literal, meta = null)
        return ItemLiteral(
            itemIdPattern = literal.substring(0, separatorIndex),
            meta = meta,
        )
    }

    private data class ItemLiteral(val itemIdPattern: String, val meta: Int?)
}
