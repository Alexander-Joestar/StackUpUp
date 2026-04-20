package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import java.io.File

internal data class RuleReloadState(
    val file: File,
    val snapshot: RuleSnapshot,
    val errors: List<String>,
    val warnings: List<RuleReloadWarning>
) {
    fun toReport(): RuleReloadReport {
        return RuleReloadReport(
            file = file,
            snapshot = snapshot,
            errors = errors,
            warnings = warnings
        )
    }
}

internal object RuleReloadPipeline {
    fun disabled(primaryRulesFile: File): RuleReloadState {
        return RuleReloadState(
            file = primaryRulesFile,
            snapshot = RuleSnapshot(0L, emptyList()),
            errors = emptyList(),
            warnings = emptyList()
        )
    }

    fun loadDslRules(primaryRulesFile: File, sourceFiles: List<File>): RuleReloadState {
        RuleFileTemplate.ensureExists(primaryRulesFile)
        val result = DslRuleSource.fromFiles(sourceFiles).load()
        return RuleReloadState(
            file = primaryRulesFile,
            snapshot = result.snapshot,
            errors = result.errors,
            warnings = RuleComplexityAnalyzer.analyze(result.snapshot).warnings
        )
    }
}
