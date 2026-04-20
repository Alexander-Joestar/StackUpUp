package io.alexjoest.stackupup.rules.parse

enum class DslTokenType(
    val lexeme: String? = null,
    val keyword: Boolean = false
) {
    IDENTIFIER,
    NUMBER,
    EQUALS("="),
    PLUS("+"),
    MINUS("-"),
    STAR("*"),
    SLASH("/"),
    NOT_EQUALS("!="),
    GREATER(">"),
    GREATER_EQUALS(">="),
    LESS("<"),
    LESS_EQUALS("<="),
    AND_AND("&&"),
    OR_OR("||"),
    IN("in", keyword = true),
    ARROW("->"),
    LEFT_BRACKET("["),
    RIGHT_BRACKET("]"),
    COMMA(","),
    EOF;

    companion object {
        private val symbolTokens: List<DslTokenType> =
            entries
                .filter { token -> token.lexeme != null && !token.keyword }
                .sortedByDescending { token -> token.lexeme!!.length }

        private val keywordTokens: Map<String, DslTokenType> =
            entries
                .filter { token -> token.lexeme != null && token.keyword }
                .associateBy { token -> token.lexeme!! }

        val comparisonOperators: Set<DslTokenType> =
            setOf(EQUALS, NOT_EQUALS, GREATER, GREATER_EQUALS, LESS, LESS_EQUALS)

        val actionOperators: Set<DslTokenType> = setOf(ARROW)

        fun matchSymbol(text: String, startIndex: Int): DslTokenType? {
            for (token in symbolTokens) {
                val symbol = token.lexeme ?: continue
                if (text.regionMatches(startIndex, symbol, 0, symbol.length)) {
                    return token
                }
            }
            return null
        }

        fun resolveKeyword(lexeme: String): DslTokenType? = keywordTokens[lexeme]
    }
}

data class DslToken(
    val type: DslTokenType,
    val lexeme: String
)

