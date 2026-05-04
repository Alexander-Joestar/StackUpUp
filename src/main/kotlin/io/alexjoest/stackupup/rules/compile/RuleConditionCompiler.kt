package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.ComparisonOperator
import io.alexjoest.stackupup.rules.FieldType
import io.alexjoest.stackupup.rules.RuleField
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

    private fun compileList(condition: ListConditionAst): (RuleMatchContext) -> Boolean = when (condition.field.fieldType) {
        FieldType.ITEM -> compileItemList(condition.literals)
        FieldType.STRING -> compileSingleStringLiteralList(condition.literals, stringSelector(condition.field))
        FieldType.STRING_SET -> compileStringLiteralList(condition.literals, stringSetSelector(condition.field))
        FieldType.NUMERIC -> compileAny(
            condition.literals.map { compileField(FieldComparisonAst(condition.field, ComparisonOperator.EQUALS, it)) },
        )
    }

    private fun compileField(condition: FieldComparisonAst): (RuleMatchContext) -> Boolean = when (condition.field.fieldType) {
        FieldType.ITEM -> compileItemField(condition)
        FieldType.STRING -> compileSingleStringComparison(condition, stringSelector(condition.field))
        FieldType.STRING_SET -> compileStringComparison(condition, stringSetSelector(condition.field))
        FieldType.NUMERIC -> compileNumericField(condition, numericSelector(condition.field))
    }

    // -- 选择器，按 FieldType 分发 --

    private fun stringSelector(field: RuleField): (RuleMatchContext) -> String = when (field) {
        RuleField.MOD -> { c -> c.modId }
        RuleField.TYPE -> { c -> c.type }
        RuleField.TAB -> { c -> c.tab }
        RuleField.CATEGORY -> { c -> c.category }
        else -> throw IllegalStateException("field is not STRING: $field")
    }

    private fun stringSetSelector(field: RuleField): (RuleMatchContext) -> Iterable<String> = when (field) {
        RuleField.ORE -> { c -> c.oreNames }
        else -> throw IllegalStateException("field is not STRING_SET: $field")
    }

    private fun numericSelector(field: RuleField): (RuleMatchContext) -> Int = when (field) {
        RuleField.META -> { c -> c.meta }
        RuleField.SIZE -> { c -> c.baseSize }
        else -> throw IllegalStateException("field is not NUMERIC: $field")
    }

    // -- 编译器 --

    private fun compileItemField(condition: FieldComparisonAst): (RuleMatchContext) -> Boolean {
        val matcher = RuleLiteralMatcherCompiler.compileItemMatcher(condition.literal)
        return compileEqualityComparison(condition.operator, matcher)
    }

    private fun compileItemList(literals: List<String>): (RuleMatchContext) -> Boolean {
        val matchers = literals.map(RuleLiteralMatcherCompiler::compileItemMatcher)
        return compileAny(matchers)
    }

    private fun compileStringLiteralList(literals: List<String>, candidatesSelector: (RuleMatchContext) -> Iterable<String>): (RuleMatchContext) -> Boolean {
        val matcher = compileStringLiteralListMatcher(literals)
        return { context -> candidatesSelector(context).any(matcher) }
    }

    private fun compileSingleStringLiteralList(literals: List<String>, selector: (RuleMatchContext) -> String): (RuleMatchContext) -> Boolean {
        val matcher = compileStringLiteralListMatcher(literals)
        return { context -> matcher(selector(context)) }
    }

    private fun compileStringComparison(
        condition: FieldComparisonAst,
        candidatesSelector: (RuleMatchContext) -> Iterable<String>,
    ): (RuleMatchContext) -> Boolean {
        val matcher = RuleLiteralMatcherCompiler.compileStringMatcher(condition.literal)
        return compileEqualityComparison(condition.operator) { candidatesSelector(it).any(matcher) }
    }

    private fun compileSingleStringComparison(condition: FieldComparisonAst, selector: (RuleMatchContext) -> String): (RuleMatchContext) -> Boolean {
        val matcher = RuleLiteralMatcherCompiler.compileStringMatcher(condition.literal)
        return compileEqualityComparison(condition.operator) { matcher(selector(it)) }
    }

    private fun compileNumericField(condition: FieldComparisonAst, selector: (RuleMatchContext) -> Int): (RuleMatchContext) -> Boolean {
        val expected = condition.literal.toInt()
        return { matchesNumericComparison(condition.operator, selector(it), expected) }
    }

    private fun compileNestedConditions(conditions: List<ConditionAst>): List<(RuleMatchContext) -> Boolean> = conditions.map { compile(it) }

    private fun compileStringLiteralListMatcher(literals: List<String>): (String) -> Boolean {
        val exactLiterals = literals.filterNotTo(HashSet()) { '*' in it }
        val wildcardMatchers = literals.filter { '*' in it }.map(RuleLiteralMatcherCompiler::compileStringMatcher)
        return { actual -> actual in exactLiterals || wildcardMatchers.any { it(actual) } }
    }

    private fun compileEqualityComparison(operator: ComparisonOperator, matcher: (RuleMatchContext) -> Boolean): (RuleMatchContext) -> Boolean =
        { applyEqualityOperator(operator, matcher(it)) }

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

    private fun compileAny(predicates: List<(RuleMatchContext) -> Boolean>): (RuleMatchContext) -> Boolean = { ctx -> predicates.any { it(ctx) } }

    private fun compileAll(predicates: List<(RuleMatchContext) -> Boolean>): (RuleMatchContext) -> Boolean = { ctx -> predicates.all { it(ctx) } }
}
