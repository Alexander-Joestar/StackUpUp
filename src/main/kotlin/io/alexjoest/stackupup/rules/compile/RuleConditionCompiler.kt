package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.ComparisonOperator
import io.alexjoest.stackupup.rules.ast.AndConditionAst
import io.alexjoest.stackupup.rules.ast.ConditionAst
import io.alexjoest.stackupup.rules.ast.FieldComparisonAst
import io.alexjoest.stackupup.rules.ast.ListConditionAst
import io.alexjoest.stackupup.rules.ast.OrConditionAst
import io.alexjoest.stackupup.rules.ast.RangeConditionAst
import io.alexjoest.stackupup.rules.field.AllOfFieldMatcher
import io.alexjoest.stackupup.rules.field.AnyOfFieldMatcher
import io.alexjoest.stackupup.rules.field.FieldMatcher

internal object RuleConditionCompiler {
    fun compile(condition: ConditionAst): FieldMatcher = when (condition) {
        is FieldComparisonAst -> condition.field.compileMatcher(condition.operator, condition.literal)
        is ListConditionAst -> condition.field.compileListMatcher(condition.literals)
        is RangeConditionAst -> {
            // 区间按 RangeConditionAst 文档：编译为 field 对 lower/upper 的两条原始比较，
            // 与旧 AndConditionAst 脱糖语义一致；空区间天然永不命中。
            val lowerOperator = if (condition.lowerInclusive) ComparisonOperator.GREATER_EQUALS else ComparisonOperator.GREATER
            val upperOperator = if (condition.upperInclusive) ComparisonOperator.LESS_EQUALS else ComparisonOperator.LESS
            AllOfFieldMatcher(
                listOf(
                    condition.field.compileMatcher(lowerOperator, condition.lower),
                    condition.field.compileMatcher(upperOperator, condition.upper),
                ),
            )
        }
        is AndConditionAst -> AllOfFieldMatcher(condition.conditions.map { compile(it) })
        is OrConditionAst -> AnyOfFieldMatcher(condition.conditions.map { compile(it) })
    }
}
