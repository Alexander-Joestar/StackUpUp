package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.rules.RuleField
import io.alexjoest.stackupup.rules.field.FieldMatcher
import io.alexjoest.stackupup.rules.model.RuleAction

data class CompiledRule(
    val lineNumber: Int,
    val sourceLine: String,
    val action: RuleAction,
    val referencedFields: Set<RuleField>,
    internal val matcher: FieldMatcher,
) {
    fun matches(context: StackContext): Boolean = matcher.matches(context)
}
