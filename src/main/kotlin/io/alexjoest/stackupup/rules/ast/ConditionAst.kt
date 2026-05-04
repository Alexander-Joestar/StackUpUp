package io.alexjoest.stackupup.rules.ast

import io.alexjoest.stackupup.rules.ComparisonOperator
import io.alexjoest.stackupup.rules.RuleField
import java.util.LinkedHashSet

sealed interface ConditionAst {
    fun debugFields(): List<String>
    fun debugLiteralCount(): Int
}

data class FieldComparisonAst(val field: RuleField, val operator: ComparisonOperator, val literal: String) : ConditionAst {
    override fun debugFields(): List<String> = listOf(field.id)
    override fun debugLiteralCount(): Int = 1
}

data class ListConditionAst(val field: RuleField, val literals: List<String>) : ConditionAst {
    override fun debugFields(): List<String> = listOf(field.id)
    override fun debugLiteralCount(): Int = literals.size
}

data class AndConditionAst(val conditions: List<ConditionAst>) : ConditionAst {
    override fun debugFields(): List<String> = collectDebugFields(conditions)
    override fun debugLiteralCount(): Int = conditions.sumOf(ConditionAst::debugLiteralCount)
}

data class OrConditionAst(val conditions: List<ConditionAst>) : ConditionAst {
    override fun debugFields(): List<String> = collectDebugFields(conditions)
    override fun debugLiteralCount(): Int = conditions.sumOf(ConditionAst::debugLiteralCount)
}

private fun collectDebugFields(conditions: List<ConditionAst>): List<String> {
    val fields = LinkedHashSet<String>()
    for (condition in conditions) {
        fields += condition.debugFields()
    }
    return fields.toList()
}
