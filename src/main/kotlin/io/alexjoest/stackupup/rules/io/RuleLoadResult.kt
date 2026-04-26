package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.rules.compile.CompiledRule
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.LocalizedMessage

data class RuleLoadResult(
    val snapshot: RuleSnapshot,
    val errors: List<LocalizedMessage>
)

