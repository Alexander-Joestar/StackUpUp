package io.alexjoest.stackupup.rules.ast

import io.alexjoest.stackupup.rules.RuleStepKind

data class RuleStepAst(
    val kind: RuleStepKind,
    val value: Int
) {
    val debugName: String
        get() = kind.debugName
}

data class RuleActionAst(
    val steps: List<RuleStepAst>
)

data class RuleAst(
    val condition: ConditionAst,
    val action: RuleActionAst
)

