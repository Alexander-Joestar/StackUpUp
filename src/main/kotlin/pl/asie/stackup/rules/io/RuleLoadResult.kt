package pl.asie.stackup.rules.io

import pl.asie.stackup.rules.compile.RuleSnapshot

data class RuleLoadResult(
    val snapshot: RuleSnapshot,
    val errors: List<String>
)
