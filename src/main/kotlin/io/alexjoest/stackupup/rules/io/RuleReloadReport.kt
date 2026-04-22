package io.alexjoest.stackupup.rules.io

import java.io.File
import io.alexjoest.stackupup.rules.compile.RuleSnapshot

data class RuleReloadReport(
    val file: File,
    val snapshot: RuleSnapshot,
    val errors: List<String>,
    val warnings: List<RuleReloadWarning>
) {
    val complexityWarnings: List<String> get() = warnings.map(RuleReloadWarning::translationKey)
    val shouldWarn: Boolean get() = warnings.isNotEmpty()
    val isClean: Boolean get() = errors.isEmpty() && warnings.isEmpty()

    companion object {
        fun empty(file: File): RuleReloadReport =
            RuleReloadReport(
                file = file,
                snapshot = RuleSnapshot(version = 0L, rules = emptyList()),
                errors = emptyList(),
                warnings = emptyList()
            )
    }
}
