package pl.asie.stackup.rules.compile

import pl.asie.stackup.rules.model.RuleAction
import pl.asie.stackup.rules.model.RuleMatchContext

data class CompiledRule(
    val lineNumber: Int,
    val sourceLine: String,
    val action: RuleAction,
    val predicate: (RuleMatchContext) -> Boolean
) {
    fun matches(context: RuleMatchContext): Boolean = predicate(context)
}
