package io.alexjoest.stackupup.rules

enum class RuleField(
    val id: String
) {
    ITEM("item"),
    MOD("mod"),
    TYPE("type"),
    ORE("ore"),
    META("meta"),
    SIZE("size");

    companion object {
        fun fromIdentifier(identifier: String): RuleField? {
            return when (identifier) {
                "item" -> ITEM
                "mod" -> MOD
                "type" -> TYPE
                "ore" -> ORE
                "meta", "metadata" -> META
                "size" -> SIZE
                else -> null
            }
        }
    }
}

enum class ComparisonOperator(
    val symbol: String
) {
    EQUALS("="),
    NOT_EQUALS("!="),
    GREATER(">"),
    GREATER_EQUALS(">="),
    LESS("<"),
    LESS_EQUALS("<=");

    fun reverse(): ComparisonOperator {
        return when (this) {
            LESS -> GREATER
            LESS_EQUALS -> GREATER_EQUALS
            GREATER -> LESS
            GREATER_EQUALS -> LESS_EQUALS
            EQUALS, NOT_EQUALS -> error("不支持反转运算符: $symbol")
        }
    }

    companion object {
        fun fromSymbol(symbol: String): ComparisonOperator {
            return when (symbol) {
                "=" -> EQUALS
                "!=" -> NOT_EQUALS
                ">" -> GREATER
                ">=" -> GREATER_EQUALS
                "<" -> LESS
                "<=" -> LESS_EQUALS
                else -> error("不支持的比较运算符: $symbol")
            }
        }
    }
}

enum class RuleStepKind(
    val debugName: String
) {
    SET("set"),
    ADD("add"),
    SUBTRACT("subtract"),
    MULTIPLY("multiply"),
    DIVIDE("divide")
}
