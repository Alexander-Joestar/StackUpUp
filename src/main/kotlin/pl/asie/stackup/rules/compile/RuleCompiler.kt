package pl.asie.stackup.rules.compile

import pl.asie.stackup.rules.ast.AndConditionAst
import pl.asie.stackup.rules.ast.ConditionAst
import pl.asie.stackup.rules.ast.FieldComparisonAst
import pl.asie.stackup.rules.ast.ListConditionAst
import pl.asie.stackup.rules.ast.OrConditionAst
import pl.asie.stackup.rules.model.RuleAction
import pl.asie.stackup.rules.model.RuleMatchContext
import pl.asie.stackup.rules.parse.DslParser

object RuleCompiler {
    fun compileLine(line: String, lineNumber: Int): CompiledRule {
        val ast = DslParser.parseLine(line)
        return CompiledRule(
            lineNumber = lineNumber,
            sourceLine = line,
            action = RuleAction(ast.action.operator, ast.action.value),
            predicate = compileCondition(ast.condition)
        )
    }

    private fun compileCondition(condition: ConditionAst): (RuleMatchContext) -> Boolean {
        return when (condition) {
            is FieldComparisonAst -> compileField(condition)
            is ListConditionAst -> compileList(condition)
            is AndConditionAst -> {
                val compiled = condition.conditions.map(::compileCondition)
                return { context -> compiled.all { predicate -> predicate(context) } }
            }
            is OrConditionAst -> {
                val compiled = condition.conditions.map(::compileCondition)
                return { context -> compiled.any { predicate -> predicate(context) } }
            }
        }
    }

    private fun compileList(condition: ListConditionAst): (RuleMatchContext) -> Boolean {
        val predicates = condition.literals.map { literal ->
            compileField(FieldComparisonAst(condition.field, "=", literal))
        }
        return { context -> predicates.any { it(context) } }
    }

    private fun compileField(condition: FieldComparisonAst): (RuleMatchContext) -> Boolean {
        return when (condition.field) {
            "item" -> compileStringField(condition) { it.itemId }
            "mod" -> compileStringField(condition) { it.modId }
            "type" -> compileStringField(condition) { it.type }
            "ore" -> compileOreField(condition)
            "meta" -> compileNumericField(condition) { it.meta }
            "size" -> compileNumericField(condition) { it.baseSize }
            else -> { _ -> false }
        }
    }

    private fun compileStringField(
        condition: FieldComparisonAst,
        selector: (RuleMatchContext) -> String
    ): (RuleMatchContext) -> Boolean {
        return { context ->
            val actual = selector(context)
            when (condition.operator) {
                "=" -> matchesPattern(actual, condition.literal)
                "!=" -> !matchesPattern(actual, condition.literal)
                else -> false
            }
        }
    }

    private fun compileOreField(condition: FieldComparisonAst): (RuleMatchContext) -> Boolean {
        return { context ->
            when (condition.operator) {
                "=" -> context.oreNames.any { matchesPattern(it, condition.literal) }
                "!=" -> context.oreNames.none { matchesPattern(it, condition.literal) }
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
                "=" -> actual == expected
                "!=" -> actual != expected
                ">" -> actual > expected
                ">=" -> actual >= expected
                "<" -> actual < expected
                "<=" -> actual <= expected
                else -> false
            }
        }
    }

    private fun matchesPattern(actual: String, pattern: String): Boolean {
        if (!pattern.contains('*')) {
            return actual == pattern
        }

        val regex = buildString(pattern.length * 2) {
            append('^')
            for (char in pattern) {
                when (char) {
                    '*' -> append(".*")
                    '.', '(', ')', '[', ']', '{', '}', '+', '?', '^', '$', '|', '\\' -> {
                        append('\\')
                        append(char)
                    }
                    else -> append(char)
                }
            }
            append('$')
        }
        return Regex(regex).matches(actual)
    }
}
