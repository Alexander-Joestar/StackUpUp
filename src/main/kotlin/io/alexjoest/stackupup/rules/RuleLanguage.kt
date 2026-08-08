package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.rules.field.FieldMatcher
import io.alexjoest.stackupup.rules.field.FieldScopedMatcher
import io.alexjoest.stackupup.rules.field.MissingValuePolicy
import io.alexjoest.stackupup.rules.field.RuleFieldContextProvider
import io.alexjoest.stackupup.rules.field.RuleFieldMatcherFactory
import io.alexjoest.stackupup.rules.field.RuleFieldMatchers

enum class FieldType { ITEM, STRING, STRING_SET, NUMERIC }

/**
 * 字段对缓存键的贡献策略（T6 显式声明，替代注释论证）。
 *
 * 每个字段必须显式声明一种策略（必填构造参数，缺失即编译错误）；
 * 声明是缓存键覆盖的唯一事实源，缓存键字段集合由 matcher 树机械推导后按此过滤：
 * - [IDENTITY_FIXED]：字段值已由固定身份键分量承载（itemId/modId/metadata/type/baseLimit），无需额外键材料；
 * - [VALUE_CONTRIBUTED]：字段值作为字段键材料进入缓存键，必须同时声明 [RuleField.cacheKeyExtractor]；
 * - [STABLE_VIA_IDENTITY]：字段值由身份（itemId+metadata）稳定决定，不进键；
 *   失效由索引替换驱动（RuleRuntime.replaceOreDictIndex → replaceRuntime → 整体换新服务实例与缓存）。
 */
enum class CacheKeyStrategy {
    IDENTITY_FIXED,
    VALUE_CONTRIBUTED,
    STABLE_VIA_IDENTITY,
}

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
    val cacheKeyStrategy: CacheKeyStrategy,
    private val cacheKeyExtractor: ((StackContext) -> String)?,
    private val matcherFactory: RuleFieldMatcherFactory,
) {
    ITEM(
        FieldType.ITEM,
        cacheKeyStrategy = CacheKeyStrategy.IDENTITY_FIXED,
        cacheKeyExtractor = null,
        matcherFactory = RuleFieldMatchers.item(),
    ),
    MOD(
        FieldType.STRING,
        cacheKeyStrategy = CacheKeyStrategy.IDENTITY_FIXED,
        cacheKeyExtractor = null,
        matcherFactory = RuleFieldMatchers.string(StackContext::modId),
    ),
    TYPE(
        FieldType.STRING,
        cacheKeyStrategy = CacheKeyStrategy.IDENTITY_FIXED,
        cacheKeyExtractor = null,
        matcherFactory = RuleFieldMatchers.string(StackContext::type),
    ),
    ORE(
        FieldType.STRING_SET,
        contextProviders = setOf(RuleFieldContextProvider.ORE_NAMES),
        cacheKeyStrategy = CacheKeyStrategy.STABLE_VIA_IDENTITY,
        cacheKeyExtractor = null,
        matcherFactory = RuleFieldMatchers.stringSet(StackContext::oreNames),
    ),
    MATERIAL(
        FieldType.STRING,
        contextProviders = setOf(RuleFieldContextProvider.MATERIAL),
        cacheKeyStrategy = CacheKeyStrategy.VALUE_CONTRIBUTED,
        cacheKeyExtractor = StackContext::material,
        matcherFactory = RuleFieldMatchers.string(StackContext::material, MissingValuePolicy.NEVER_MATCH),
    ),
    META(
        FieldType.NUMERIC,
        setOf("metadata"),
        cacheKeyStrategy = CacheKeyStrategy.IDENTITY_FIXED,
        cacheKeyExtractor = null,
        matcherFactory = RuleFieldMatchers.numeric(StackContext::metadata),
    ),
    SIZE(
        FieldType.NUMERIC,
        cacheKeyStrategy = CacheKeyStrategy.IDENTITY_FIXED,
        cacheKeyExtractor = null,
        matcherFactory = RuleFieldMatchers.numeric(StackContext::baseLimit),
    ),
    TAB(
        FieldType.STRING,
        contextProviders = setOf(RuleFieldContextProvider.TAB),
        cacheKeyStrategy = CacheKeyStrategy.VALUE_CONTRIBUTED,
        cacheKeyExtractor = StackContext::tab,
        matcherFactory = RuleFieldMatchers.string(StackContext::tab),
    ),
    ;

    init {
        // T6 护栏：只有 VALUE_CONTRIBUTED 字段可以并且必须声明 cacheKeyExtractor，
        // 其余策略禁止携带提取器——杜绝无护栏的手工键材料声明。
        require((cacheKeyStrategy == CacheKeyStrategy.VALUE_CONTRIBUTED) == (cacheKeyExtractor != null)) {
            "字段 $name: VALUE_CONTRIBUTED 必须声明 cacheKeyExtractor，其他策略不得携带"
        }
    }

    val id: String by lazy { name.lowercase() }
    private val matchers: Set<String> by lazy { aliases.mapTo(mutableSetOf(name)) { it.uppercase() } }

    /**
     * 该字段是否以值形式进入字段缓存键（仅 VALUE_CONTRIBUTED）。
     */
    fun contributesToCacheKey(): Boolean = cacheKeyStrategy == CacheKeyStrategy.VALUE_CONTRIBUTED

    /**
     * 该字段的求值输入是否完全由缓存键覆盖。
     *
     * 三种策略均覆盖（身份分量 / 字段键材料 / 身份稳定性契约）；
     * 新增 [CacheKeyStrategy] 条目时本 when 强制显式决定覆盖与否（穷尽检查），这是 T6 的编译期护栏。
     */
    fun isCacheKeyCovered(): Boolean = when (cacheKeyStrategy) {
        CacheKeyStrategy.IDENTITY_FIXED -> true
        CacheKeyStrategy.VALUE_CONTRIBUTED -> true
        CacheKeyStrategy.STABLE_VIA_IDENTITY -> true
    }

    /**
     * 编译单值字段比较。
     *
     * 编译产物包一层 [FieldScopedMatcher] 绑定字段身份，
     * [FieldMatcher.readFields] 才能机械推导出缓存键读取字段。
     */
    fun compileMatcher(operator: ComparisonOperator, literal: String): FieldMatcher = FieldScopedMatcher(this, matcherFactory.compile(operator, literal))

    /**
     * 编译列表字段比较，列表语义复用字段自身的等值 matcher。
     */
    fun compileListMatcher(literals: List<String>): FieldMatcher = FieldScopedMatcher(this, matcherFactory.compileList(literals))

    /**
     * 提取该字段贡献给规则缓存键的值。
     *
     * 非 VALUE_CONTRIBUTED 字段不应进入字段缓存键；误用时直接抛错（fail-fast），
     * 不再像旧实现那样静默返回空串。
     */
    internal fun cacheKeyValue(context: StackContext): String {
        val extractor = cacheKeyExtractor
        check(extractor != null) {
            "字段 $name 未声明缓存键提取器（策略 $cacheKeyStrategy），不应进入字段缓存键"
        }
        return extractor(context)
    }

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
