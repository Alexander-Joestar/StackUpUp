package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.rules.LocalizedMessage
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.RuleStepKind

object RuleComplexityAnalyzer {
    private const val RULE_COUNT_WARNING_THRESHOLD: Int = 80
    private const val RULE_LENGTH_WARNING_THRESHOLD: Int = 140
    private const val RULE_TOTAL_LENGTH_WARNING_THRESHOLD: Int = 4096

    fun analyze(snapshot: RuleSnapshot): RuleComplexityReport {
        val ruleCount = snapshot.rules.size
        val longestRuleLength = snapshot.rules.maxOfOrNull { it.sourceLine.length } ?: 0
        val totalRuleLength = snapshot.rules.sumOf { it.sourceLine.length }
        val warnings = buildList(3) {
            if (ruleCount >= RULE_COUNT_WARNING_THRESHOLD) {
                add(RuleComplexityWarning(StackUpUpIds.RULE_COMPLEXITY_RULE_COUNT_KEY, listOf(ruleCount)))
            }
            if (longestRuleLength >= RULE_LENGTH_WARNING_THRESHOLD) {
                add(RuleComplexityWarning(StackUpUpIds.RULE_COMPLEXITY_RULE_LENGTH_KEY, listOf(longestRuleLength)))
            }
            if (totalRuleLength >= RULE_TOTAL_LENGTH_WARNING_THRESHOLD) {
                add(RuleComplexityWarning(StackUpUpIds.RULE_COMPLEXITY_TOTAL_LENGTH_KEY, listOf(totalRuleLength)))
            }
            addAll(analyzeExplicitClampWarnings(snapshot))
        }

        return RuleComplexityReport(
            ruleCount = ruleCount,
            longestRuleLength = longestRuleLength,
            totalRuleLength = totalRuleLength,
            warnings = warnings
        )
    }

    private fun analyzeExplicitClampWarnings(snapshot: RuleSnapshot): List<RuleComplexityWarning> {
        val maxStackSize = StackUpUpConfig.maxStackSize
        return snapshot.rules.mapNotNull { rule ->
            val explicitSetValue = rule.action.steps
                .firstOrNull { step -> step.kind == RuleStepKind.SET }
                ?.value
                ?: return@mapNotNull null

            if (explicitSetValue <= maxStackSize) {
                return@mapNotNull null
            }

            RuleComplexityWarning(
                StackUpUpIds.RULE_LIMIT_CLAMP_KEY,
                listOf(rule.lineNumber, maxStackSize)
            )
        }
    }
}

data class RuleComplexityReport(
    val ruleCount: Int,
    val longestRuleLength: Int,
    val totalRuleLength: Int,
    val warnings: List<RuleComplexityWarning>
)

typealias RuleReloadWarning = LocalizedMessage
typealias RuleComplexityWarning = LocalizedMessage

