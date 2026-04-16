package pl.asie.stackup.rules.ast

data class RuleActionAst(
    val operator: String,
    val value: Int
)

data class RuleAst(
    val condition: ConditionAst,
    val action: RuleActionAst
)
