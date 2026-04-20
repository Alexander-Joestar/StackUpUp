package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.rules.compile.RuleSnapshot

data class RuleLoadResult(
    val snapshot: RuleSnapshot,
    val errors: List<String>
)

