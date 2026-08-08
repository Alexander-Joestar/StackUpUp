package io.alexjoest.stackupup.rules.parse

import io.alexjoest.stackupup.rules.RuleMessageKey
import io.alexjoest.stackupup.rules.RuleMessages

object DslTokenizer {
    fun tokenize(line: String): List<DslToken> {
        val tokens = ArrayList<DslToken>()
        var index = 0

        while (index < line.length) {
            val current = line[index]
            val symbolType = DslTokenType.matchSymbol(line, index)
            when {
                current.isWhitespace() -> index++
                current.isDigit() -> {
                    val start = index
                    while (index < line.length && line[index].isDigit()) {
                        index++
                    }
                    tokens.add(DslToken(DslTokenType.NUMBER, line.substring(start, index), column = start + 1))
                }

                symbolType != null && shouldEmitSymbol(symbolType, tokens) -> {
                    val symbol = requireNotNull(symbolType.lexeme)
                    tokens.add(DslToken(symbolType, symbol, column = index + 1))
                    index += symbol.length
                }

                else -> {
                    val start = index
                    val value = StringBuilder(line.length - start)
                    while (index < line.length) {
                        val char = line[index]
                        if (char == '"') {
                            index = appendQuotedSection(line, index, value)
                            continue
                        }
                        if (char.isWhitespace()) {
                            break
                        }
                        if (DslTokenType.matchSymbol(line, index)?.let { shouldEmitSymbol(it, tokens) } == true) {
                            break
                        }
                        value.append(char)
                        index++
                    }
                    val lexeme = value.toString()
                    val type = DslTokenType.resolveKeyword(lexeme) ?: DslTokenType.IDENTIFIER
                    tokens.add(DslToken(type, lexeme, column = start + 1))
                }
            }
        }

        tokens.add(DslToken(DslTokenType.EOF, ""))
        return tokens
    }

    /**
     * 引号只改变词法边界不改变值：把 [line] 中开引号 [quoteIndex] 到闭引号之间的内容
     * 原样追加进 [value]，返回闭引号后的下一个 index。未闭合或空引号字面量直接抛错
     * （fail-fast），错误指向开引号所在列。
     */
    private fun appendQuotedSection(line: String, quoteIndex: Int, value: StringBuilder): Int {
        val contentStart = quoteIndex + 1
        var index = contentStart
        while (index < line.length && line[index] != '"') {
            index++
        }
        if (index >= line.length) {
            throw RuleMessages.exception(RuleMessageKey.UNTERMINATED_QUOTED_LITERAL, quoteIndex + 1)
        }
        if (index == contentStart) {
            throw RuleMessages.exception(RuleMessageKey.EMPTY_QUOTED_LITERAL, quoteIndex + 1)
        }
        value.append(line, contentStart, index)
        return index + 1
    }

    private fun shouldEmitSymbol(type: DslTokenType, tokens: List<DslToken>): Boolean = when (type) {
        DslTokenType.PLUS,
        DslTokenType.MINUS,
        DslTokenType.STAR,
        DslTokenType.SLASH,
        -> tokens.lastOrNull()?.type == DslTokenType.ARROW

        else -> true
    }
}
