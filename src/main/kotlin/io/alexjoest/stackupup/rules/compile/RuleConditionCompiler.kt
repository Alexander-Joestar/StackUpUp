package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.ComparisonOperator
import io.alexjoest.stackupup.rules.RuleField
import io.alexjoest.stackupup.rules.ast.AndConditionAst
import io.alexjoest.stackupup.rules.ast.ConditionAst
import io.alexjoest.stackupup.rules.ast.FieldComparisonAst
import io.alexjoest.stackupup.rules.ast.ListConditionAst
import io.alexjoest.stackupup.rules.ast.OrConditionAst
import io.alexjoest.stackupup.rules.model.RuleMatchContext

internal object RuleConditionCompiler {
    fun compile(condition: ConditionAst): (RuleMatchContext) -> Boolean {
        return when (condition) {
            is FieldComparisonAst -> compileField(condition)
            is ListConditionAst   -> compileList(condition)
            is AndConditionAst    -> compileAll(compileNestedConditions(condition.conditions))
            is OrConditionAst     -> compileAny(compileNestedConditions(condition.conditions))
        }
    }

    private fun compileList(condition: ListConditionAst): (RuleMatchContext) -> Boolean {
        return when (condition.field) {
            RuleField.ITEM -> compileItemList(condition.literals)
            RuleField.MOD  -> compileSingleStringLiteralList(condition.literals) { it.modId }
            RuleField.TYPE -> compileSingleStringLiteralList(condition.literals) { it.type }
            RuleField.ORE  -> compileStringLiteralList(condition.literals) { it.oreNames }
            else           -> compileAny(
                condition.literals.map { literal ->
                    compileField(FieldComparisonAst(condition.field, ComparisonOperator.EQUALS, literal))
                }
            )
        }
    }

    private fun compileField(condition: FieldComparisonAst): (RuleMatchContext) -> Boolean {
        return when (condition.field) {
            RuleField.ITEM -> compileItemField(condition)
            RuleField.MOD  -> compileSingleStringComparison(condition) { it.modId }
            RuleField.TYPE -> compileSingleStringComparison(condition) { it.type }
            RuleField.ORE  -> compileStringComparison(condition) { it.oreNames }
            RuleField.META -> compileNumericField(condition) { it.meta }
            RuleField.SIZE -> compileNumericField(condition) { it.baseSize }
        }
    }

    private fun compileItemField(condition: FieldComparisonAst): (RuleMatchContext) -> Boolean {
        val matcher = RuleLiteralMatcherCompiler.compileItemMatcher(condition.literal)
        return { context ->
            when (condition.operator) {
                ComparisonOperator.EQUALS     -> matcher(context)
                ComparisonOperator.NOT_EQUALS -> !matcher(context)
                else                          -> false
            }
        }
    }

    private fun compileItemList(literals: List<String>): (RuleMatchContext) -> Boolean {
        return compileAny(literals.map(RuleLiteralMatcherCompiler::compileItemMatcher))
    }

    private fun compileStringLiteralList(
        literals: List<String>,
        candidatesSelector: (RuleMatchContext) -> Iterable<String>
    ): (RuleMatchContext) -> Boolean {
        val exactLiterals = literals.filterNot { '*' in it }.toHashSet()
        val wildcardMatchers = literals.filter { '*' in it }.map(RuleLiteralMatcherCompiler::compileStringMatcher)
        return { context ->
            candidatesSelector(context).any { candidate ->
                candidate in exactLiterals || wildcardMatchers.any { matcher -> matcher(candidate) }
            }
        }
    }

    private fun compileSingleStringLiteralList(
        literals: List<String>,
        selector: (RuleMatchContext) -> String
    ): (RuleMatchContext) -> Boolean {
        val exactLiterals = literals.filterNot { '*' in it }.toHashSet()
        val wildcardMatchers = literals.filter { '*' in it }.map(RuleLiteralMatcherCompiler::compileStringMatcher)
        return { context ->
            val actual = selector(context)
            actual in exactLiterals || wildcardMatchers.any { matcher -> matcher(actual) }
        }
    }

    private fun compileStringComparison(
        condition: FieldComparisonAst,
        candidatesSelector: (RuleMatchContext) -> Iterable<String>
    ): (RuleMatchContext) -> Boolean {
        val matcher = RuleLiteralMatcherCompiler.compileStringMatcher(condition.literal)
        return { context ->
            val matches = candidatesSelector(context).any(matcher)
            when (condition.operator) {
                ComparisonOperator.EQUALS     -> matches
                ComparisonOperator.NOT_EQUALS -> !matches
                else                          -> false
            }
        }
    }

    private fun compileSingleStringComparison(
        condition: FieldComparisonAst,
        selector: (RuleMatchContext) -> String
    ): (RuleMatchContext) -> Boolean {
        val matcher = RuleLiteralMatcherCompiler.compileStringMatcher(condition.literal)
        return { context ->
            val matches = matcher(selector(context))
            when (condition.operator) {
                ComparisonOperator.EQUALS     -> matches
                ComparisonOperator.NOT_EQUALS -> !matches
                else                          -> false
            }
        }
    }

    private fun compileNumericField(
        condition: FieldComparisonAst,
        selector: (RuleMatchContext) -> Int
    ): (RuleMatchContext) -> Boolean {
        val expected = condition.literal.toInt()
        return { context ->
            val actual = selector(context)
            when (condition.operator) {
                ComparisonOperator.EQUALS         -> actual == expected
                ComparisonOperator.NOT_EQUALS     -> actual != expected
                ComparisonOperator.GREATER        -> actual > expected
                ComparisonOperator.GREATER_EQUALS -> actual >= expected
                ComparisonOperator.LESS           -> actual < expected
                ComparisonOperator.LESS_EQUALS    -> actual <= expected
            }
        }
    }

    private fun compileNestedConditions(conditions: List<ConditionAst>): List<(RuleMatchContext) -> Boolean> {
        val compiled = ArrayList<(RuleMatchContext) -> Boolean>(conditions.size)
        for (nested in conditions) {
            compiled += compile(nested)
        }
        return compiled
    }

    private fun compileAny(predicates: List<(RuleMatchContext) -> Boolean>): (RuleMatchContext) -> Boolean =
        { context -> predicates.any { predicate -> predicate(context) } }

    private fun compileAll(predicates: List<(RuleMatchContext) -> Boolean>): (RuleMatchContext) -> Boolean =
        { context -> predicates.all { predicate -> predicate(context) } }
}
