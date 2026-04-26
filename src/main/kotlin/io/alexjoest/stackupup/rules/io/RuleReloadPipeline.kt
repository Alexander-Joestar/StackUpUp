package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import java.io.File

internal object RuleReloadPipeline {
    fun disabled(primaryRulesFile: File): RuleReloadReport =
        RuleReloadReport(
            file = primaryRulesFile,
            snapshot = RuleSnapshot(0L, emptyList()),
            errors = emptyList(),
            warnings = emptyList()
        )

    fun loadDslRules(primaryRulesFile: File, sourceFiles: List<File>): RuleReloadReport {
        RuleFileTemplate.ensureExists(primaryRulesFile)
        val result = DslRuleSource.fromFiles(sourceFiles)
        return RuleReloadReport(
            file = primaryRulesFile,
            snapshot = result.snapshot,
            errors = result.errors,
            warnings = RuleComplexityAnalyzer.analyze(result.snapshot).warnings
        )
    }
}
