package io.alexjoest.stackupup

import java.io.File
import io.alexjoest.stackupup.limit.OreDictIndex
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.rules.io.RuleFileLocator
import io.alexjoest.stackupup.rules.io.RuleReloadPipeline
import io.alexjoest.stackupup.rules.io.RuleReloadReport
import io.alexjoest.stackupup.rules.io.RuleReloadState
import io.alexjoest.stackupup.rules.io.RuleSourceLocator
import io.alexjoest.stackupup.rules.persist.RuleBlockFileStore
import io.alexjoest.stackupup.rules.persist.RuleTextBlock

object RuleRuntimeCoordinator {
    @Volatile
    private var lastReportState: RuleReloadReport = RuleReloadReport.empty(RuleFileLocator.resolve())

    fun lastReport(): RuleReloadReport = lastReportState

    fun reload(enableDslRules: Boolean = StackUpUpConfig.enableDslRules): RuleReloadReport {
        val primaryRulesFile = RuleFileLocator.resolve()
        val state = loadState(primaryRulesFile, enableDslRules)
        refreshRuntime(state, enableDslRules)
        StackSizeBackupRegistry.restoreAll()
        return state.toReport().also { lastReportState = it }
    }

    fun getRulesFile(): File = RuleFileLocator.resolve()

    fun getWorldRulesFile(): File? = RuleSourceLocator.resolveWorldFile()

    fun persistWorldRules(sourceId: String, lines: List<String>): Boolean {
        val worldFile = getWorldRulesFile() ?: return false
        RuleBlockFileStore(worldFile).replaceBlock(RuleTextBlock(id = sourceId, lines = lines))
        reload()
        return true
    }

    private fun loadState(primaryRulesFile: File, enableDslRules: Boolean): RuleReloadState =
        if (enableDslRules) {
            RuleReloadPipeline.loadDslRules(
                primaryRulesFile = primaryRulesFile,
                sourceFiles = RuleSourceLocator.resolveLoadOrder()
            )
        } else {
            RuleReloadPipeline.disabled(primaryRulesFile)
        }

    private fun refreshRuntime(state: RuleReloadState, enableDslRules: Boolean) {
        RuleRuntime.replaceSnapshot(state.snapshot)
        if (enableDslRules) {
            RuleRuntime.replaceOreDictIndex(OreDictIndex.createDefault())
        }
    }
}
