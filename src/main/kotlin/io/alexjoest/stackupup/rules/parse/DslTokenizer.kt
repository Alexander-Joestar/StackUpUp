package io.alexjoest.stackupup.rules.parse

object DslTokenizer {
    fun tokenize(line: String): List<DslToken> {
        val tokens = ArrayList<DslToken>()
        var index = 0

        while (index < line.length) {
            val current = line[index]
            when {
                current.isWhitespace() -> index++
                current.isDigit() -> {
                    val start = index
                    while (index < line.length && line[index].isDigit()) {
                        index++
                    }
                    tokens.add(DslToken(DslTokenType.NUMBER, line.substring(start, index)))
                }
                DslTokenType.matchSymbol(line, index) != null -> {
                    val tokenType = requireNotNull(DslTokenType.matchSymbol(line, index))
                    val symbol = requireNotNull(tokenType.lexeme)
                    tokens.add(DslToken(tokenType, symbol))
                    index += symbol.length
                }
                else -> {
                    val start = index
                    while (index < line.length && !line[index].isWhitespace() && DslTokenType.matchSymbol(line, index) == null) {
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
}

