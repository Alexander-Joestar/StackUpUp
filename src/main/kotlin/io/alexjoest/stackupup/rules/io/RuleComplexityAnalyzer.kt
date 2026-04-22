package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.rules.compile.RuleSnapshot

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
                add(RuleComplexityWarning("${StackUpUpIds.MESSAGE_LANG_ROOT}.rule_complexity.rule_count", listOf(ruleCount)))
            }
            if (longestRuleLength >= RULE_LENGTH_WARNING_THRESHOLD) {
                add(RuleComplexityWarning("${StackUpUpIds.MESSAGE_LANG_ROOT}.rule_complexity.rule_length", listOf(longestRuleLength)))
            }
            if (totalRuleLength >= RULE_TOTAL_LENGTH_WARNING_THRESHOLD) {
                add(RuleComplexityWarning("${StackUpUpIds.MESSAGE_LANG_ROOT}.rule_complexity.total_length", listOf(totalRuleLength)))
            }
        }

        return RuleComplexityReport(
            ruleCount = ruleCount,
            longestRuleLength = longestRuleLength,
            totalRuleLength = totalRuleLength,
            warnings = warnings
        )
    }
}

data class RuleComplexityReport(
    val ruleCount: Int,
    val longestRuleLength: Int,
    val totalRuleLength: Int,
    val warnings: List<RuleComplexityWarning>
)

data class RuleReloadWarning(
    val translationKey: String,
    val args: List<Any>
)

typealias RuleComplexityWarning = RuleReloadWarning

