package io.alexjoest.stackupup.rules

enum class FieldType { ITEM, STRING, STRING_SET, NUMERIC }

/**
 * 规则字段枚举。
 *
 * `matchers` 首次访问时懒加载为 uppercase 集合，`fromIdentifier` 只做一次 uppercase 查表。
 * 新增字段只需在 enum 里加一行，如有别名传入 `setOf("alias1", "alias2")`。
 */
enum class RuleField(val fieldType: FieldType, aliases: Set<String> = emptySet()) {
    ITEM(FieldType.ITEM),
    MOD(FieldType.STRING),
    TYPE(FieldType.STRING),
    ORE(FieldType.STRING_SET),
    MATERIAL(FieldType.STRING),
    META(FieldType.NUMERIC, setOf("metadata")),
    SIZE(FieldType.NUMERIC),
    TAB(FieldType.STRING),
    ;

    val id: String by lazy { name.lowercase() }
    private val matchers: Set<String> by lazy { aliases.mapTo(mutableSetOf(name)) { it.uppercase() } }

    companion object {
        private val byName: Map<String, RuleField> by lazy {
            entries.flatMap { f -> f.matchers.map { it to f } }.toMap()
        }

        fun fromIdentifier(identifier: String): RuleField? = byName[identifier.uppercase()]
    }
}

enum class ComparisonOperator(val symbol: String) {
    EQUALS("="),
    NOT_EQUALS("!="),
    GREATER(">"),
    GREATER_EQUALS(">="),
    LESS("<"),
    LESS_EQUALS("<="),
    ;

    fun reverse(): ComparisonOperator = when (this) {
        LESS -> GREATER
        LESS_EQUALS -> GREATER_EQUALS
        GREATER -> LESS
        GREATER_EQUALS -> LESS_EQUALS
        EQUALS, NOT_EQUALS -> throw RuleMessages.exception(RuleMessageKey.UNSUPPORTED_REVERSE_OPERATOR, symbol)
    }

    companion object {
        fun fromSymbol(symbol: String): ComparisonOperator = when (symbol) {
            "=" -> EQUALS
            "!=" -> NOT_EQUALS
            ">" -> GREATER
            ">=" -> GREATER_EQUALS
            "<" -> LESS
            "<=" -> LESS_EQUALS
            else -> throw RuleMessages.exception(RuleMessageKey.UNSUPPORTED_COMPARISON_OPERATOR, symbol)
        }
    }
}

enum class RuleStepKind {
    SET,
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    ;

    val id: String by lazy { name.lowercase() }
}
