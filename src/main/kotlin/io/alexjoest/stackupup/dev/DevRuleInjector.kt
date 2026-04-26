package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.rules.LocalizedMessage
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.io.DslRuleSource

/**
 * 开发期临时规则注入器。
 *
 * 只在自动验收开启时追加一条临时 DSL 规则，
 * 用于验证“矿辞/metadata/真实栈上限”整条链路是否已经打通。
 */
object DevRuleInjector {
    private var injected: Boolean = false

    fun ensureInjected(ruleLine: String): DevRuleInjectionResult {
        if (injected || ruleLine.isBlank()) {
            return DevRuleInjectionResult.Skipped
        }

        val loaded = DslRuleSource.fromLines(listOf(ruleLine))
        if (loaded.errors.isNotEmpty()) {
            return DevRuleInjectionResult.Failed(loaded.errors)
        }

        val current = RuleRuntime.currentSnapshot()
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = System.nanoTime(),
                rules = current.rules + loaded.snapshot.rules
            )
        )
        injected = true
        return DevRuleInjectionResult.Applied(ruleLine, current.rules.size, RuleRuntime.currentSnapshot().rules.size)
    }
}

sealed class DevRuleInjectionResult {
    data object Skipped : DevRuleInjectionResult()
    data class Applied(val ruleLine: String, val previousRuleCount: Int, val newRuleCount: Int) : DevRuleInjectionResult()
    data class Failed(val errors: List<LocalizedMessage>) : DevRuleInjectionResult()
}


