package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.rules.compile.CompiledRule
import io.alexjoest.stackupup.rules.compile.RuleSnapshot

data class RuleLoadResult(
    val snapshot: RuleSnapshot,
    val errors: List<String>
) {
    companion object {
        fun compiled(rules: List<CompiledRule>, errors: List<String>): RuleLoadResult =
            RuleLoadResult(
                snapshot = RuleSnapshot(
                    version = System.nanoTime(),
                    rules = rules
                ),
                errors = errors
            )
    }
}

