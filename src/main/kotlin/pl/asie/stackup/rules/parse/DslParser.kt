package pl.asie.stackup.rules.parse

import pl.asie.stackup.rules.ast.AndConditionAst
import pl.asie.stackup.rules.ast.ConditionAst
import pl.asie.stackup.rules.ast.FieldComparisonAst
import pl.asie.stackup.rules.ast.ListConditionAst
import pl.asie.stackup.rules.ast.OrConditionAst
import pl.asie.stackup.rules.ast.RuleActionAst
import pl.asie.stackup.rules.ast.RuleAst

object DslParser {
    fun parseLine(line: String): RuleAst {
        val actionOperator = listOf("+=", "-=", "*=", "/=", "->").firstOrNull(line::contains)
            ?: error("规则必须包含动作运算符")
        val parts = line.split(actionOperator, limit = 2)
        require(parts.size == 2) { "规则必须包含动作运算符" }

        val left = parts[0].trim()
        val right = parts[1].trim()

        val action = RuleActionAst(actionOperator, right.toInt())
        return RuleAst(parseCondition(left), action)
    }

    private fun parseCondition(text: String): ConditionAst {
        val orParts = splitByOperator(text, "||")
        if (orParts.size > 1) {
            return OrConditionAst(orParts.map(::parseCondition))
        }

        if (text.contains(" in [")) {
            val field = text.substringBefore(" in ").trim()
            val body = text.substringAfter('[').substringBeforeLast(']')
            val literals = body.split(',').map(String::trim).filter(String::isNotEmpty)
            return ListConditionAst(field, literals)
        }

        if (text.count { it == '<' } == 2 && text.contains("size")) {
            val pieces = text.split('<').map(String::trim)
            return AndConditionAst(
                listOf(
                    FieldComparisonAst("size", ">", pieces[0]),
                    FieldComparisonAst("size", "<", pieces[2])
                )
            )
        }

        val conditions = splitByOperator(text, "&&").map(::parseSingleCondition)
        return if (conditions.size == 1) conditions.single() else AndConditionAst(conditions)
    }

    private fun splitByOperator(text: String, operator: String): List<String> {
        return text.split(operator).map(String::trim).filter(String::isNotEmpty)
    }

    private fun parseSingleCondition(text: String): ConditionAst {
        val operator = when {
            text.contains(">=") -> ">="
            text.contains("<=") -> "<="
            text.contains("!=") -> "!="
            text.contains('>') -> ">"
            text.contains('<') -> "<"
            text.contains('=') -> "="
            else -> error("不支持的条件：$text")
        }
        val parts = text.split(operator, limit = 2)
        return FieldComparisonAst(parts[0].trim(), operator, parts[1].trim())
    }
}
