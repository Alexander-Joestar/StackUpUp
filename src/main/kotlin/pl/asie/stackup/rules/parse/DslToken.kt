package pl.asie.stackup.rules.parse

enum class DslTokenType {
    IDENTIFIER,
    NUMBER,
    EQUALS,
    NOT_EQUALS,
    GREATER,
    GREATER_EQUALS,
    LESS,
    LESS_EQUALS,
    AND_AND,
    OR_OR,
    IN,
    ARROW,
    PLUS_EQUALS,
    MINUS_EQUALS,
    STAR_EQUALS,
    SLASH_EQUALS,
    LEFT_BRACKET,
    RIGHT_BRACKET,
    COMMA,
    EOF
}

data class DslToken(
    val type: DslTokenType,
    val lexeme: String
)
