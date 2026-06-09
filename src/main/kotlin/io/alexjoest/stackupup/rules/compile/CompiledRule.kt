@file:Suppress("DEPRECATION")

package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.rules.RuleField
import io.alexjoest.stackupup.rules.model.RuleAction
import io.alexjoest.stackupup.rules.model.RuleMatchContext

data class CompiledRule(
    val lineNumber: Int,
    val sourceLine: String,
    val action: RuleAction,
    val referencedFields: Set<RuleField>,
    val predicate: (StackContext) -> Boolean,
) {
    fun matches(context: StackContext): Boolean = predicate(context)

    @Deprecated("Use matches(StackContext).")
    fun matches(context: RuleMatchContext): Boolean = predicate(context.toStackContext())
}
