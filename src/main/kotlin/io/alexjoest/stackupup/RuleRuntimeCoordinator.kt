package io.alexjoest.stackupup

import java.io.File
import io.alexjoest.stackupup.limit.OreDictIndex
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.io.RuleFileLocator
import io.alexjoest.stackupup.rules.io.RuleReloadPipeline
import io.alexjoest.stackupup.rules.io.RuleReloadReport
import io.alexjoest.stackupup.rules.io.RuleSourceLocator
import io.alexjoest.stackupup.rules.persist.WorldRuleStore

object RuleRuntimeCoordinator {
    @Volatile
    private var lastReportState: RuleReloadReport = emptyReport(RuleFileLocator.resolve())

    fun lastReport(): RuleReloadReport = lastReportState

    fun reload(enableDslRules: Boolean = StackUpUpConfig.enableDslRules): RuleReloadReport {
        val primaryRulesFile = RuleFileLocator.resolve()
        val state =
            if (enableDslRules) {
                RuleReloadPipeline.loadDslRules(
                    primaryRulesFile = primaryRulesFile,
                    sourceFiles = RuleSourceLocator.resolveLoadOrder()
                )
            } else {
                RuleReloadPipeline.disabled(primaryRulesFile)
            }

        RuleRuntime.replaceSnapshot(state.snapshot)
        enableDslRules.takeIf { it }?.let {
            RuleRuntime.replaceOreDictIndex(OreDictIndex.createDefault())
        }
        StackSizeBackupRegistry.restoreAll()
        return state.toReport().also { lastReportState = it }
    }

    fun getRulesFile(): File = RuleFileLocator.resolve()

    fun getWorldRulesFile(): File? = RuleSourceLocator.resolveWorldFile()

    fun persistWorldRules(sourceId: String, lines: List<String>): Boolean {
        val worldFile = getWorldRulesFile() ?: return false
        WorldRuleStore(worldFile).replaceSourceBlock(sourceId, lines)
        reload()
        return true
    }

    private fun emptyReport(file: File): RuleReloadReport =
        RuleReloadReport(
            file = file,
            snapshot = RuleSnapshot(version = 0L, rules = emptyList()),
            errors = emptyList(),
            warnings = emptyList()
        )
}
