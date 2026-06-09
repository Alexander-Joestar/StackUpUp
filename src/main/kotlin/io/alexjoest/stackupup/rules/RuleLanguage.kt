package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.rules.compile.MissingValuePolicy
import io.alexjoest.stackupup.rules.compile.RuleFieldMatcherFactory
import io.alexjoest.stackupup.rules.compile.RuleFieldMatchers
import io.alexjoest.stackupup.rules.model.RuleMatchContext

enum class FieldType { ITEM, STRING, STRING_SET, NUMERIC }

/**
 * 规则字段需要的运行时上下文。
 */
enum class RuleContextRequirement { ORE_NAMES, MATERIAL }

/**
 * 规则字段枚举。
 *
 * `matchers` 首次访问时懒加载为 uppercase 集合，`fromIdentifier` 只做一次 uppercase 查表。
 * 字段自身声明上下文需求和 matcher，避免编译器按字段名重复分发。
 */
enum class RuleField(
    val fieldType: FieldType,
    aliases: Set<String> = emptySet(),
    val requirements: Set<RuleContextRequirement> = emptySet(),
    val contributesToCacheKey: Boolean = false,
    private val matcherFactory: RuleFieldMatcherFactory,
) {
    ITEM(FieldType.ITEM, matcherFactory = RuleFieldMatchers.item()),
    MOD(FieldType.STRING, matcherFactory = RuleFieldMatchers.string(RuleMatchContext::modId)),
    TYPE(FieldType.STRING, matcherFactory = RuleFieldMatchers.string(RuleMatchContext::type)),
    ORE(
        FieldType.STRING_SET,
        requirements = setOf(RuleContextRequirement.ORE_NAMES),
        matcherFactory = RuleFieldMatchers.stringSet(RuleMatchContext::oreNames)
    ),
    MATERIAL(
        FieldType.STRING,
        requirements = setOf(RuleContextRequirement.MATERIAL),
        contributesToCacheKey = true,
        matcherFactory = RuleFieldMatchers.string(RuleMatchContext::material, MissingValuePolicy.NEVER_MATCH),
    ),
    META(FieldType.NUMERIC, setOf("metadata"), matcherFactory = RuleFieldMatchers.numeric(RuleMatchContext::meta)),
    SIZE(FieldType.NUMERIC, matcherFactory = RuleFieldMatchers.numeric(RuleMatchContext::baseSize)),
    TAB(FieldType.STRING, matcherFactory = RuleFieldMatchers.string(RuleMatchContext::tab)),
    ;

    val id: String by lazy { name.lowercase() }
    private val matchers: Set<String> by lazy { aliases.mapTo(mutableSetOf(name)) { it.uppercase() } }

    /**
     * 编译单值字段比较。
     */
    fun compileMatcher(operator: ComparisonOperator, literal: String): (RuleMatchContext) -> Boolean =
        matcherFactory.compile(operator, literal)

    /**
     * 编译列表字段比较，列表语义复用字段自身的等值 matcher。
     */
    fun compileListMatcher(literals: List<String>): (RuleMatchContext) -> Boolean =
        matcherFactory.compileList(literals)

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
