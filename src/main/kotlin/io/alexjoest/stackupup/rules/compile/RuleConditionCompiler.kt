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
            is ListConditionAst -> compileList(condition)
            is AndConditionAst -> {
                val compiled = condition.conditions.map(::compile)
                ({ context -> compiled.all { predicate -> predicate(context) } })
            }
            is OrConditionAst -> {
                val compiled = condition.conditions.map(::compile)
                ({ context -> compiled.any { predicate -> predicate(context) } })
            }
        }
    }

    private fun compileList(condition: ListConditionAst): (RuleMatchContext) -> Boolean {
        return when (condition.field) {
            RuleField.ITEM -> compileItemList(condition.literals)
            RuleField.MOD -> compileStringList(condition.literals) { it.modId }
            RuleField.TYPE -> compileStringList(condition.literals) { it.type }
            RuleField.ORE -> compileOreList(condition.literals)
            else -> {
                val predicates = condition.literals.map { literal ->
                    compileField(FieldComparisonAst(condition.field, ComparisonOperator.EQUALS, literal))
                }
                ({ context -> predicates.any { predicate -> predicate(context) } })
            }
        }
    }

    private fun compileField(condition: FieldComparisonAst): (RuleMatchContext) -> Boolean {
        return when (condition.field) {
            RuleField.ITEM -> compileItemField(condition)
            RuleField.MOD -> compileStringField(condition) { it.modId }
            RuleField.TYPE -> compileStringField(condition) { it.type }
            RuleField.ORE -> compileOreField(condition)
            RuleField.META -> compileNumericField(condition) { it.meta }
            RuleField.SIZE -> compileNumericField(condition) { it.baseSize }
        }
    }

    private fun compileItemField(condition: FieldComparisonAst): (RuleMatchContext) -> Boolean {
        val matcher = RuleLiteralMatcherCompiler.compileItemMatcher(condition.literal)
        return { context ->
            when (condition.operator) {
                ComparisonOperator.EQUALS -> matcher(context)
                ComparisonOperator.NOT_EQUALS -> !matcher(context)
                else -> false
            }
        }
    }

    private fun compileItemList(literals: List<String>): (RuleMatchContext) -> Boolean {
        val matchers = literals.map(RuleLiteralMatcherCompiler::compileItemMatcher)
        return { context ->
            matchers.any { matcher -> matcher(context) }
        }
    }

    private fun compileStringList(
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

    private fun compileOreList(literals: List<String>): (RuleMatchContext) -> Boolean {
        val exactLiterals = literals.filterNot { '*' in it }.toHashSet()
        val wildcardMatchers = literals.filter { '*' in it }.map(RuleLiteralMatcherCompiler::compileStringMatcher)
        return { context ->
            context.oreNames.any { oreName ->
                oreName in exactLiterals || wildcardMatchers.any { matcher -> matcher(oreName) }
            }
        }
    }

    private fun compileStringField(
        condition: FieldComparisonAst,
        selector: (RuleMatchContext) -> String
    ): (RuleMatchContext) -> Boolean {
        val matcher = RuleLiteralMatcherCompiler.compileStringMatcher(condition.literal)
        return { context ->
            val actual = selector(context)
            when (condition.operator) {
                ComparisonOperator.EQUALS -> matcher(actual)
                ComparisonOperator.NOT_EQUALS -> !matcher(actual)
                else -> false
            }
        }
    }

    private fun compileOreField(condition: FieldComparisonAst): (RuleMatchContext) -> Boolean {
        val matcher = RuleLiteralMatcherCompiler.compileStringMatcher(condition.literal)
        return { context ->
            when (condition.operator) {
                ComparisonOperator.EQUALS -> context.oreNames.any(matcher)
                ComparisonOperator.NOT_EQUALS -> context.oreNames.none(matcher)
                else -> false
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
                ComparisonOperator.EQUALS -> actual == expected
                ComparisonOperator.NOT_EQUALS -> actual != expected
                ComparisonOperator.GREATER -> actual > expected
                ComparisonOperator.GREATER_EQUALS -> actual >= expected
                ComparisonOperator.LESS -> actual < expected
                ComparisonOperator.LESS_EQUALS -> actual <= expected
            }
        }
    }
}
