package io.alexjoest.stackupup.rules.io

import java.io.File
import io.alexjoest.stackupup.rules.LocalizedMessage
import io.alexjoest.stackupup.rules.compile.RuleSnapshot

data class RuleReloadReport(
    val file: File,
    val snapshot: RuleSnapshot,
    val errors: List<LocalizedMessage>,
    val warnings: List<RuleReloadWarning>
) {
    val isClean: Boolean get() = errors.isEmpty() && warnings.isEmpty()
}
