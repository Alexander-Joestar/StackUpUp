package io.alexjoest.stackupup

import io.alexjoest.stackupup.limit.OreDictIndex
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.rules.LocalizedMessage
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.io.RuleFileExampleTemplate
import io.alexjoest.stackupup.rules.io.RuleFileLocator
import io.alexjoest.stackupup.rules.io.RuleReloadPipeline
import io.alexjoest.stackupup.rules.io.RuleReloadReport
import io.alexjoest.stackupup.rules.io.RuleSourceLocator
import java.io.File

/**
 * 规则重载与运行时发布的统一协调入口。
 */
object RuleRuntimeCoordinator {
    @Volatile
    private var lastReportState: RuleReloadReport = RuleReloadReport(
        file = RuleFileLocator.resolve(),
        snapshot = RuleSnapshot(version = 0L, rules = emptyList()),
        errors = emptyList(),
        warnings = emptyList(),
    )

    /**
     * 返回最近一次规则重载报告。
     */
    fun lastReport(): RuleReloadReport = lastReportState

    /**
     * 重载规则文件、刷新运行时快照，并恢复已备份的堆叠上限。
     */
    fun reload(enableDslRules: Boolean = StackUpUpConfig.general.enableDslRules): RuleReloadReport {
        val primaryRulesFile = RuleFileLocator.resolve()
        // Refresh the example syntax reference file on every reload,
        // so it always reflects the latest DSL documentation regardless of user edits.
        RuleFileExampleTemplate.refreshExample(primaryRulesFile.parentFile)
        RuleFileExampleTemplate.refreshMarkdownExample(primaryRulesFile.parentFile)
        return try {
            val report = if (enableDslRules) {
                RuleReloadPipeline.loadDslRules(
                    primaryRulesFile = primaryRulesFile,
                    sourceFiles = RuleSourceLocator.resolveLoadOrder(),
                )
            } else {
                RuleReloadPipeline.disabled(primaryRulesFile)
            }
            val oreDictIndex = if (enableDslRules) OreDictIndex.createDefault() else RuleRuntime.oreDictIndex()
            RuleRuntime.replaceRuntime(report.snapshot, oreDictIndex)
            StackSizeBackupRegistry.restoreAll()
            report.also { lastReportState = it }
        } catch (ex: Exception) {
            val report = RuleReloadReport(
                file = primaryRulesFile,
                snapshot = RuleSnapshot(version = 0L, rules = emptyList()),
                errors = listOf(LocalizedMessage(ex.message ?: ex.javaClass.simpleName)),
                warnings = emptyList(),
            )
            lastReportState = report
            report
        }
    }

    /**
     * 返回主规则文件位置，供命令层打开编辑。
     */
    fun getRulesFile(): File = RuleFileLocator.resolve()
}
