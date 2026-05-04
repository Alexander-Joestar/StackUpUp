package io.alexjoest.stackupup.rules.ast

import io.alexjoest.stackupup.rules.model.RuleAction

data class RuleAst(val condition: ConditionAst, val action: RuleAction)
