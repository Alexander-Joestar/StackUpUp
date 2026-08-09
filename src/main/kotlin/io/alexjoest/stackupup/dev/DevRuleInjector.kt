package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.rules.LocalizedMessage
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.io.DslRuleSource

/**
 * 开发期临时规则注入器。
 *
 * 只在自动验收开启时追加临时 DSL 规则，用于验证“矿辞/metadata/真实栈上限”整条链路是否已经打通。
 * 按规则行跟踪已注入内容，保证同一规则行重复调用不会叠加（幂等）；不同规则行可以先后注入，
 * 例如单场景先注入 `tempRule`、再注入边界探针自带的 vanilla 规则。
 */
object DevRuleInjector {
    private val injectedRuleLines: MutableSet<String> = LinkedHashSet()

    fun ensureInjected(ruleLine: String): DevRuleInjectionResult = ensureInjected(listOf(ruleLine))

    /**
     * 批量注入：全部规则行一次解析、一次替换快照；任一行解析失败则整体失败，不注入任何行。
     */
    fun ensureInjected(ruleLines: List<String>): DevRuleInjectionResult {
        val pending = ruleLines.map(String::trim).filter(String::isNotEmpty).filterNot(injectedRuleLines::contains)
        if (pending.isEmpty()) {
            return DevRuleInjectionResult.Skipped
        }

        val loaded = DslRuleSource.fromLines(pending)
        if (loaded.errors.isNotEmpty()) {
            return DevRuleInjectionResult.Failed(loaded.errors)
        }

        val current = RuleRuntime.currentSnapshot()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = System.nanoTime(),
                rules = current.rules + loaded.snapshot.rules,
            ),
        )
        injectedRuleLines.addAll(pending)
        return DevRuleInjectionResult.Applied(
            ruleLines = pending,
            previousRuleCount = current.rules.size,
            newRuleCount = RuleRuntime.currentSnapshot().rules.size,
        )
    }

    internal fun resetForTests() {
        injectedRuleLines.clear()
    }
}

sealed class DevRuleInjectionResult {
    data object Skipped : DevRuleInjectionResult()

    data class Applied(val ruleLines: List<String>, val previousRuleCount: Int, val newRuleCount: Int) : DevRuleInjectionResult() {
        /** 单条注入时保留旧日志契约：直接可读的规则行文本。 */
        val ruleLine: String
            get() = ruleLines.joinToString("；")
    }

    data class Failed(val errors: List<LocalizedMessage>) : DevRuleInjectionResult()
}
