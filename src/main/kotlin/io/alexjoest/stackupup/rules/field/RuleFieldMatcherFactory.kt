package io.alexjoest.stackupup.rules.field

import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.rules.ComparisonOperator

/**
 * 字段条件 matcher 工厂。
 *
 * 字段只负责把比较表达式编译成命中判断，堆叠数量仍由规则 action 决定。
 */
internal fun interface RuleFieldMatcherFactory {
    fun compile(operator: ComparisonOperator, literal: String): (StackContext) -> Boolean

    fun compileList(literals: List<String>): (StackContext) -> Boolean {
        val predicates = literals.map { compile(ComparisonOperator.EQUALS, it) }
        return { context -> predicates.any { it(context) } }
    }
}

/**
 * 字段值缺失时的比较策略。
 */
internal enum class MissingValuePolicy {
    EMPTY_VALUE,
    NEVER_MATCH,
}

/**
 * 内置字段 matcher 集合。
 *
 * 保持不同字段的特殊语义集中在这里，避免条件编译器按字段名重复分发。
 */
internal object RuleFieldMatchers {
    fun item(): RuleFieldMatcherFactory = RuleFieldMatcherFactory { operator, literal ->
        val matcher = RuleLiteralMatcherCompiler.compileItemMatcher(literal)
        compileEqualityComparison(operator, matcher)
    }

    fun string(
        selector: (StackContext) -> String,
        missingValuePolicy: MissingValuePolicy = MissingValuePolicy.EMPTY_VALUE,
    ): RuleFieldMatcherFactory = RuleFieldMatcherFactory { operator, literal ->
        val matcher = RuleLiteralMatcherCompiler.compileStringMatcher(literal)
        return@RuleFieldMatcherFactory { context ->
            val actual = selector(context)
            if (actual.isEmpty() && missingValuePolicy == MissingValuePolicy.NEVER_MATCH) {
                false
            } else {
                applyEqualityOperator(operator, matcher(actual))
            }
        }
    }

    fun stringSet(selector: (StackContext) -> Iterable<String>): RuleFieldMatcherFactory =
        RuleFieldMatcherFactory { operator, literal ->
            val matcher = RuleLiteralMatcherCompiler.compileStringMatcher(literal)
            compileEqualityComparison(operator) { context -> selector(context).any(matcher) }
        }

    fun numeric(selector: (StackContext) -> Int): RuleFieldMatcherFactory =
        RuleFieldMatcherFactory { operator, literal ->
            val expected = literal.toInt()
            return@RuleFieldMatcherFactory { context -> matchesNumericComparison(operator, selector(context), expected) }
        }

    private fun compileEqualityComparison(
        operator: ComparisonOperator,
        matcher: (StackContext) -> Boolean,
    ): (StackContext) -> Boolean = { applyEqualityOperator(operator, matcher(it)) }

    private fun applyEqualityOperator(op: ComparisonOperator, matches: Boolean): Boolean = when (op) {
        ComparisonOperator.EQUALS -> matches
        ComparisonOperator.NOT_EQUALS -> !matches
        else -> false
    }

    private fun matchesNumericComparison(op: ComparisonOperator, actual: Int, expected: Int): Boolean = when (op) {
        ComparisonOperator.EQUALS -> actual == expected
        ComparisonOperator.NOT_EQUALS -> actual != expected
        ComparisonOperator.GREATER -> actual > expected
        ComparisonOperator.GREATER_EQUALS -> actual >= expected
        ComparisonOperator.LESS -> actual < expected
        ComparisonOperator.LESS_EQUALS -> actual <= expected
    }
}
