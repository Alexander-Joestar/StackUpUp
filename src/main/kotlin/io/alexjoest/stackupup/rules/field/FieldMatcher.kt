package io.alexjoest.stackupup.rules.field

import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.rules.ComparisonOperator
import io.alexjoest.stackupup.rules.RuleField

/**
 * 字段条件 matcher 的 sealed 表达式树。
 *
 * 替代 `(StackContext) -> Boolean` 泛型闭包：比较在编译期定型为具体节点，
 * 热路径只做节点分派与原始类型比较，不产生 Function1 装箱与运行时运算符兜底。
 */
sealed interface FieldMatcher {
    fun matches(context: StackContext): Boolean

    /**
     * 该 matcher 子树读取的规则字段集合（T6 缓存键机械推导的唯一事实源）。
     *
     * 叶子节点不持有字段身份（只持 selector lambda），字段身份由
     * [RuleField.compileMatcher] 在编译点绑定为 [FieldScopedMatcher]；
     * 组合节点机械并集，不做手工维护。新增节点必须实现本方法（抽象成员强制）。
     */
    fun readFields(): Set<RuleField>
}

/**
 * 字段作用域节点：把 matcher 子树与它读取的 [RuleField] 绑定（T6）。
 *
 * 叶子节点只持有 `(StackContext) -> ...` selector，无法自我描述字段身份；
 * 字段身份在 [RuleField.compileMatcher]/[RuleField.compileListMatcher] 处已知，
 * 由该处包一层作用域节点，`readFields()` 才能沿树机械收集，杜绝手工维护 cacheKeyExtractor。
 */
internal class FieldScopedMatcher(val field: RuleField, private val inner: FieldMatcher) : FieldMatcher {
    private val fields: Set<RuleField> = setOf(field)

    override fun matches(context: StackContext): Boolean = inner.matches(context)

    override fun readFields(): Set<RuleField> = fields
}

/**
 * `item = *`：匹配所有可堆叠物品（原版 baseSize > 1）。
 */
internal class ItemStackableMatcher(private val negate: Boolean) : FieldMatcher {
    override fun matches(context: StackContext): Boolean {
        val matched = context.baseLimit > 1
        return if (negate) !matched else matched
    }

    // 无字段绑定的裸叶子：生产路径恒由 FieldScopedMatcher 包裹，此实现仅供工厂直接构造（测试）使用。
    override fun readFields(): Set<RuleField> = emptySet()
}

/**
 * `item = <pattern>[@meta]`：itemId 通配 pattern + 可选精确 meta。
 */
internal class ItemPatternMatcher(private val itemIdMatcher: StringMatcher, private val meta: Int?, private val negate: Boolean) : FieldMatcher {
    override fun matches(context: StackContext): Boolean {
        val matched = itemIdMatcher.matches(context.itemId) && (meta == null || meta == context.metadata)
        return if (negate) !matched else matched
    }

    // 无字段绑定的裸叶子：生产路径恒由 FieldScopedMatcher 包裹，此实现仅供工厂直接构造（测试）使用。
    override fun readFields(): Set<RuleField> = emptySet()
}

/**
 * 单值字符串字段（mod/type/material/tab）。
 *
 * NOT_EQUALS 在节点内取反，保证缺失值策略（NEVER_MATCH）先于取反生效。
 */
internal class StringFieldMatcher(
    private val selector: (StackContext) -> String,
    private val valueMatcher: StringMatcher,
    private val negate: Boolean,
    private val missingValuePolicy: MissingValuePolicy,
) : FieldMatcher {
    override fun matches(context: StackContext): Boolean {
        val actual = selector(context)
        if (actual.isEmpty() && missingValuePolicy == MissingValuePolicy.NEVER_MATCH) {
            return false
        }
        val matched = valueMatcher.matches(actual)
        return if (negate) !matched else matched
    }

    // 无字段绑定的裸叶子：生产路径恒由 FieldScopedMatcher 包裹，此实现仅供工厂直接构造（测试）使用。
    override fun readFields(): Set<RuleField> = emptySet()
}

