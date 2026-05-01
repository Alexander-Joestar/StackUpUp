package io.alexjoest.stackupup.rules.parse

enum class DslTokenType(val lexeme: String? = null, val isComparisonOperator: Boolean = false, val isActionOperator: Boolean = false) {
    IDENTIFIER,
    NUMBER,
    EQUALS("=", isComparisonOperator = true),
    PLUS("+"),
    MINUS("-"),
    STAR("*"),
    SLASH("/"),
    NOT_EQUALS("!=", isComparisonOperator = true),
    GREATER(">", isComparisonOperator = true),
    GREATER_EQUALS(">=", isComparisonOperator = true),
    LESS("<", isComparisonOperator = true),
    LESS_EQUALS("<=", isComparisonOperator = true),
    AND_AND("&&"),
    OR_OR("||"),
    IN("in"),
    ARROW("->", isActionOperator = true),
    LEFT_BRACKET("["),
    RIGHT_BRACKET("]"),
    COMMA(","),
    EOF,
    ;

    companion object {
        // 显式维护匹配顺序，避免在类初始化时再次做 filter/sort/associate。
        private val symbolTokens = arrayOf(
            NOT_EQUALS,
            GREATER_EQUALS,
            LESS_EQUALS,
            AND_AND,
            OR_OR,
            ARROW,
            EQUALS,
            PLUS,
            MINUS,
            STAR,
            SLASH,
            GREATER,
            LESS,
            LEFT_BRACKET,
            RIGHT_BRACKET,
            COMMA,
        )

        fun matchSymbol(text: String, startIndex: Int): DslTokenType? {
            for (token in symbolTokens) {
                val symbol = token.lexeme ?: continue
                if (text.regionMatches(startIndex, symbol, 0, symbol.length)) {
                    return token
                }
            }
            return null
        }

        fun resolveKeyword(lexeme: String): DslTokenType? = if (lexeme == IN.lexeme) IN else null
    }
}

data class DslToken(val type: DslTokenType, val lexeme: String)
