package pl.asie.stackup.rules.parse

object DslTokenizer {
    fun tokenize(line: String): List<DslToken> {
        val tokens = ArrayList<DslToken>()
        var index = 0

        while (index < line.length) {
            val current = line[index]
            when {
                current.isWhitespace() -> index++
                current == '&' && index + 1 < line.length && line[index + 1] == '&' -> {
                    tokens.add(DslToken(DslTokenType.AND_AND, "&&"))
                    index += 2
                }
                current == '|' && index + 1 < line.length && line[index + 1] == '|' -> {
                    tokens.add(DslToken(DslTokenType.OR_OR, "||"))
                    index += 2
                }
                current == '-' && index + 1 < line.length && line[index + 1] == '>' -> {
                    tokens.add(DslToken(DslTokenType.ARROW, "->"))
                    index += 2
                }
                current == '=' -> {
                    tokens.add(DslToken(DslTokenType.EQUALS, "="))
                    index++
                }
                current == '>' -> {
                    tokens.add(DslToken(DslTokenType.GREATER, ">"))
                    index++
                }
                current == '<' -> {
                    tokens.add(DslToken(DslTokenType.LESS, "<"))
                    index++
                }
                current == '[' -> {
                    tokens.add(DslToken(DslTokenType.LEFT_BRACKET, "["))
                    index++
                }
                current == ']' -> {
                    tokens.add(DslToken(DslTokenType.RIGHT_BRACKET, "]"))
                    index++
                }
                current == ',' -> {
                    tokens.add(DslToken(DslTokenType.COMMA, ","))
                    index++
                }
                current.isDigit() -> {
                    val start = index
                    while (index < line.length && line[index].isDigit()) {
                        index++
                    }
                    tokens.add(DslToken(DslTokenType.NUMBER, line.substring(start, index)))
                }
                else -> {
                    val start = index
                    while (index < line.length && !line[index].isWhitespace() && line[index] !in charArrayOf('&', '|', '-', '=', '>', '<', '[', ']', ',')) {
                        index++
                    }
                    val lexeme = line.substring(start, index)
                    val type = if (lexeme == "in") DslTokenType.IN else DslTokenType.IDENTIFIER
                    tokens.add(DslToken(type, lexeme))
                }
            }
        }

        tokens.add(DslToken(DslTokenType.EOF, ""))
        return tokens
    }
}