/**
 * 纯字符串列表命中：成员无通配时编译为 HashSet，求值 O(1)。
 *
 * 列表条目不允许为空字符串（DSL 解析期拦截），因此缺失值策略在此路径与旧实现等价，
 * 不需要额外分支。
 */
internal class StringMembershipMatcher(private val selector: (StackContext) -> String, private val members: Set<String>) : FieldMatcher {
    override fun matches(context: StackContext): Boolean = selector(context) in members

    // 无字段绑定的裸叶子：生产路径恒由 FieldScopedMatcher 包裹，此实现仅供工厂直接构造（测试）使用。
    override fun readFields(): Set<RuleField> = emptySet()
}

/**
 * 字符串集合字段（ore）：逐元素命中 [StringMatcher]。
 */
internal class StringSetAnyMatcher(
    private val selector: (StackContext) -> Iterable<String>,
    private val valueMatcher: StringMatcher,
    private val negate: Boolean,
) : FieldMatcher {
    override fun matches(context: StackContext): Boolean {
        val matched = selector(context).any { valueMatcher.matches(it) }
        return if (negate) !matched else matched
    }

    // 无字段绑定的裸叶子：生产路径恒由 FieldScopedMatcher 包裹，此实现仅供工厂直接构造（测试）使用。
    override fun readFields(): Set<RuleField> = emptySet()
}

/**
 * 数值字段比较：直接编译为原始 int 比较，无 IntRange/装箱。
 */
internal class ComparisonFieldMatcher(private val operator: ComparisonOperator, private val expected: Int, private val selector: (StackContext) -> Int) :
    FieldMatcher {
    override fun matches(context: StackContext): Boolean {
        val actual = selector(context)
        return when (operator) {
            ComparisonOperator.EQUALS -> actual == expected
            ComparisonOperator.NOT_EQUALS -> actual != expected
            ComparisonOperator.GREATER -> actual > expected
            ComparisonOperator.GREATER_EQUALS -> actual >= expected
            ComparisonOperator.LESS -> actual < expected
            ComparisonOperator.LESS_EQUALS -> actual <= expected
        }
    }

    // 无字段绑定的裸叶子：生产路径恒由 FieldScopedMatcher 包裹，此实现仅供工厂直接构造（测试）使用。
    override fun readFields(): Set<RuleField> = emptySet()
}

/**
 * AND 组合节点（`&&` 与链式区间）。
 */
internal class AllOfFieldMatcher(private val matchers: List<FieldMatcher>) : FieldMatcher {
    override fun matches(context: StackContext): Boolean = matchers.all { it.matches(context) }

    override fun readFields(): Set<RuleField> = collectReadFields(matchers)
}

/**
 * OR 组合节点（`||` 与列表条件）。
 */
internal class AnyOfFieldMatcher(private val matchers: List<FieldMatcher>) : FieldMatcher {
    override fun matches(context: StackContext): Boolean = matchers.any { it.matches(context) }

    override fun readFields(): Set<RuleField> = collectReadFields(matchers)
}

/**
 * 机械并集：按子节点顺序去重收集，保证缓存键字段顺序确定（插入序 = 子节点序）。
 */
private fun collectReadFields(matchers: List<FieldMatcher>): Set<RuleField> {
    val fields = LinkedHashSet<RuleField>()
    for (matcher in matchers) {
        fields += matcher.readFields()
    }
    return fields
}

/**
 * 字符串值级 pattern：不直接消费 StackContext，由字符串/字符串集合/物品节点复用。
 */
internal sealed interface StringMatcher {
    fun matches(actual: String): Boolean
}

/**
 * 无通配 pattern：精确字符串比较。
 */
internal class ExactStringMatcher(private val expected: String) : StringMatcher {
    override fun matches(actual: String): Boolean = actual == expected
}

/**
 * 含 `*` 的 pattern：编译期转为锚定正则，运行时只做 Regex.matches。
 */
internal class WildcardStringMatcher(private val regex: Regex) : StringMatcher {
    override fun matches(actual: String): Boolean = regex.matches(actual)
}
