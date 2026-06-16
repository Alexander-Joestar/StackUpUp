package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.rules.field.MissingValuePolicy
import io.alexjoest.stackupup.rules.field.RuleFieldContextProvider
import io.alexjoest.stackupup.rules.field.RuleFieldMatcherFactory
import io.alexjoest.stackupup.rules.field.RuleFieldMatchers
import io.alexjoest.stackupup.limit.StackContext

enum class FieldType { ITEM, STRING, STRING_SET, NUMERIC }

/**
 * 规则字段需要的运行时上下文。
 *
 * 这是面向旧 ABI 和诊断调用的兼容投影；字段扩展的主路径是 RuleField.contextProviders。
 */
enum class RuleContextRequirement(val provider: RuleFieldContextProvider) {
    ORE_NAMES(RuleFieldContextProvider.ORE_NAMES),
    MATERIAL(RuleFieldContextProvider.MATERIAL),
}

/**
 * 规则字段枚举。
 *
 * `matchers` 首次访问时懒加载为 uppercase 集合，`fromIdentifier` 只做一次 uppercase 查表。
 * 字段自身声明上下文需求和 matcher，避免编译器按字段名重复分发。
 */
enum class RuleField(
    val fieldType: FieldType,
    aliases: Set<String> = emptySet(),
    val contextProviders: Set<RuleFieldContextProvider> = emptySet(),
    private val cacheKeyExtractor: ((StackContext) -> String)? = null,
    private val matcherFactory: RuleFieldMatcherFactory,
) {
    ITEM(FieldType.ITEM, matcherFactory = RuleFieldMatchers.item()),
    MOD(FieldType.STRING, matcherFactory = RuleFieldMatchers.string(StackContext::modId)),
    TYPE(FieldType.STRING, matcherFactory = RuleFieldMatchers.string(StackContext::type)),
    ORE(
        FieldType.STRING_SET,
        contextProviders = setOf(RuleFieldContextProvider.ORE_NAMES),
        matcherFactory = RuleFieldMatchers.stringSet(StackContext::oreNames)
    ),
    MATERIAL(
        FieldType.STRING,
        contextProviders = setOf(RuleFieldContextProvider.MATERIAL),
        cacheKeyExtractor = StackContext::material,
        matcherFactory = RuleFieldMatchers.string(StackContext::material, MissingValuePolicy.NEVER_MATCH),
    ),
    META(FieldType.NUMERIC, setOf("metadata"), matcherFactory = RuleFieldMatchers.numeric(StackContext::metadata)),
    SIZE(FieldType.NUMERIC, matcherFactory = RuleFieldMatchers.numeric(StackContext::baseLimit)),
    TAB(
        FieldType.STRING,
        contextProviders = setOf(RuleFieldContextProvider.TAB),
        cacheKeyExtractor = StackContext::tab,
        matcherFactory = RuleFieldMatchers.string(StackContext::tab),
    ),
    ;

    val id: String by lazy { name.lowercase() }
    private val matchers: Set<String> by lazy { aliases.mapTo(mutableSetOf(name)) { it.uppercase() } }

    fun contributesToCacheKey(): Boolean = cacheKeyExtractor != null

    /**
     * 编译单值字段比较。
     */
    fun compileMatcher(operator: ComparisonOperator, literal: String): (StackContext) -> Boolean =
        matcherFactory.compile(operator, literal)

    /**
     * 编译列表字段比较，列表语义复用字段自身的等值 matcher。
     */
    fun compileListMatcher(literals: List<String>): (StackContext) -> Boolean =
        matcherFactory.compileList(literals)

    /**
     * 提取该字段贡献给规则缓存键的值。
     */
    internal fun cacheKeyValue(context: StackContext): String =
        cacheKeyExtractor?.invoke(context).orEmpty()

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
