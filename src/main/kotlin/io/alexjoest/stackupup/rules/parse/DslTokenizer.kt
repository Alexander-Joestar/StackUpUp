package io.alexjoest.stackupup.rules.parse

object DslTokenizer {
    fun tokenize(line: String): List<DslToken> {
        val tokens = ArrayList<DslToken>()
        var index = 0

        while (index < line.length) {
            val current = line[index]
            val symbolType = DslTokenType.matchSymbol(line, index)
            when {
                current.isWhitespace()                                     -> index++
                current.isDigit()                                          -> {
                    val start = index
                    while (index < line.length && line[index].isDigit()) {
                        index++
                    }
                    tokens.add(DslToken(DslTokenType.NUMBER, line.substring(start, index)))
                }

                symbolType != null && shouldEmitSymbol(symbolType, tokens) -> {
                    val symbol = requireNotNull(symbolType.lexeme)
                    tokens.add(DslToken(symbolType, symbol))
                    index += symbol.length
                }

                else                                                       -> {
                    val start = index
                    while (
                        index < line.length &&
                        !line[index].isWhitespace() &&
                        DslTokenType.matchSymbol(line, index)?.let { shouldEmitSymbol(it, tokens) } != true
                    ) {
                        index++
                    }
                    val lexeme = line.substring(start, index)
                    val type = DslTokenType.resolveKeyword(lexeme) ?: DslTokenType.IDENTIFIER
                    tokens.add(DslToken(type, lexeme))
                }
            }
        }

        tokens.add(DslToken(DslTokenType.EOF, ""))
        return tokens
    }

    private fun shouldEmitSymbol(type: DslTokenType, tokens: List<DslToken>): Boolean {
        return when (type) {
            DslTokenType.PLUS,
            DslTokenType.MINUS,
            DslTokenType.STAR,
            DslTokenType.SLASH -> tokens.lastOrNull()?.type == DslTokenType.ARROW

            else               -> true
        }
    }
}

