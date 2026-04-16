package pl.asie.stackup.rules.ast

sealed interface ConditionAst {
    fun debugFields(): List<String>

    fun debugLiteralCount(): Int
}

data class FieldComparisonAst(
    val field: String,
    val operator: String,
    val literal: String
) : ConditionAst {
    override fun debugFields(): List<String> = listOf(field)

    override fun debugLiteralCount(): Int = 1
}

data class ListConditionAst(
    val field: String,
    val literals: List<String>
) : ConditionAst {
    override fun debugFields(): List<String> = listOf(field)

    override fun debugLiteralCount(): Int = literals.size
}

data class AndConditionAst(
    val conditions: List<ConditionAst>
) : ConditionAst {
    override fun debugFields(): List<String> = conditions.flatMap(ConditionAst::debugFields).distinct()

    override fun debugLiteralCount(): Int = conditions.sumOf(ConditionAst::debugLiteralCount)
}

data class OrConditionAst(
    val conditions: List<ConditionAst>
) : ConditionAst {
    override fun debugFields(): List<String> = conditions.flatMap(ConditionAst::debugFields).distinct()

    override fun debugLiteralCount(): Int = conditions.sumOf(ConditionAst::debugLiteralCount)
}
