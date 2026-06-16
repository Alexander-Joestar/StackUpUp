package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.rules.RuleMessageKey
import io.alexjoest.stackupup.rules.RuleMessages
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import java.io.File

internal object RuleReloadPipeline {
    fun disabled(primaryRulesFile: File): RuleReloadReport = RuleReloadReport(
        file = primaryRulesFile,
        snapshot = RuleSnapshot(0L, emptyList()),
        errors = emptyList(),
        warnings = emptyList(),
    )

    fun loadDslRules(primaryRulesFile: File, sourceFiles: List<File>): RuleReloadReport = try {
        RuleFileTemplate.ensureExists(primaryRulesFile)
        val markdownFiles = sourceFiles.filter { it.name.endsWith(".su.md") }
        val dslFiles = sourceFiles.filterNot { it.name.endsWith(".su.md") }
        val gateContext = RuleGateContext.fromLoadedMods()
        val parsedMarkdownFiles = markdownFiles.mapNotNull { file ->
            if (!file.exists()) {
                null
            } else {
                ParsedMarkdownFile(file.name, MarkdownStateParser.parse(file.readLines(Charsets.UTF_8)))
            }
        }
        val stateErrors = parsedMarkdownFiles.flatMap { it.document.errors }
        val markdownResult = MarkdownRuleSource.fromParsedFiles(parsedMarkdownFiles, gateContext)
        val dslResult = DslRuleSource.fromFiles(dslFiles, gateContext)
        val mergedRules = RuleLoadResult(
            snapshot = RuleSnapshot(
                version = maxOf(markdownResult.snapshot.version, dslResult.snapshot.version),
                rules = markdownResult.snapshot.rules + dslResult.snapshot.rules,
            ),
            errors = markdownResult.errors + dslResult.errors,
        )
        RuleReloadReport(
            file = primaryRulesFile,
            snapshot = mergedRules.snapshot,
            warnings = RuleComplexityAnalyzer.analyze(mergedRules.snapshot),
            errors = mergedRules.errors + stateErrors.map { RuleMessages.message(RuleMessageKey.STATE_ERROR_PREFIX, it) },
        )
    } catch (ex: Exception) {
        RuleReloadReport(
            file = primaryRulesFile,
            snapshot = RuleSnapshot(0L, emptyList()),
            errors = listOf(RuleMessages.message(RuleMessageKey.UNKNOWN_ERROR, ex.message ?: ex.javaClass.simpleName)),
            warnings = emptyList(),
        )
    }
}
