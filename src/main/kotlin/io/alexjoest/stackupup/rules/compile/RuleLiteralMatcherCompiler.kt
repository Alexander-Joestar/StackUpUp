package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.model.RuleMatchContext

internal object RuleLiteralMatcherCompiler {
    fun compileItemMatcher(literal: String): (RuleMatchContext) -> Boolean {
        val itemLiteral = parseItemLiteral(literal)
        val itemIdMatcher = compileStringMatcher(itemLiteral.itemIdPattern)
        return { context ->
            itemIdMatcher(context.itemId) && (itemLiteral.meta == null || itemLiteral.meta == context.meta)
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
        extractMetaLiteral(literal, '@')?.let { return it }

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

    private fun extractMetaLiteral(literal: String, separator: Char): ItemLiteral? {
        val separatorIndex = literal.lastIndexOf(separator)
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
