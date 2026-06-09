package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.ast.AndConditionAst
import io.alexjoest.stackupup.rules.ast.ConditionAst
import io.alexjoest.stackupup.rules.ast.FieldComparisonAst
import io.alexjoest.stackupup.rules.ast.ListConditionAst
import io.alexjoest.stackupup.rules.ast.OrConditionAst
import io.alexjoest.stackupup.rules.model.RuleMatchContext

internal object RuleConditionCompiler {
    fun compile(condition: ConditionAst): (RuleMatchContext) -> Boolean = when (condition) {
        is FieldComparisonAst -> compileField(condition)
        is ListConditionAst -> compileList(condition)
        is AndConditionAst -> compileAll(compileNestedConditions(condition.conditions))
        is OrConditionAst -> compileAny(compileNestedConditions(condition.conditions))
    }

    private fun compileList(condition: ListConditionAst): (RuleMatchContext) -> Boolean =
        condition.field.compileListMatcher(condition.literals)

    private fun compileField(condition: FieldComparisonAst): (RuleMatchContext) -> Boolean =
        condition.field.compileMatcher(condition.operator, condition.literal)

    private fun compileNestedConditions(conditions: List<ConditionAst>): List<(RuleMatchContext) -> Boolean> =
        conditions.map { compile(it) }

    private fun compileAny(predicates: List<(RuleMatchContext) -> Boolean>): (RuleMatchContext) -> Boolean =
        { ctx -> predicates.any { it(ctx) } }

    private fun compileAll(predicates: List<(RuleMatchContext) -> Boolean>): (RuleMatchContext) -> Boolean =
        { ctx -> predicates.all { it(ctx) } }
}
