package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.model.RuleAction
import io.alexjoest.stackupup.rules.model.RuleMatchContext

data class CompiledRule(
    val lineNumber: Int,
    val sourceLine: String,
    val action: RuleAction,
    val referencedFields: Set<String>,
    val predicate: (RuleMatchContext) -> Boolean
) {
    fun matches(context: RuleMatchContext): Boolean = predicate(context)
}

