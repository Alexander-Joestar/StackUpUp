package io.alexjoest.stackupup

import java.io.File
import io.alexjoest.stackupup.limit.OreDictIndex
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.rules.io.RuleFileLocator
import io.alexjoest.stackupup.rules.io.RuleReloadPipeline
import io.alexjoest.stackupup.rules.io.RuleReloadReport
import io.alexjoest.stackupup.rules.io.RuleSourceLocator
import io.alexjoest.stackupup.rules.persist.RuleBlockFileStore
import io.alexjoest.stackupup.rules.persist.RuleTextBlock

object RuleRuntimeCoordinator {
    @Volatile
    private var lastReportState: RuleReloadReport = RuleReloadReport(
        file = RuleFileLocator.resolve(),
        snapshot = io.alexjoest.stackupup.rules.compile.RuleSnapshot(version = 0L, rules = emptyList()),
        errors = emptyList(),
        warnings = emptyList()
    )

    fun lastReport(): RuleReloadReport = lastReportState

    fun reload(enableDslRules: Boolean = StackUpUpConfig.enableDslRules): RuleReloadReport {
        val primaryRulesFile = RuleFileLocator.resolve()
        val report = loadState(primaryRulesFile, enableDslRules)
        refreshRuntime(report, enableDslRules)
        StackSizeBackupRegistry.restoreAll()
        return report.also { lastReportState = it }
    }

    fun getRulesFile(): File = RuleFileLocator.resolve()

    fun getWorldRulesFile(): File? = RuleSourceLocator.resolveWorldFile()

    fun persistWorldRules(sourceId: String, lines: List<String>): Boolean {
        val worldFile = getWorldRulesFile() ?: return false
        RuleBlockFileStore(worldFile).replaceBlock(RuleTextBlock(id = sourceId, lines = lines))
        reload()
        return true
    }

    private fun loadState(primaryRulesFile: File, enableDslRules: Boolean): RuleReloadReport =
        if (enableDslRules) {
            RuleReloadPipeline.loadDslRules(
                primaryRulesFile = primaryRulesFile,
                sourceFiles = RuleSourceLocator.resolveLoadOrder()
            )
        } else {
            RuleReloadPipeline.disabled(primaryRulesFile)
        }

    private fun refreshRuntime(report: RuleReloadReport, enableDslRules: Boolean) {
        RuleRuntime.replaceSnapshot(report.snapshot)
        if (enableDslRules) {
            RuleRuntime.replaceOreDictIndex(OreDictIndex.createDefault())
        }
    }
}
