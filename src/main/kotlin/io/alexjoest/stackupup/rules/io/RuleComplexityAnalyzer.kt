package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.rules.LocalizedMessage
import io.alexjoest.stackupup.rules.RuleStepKind
import io.alexjoest.stackupup.rules.compile.RuleSnapshot

object RuleComplexityAnalyzer {
    private const val RULE_COUNT_THRESHOLD = 80
    private const val RULE_LENGTH_THRESHOLD = 140
    private const val TOTAL_LENGTH_THRESHOLD = 4096

    fun analyze(snapshot: RuleSnapshot): List<LocalizedMessage> = buildList {
        val ruleCount = snapshot.rules.size
        val longestRuleLength = snapshot.rules.maxOfOrNull { it.sourceLine.length } ?: 0
        val totalRuleLength = snapshot.rules.sumOf { it.sourceLine.length }

        if (ruleCount >= RULE_COUNT_THRESHOLD) {
            add(LocalizedMessage(StackUpUpIds.RULE_COMPLEXITY_RULE_COUNT_KEY, listOf(ruleCount)))
        }
        if (longestRuleLength >= RULE_LENGTH_THRESHOLD) {
            add(LocalizedMessage(StackUpUpIds.RULE_COMPLEXITY_RULE_LENGTH_KEY, listOf(longestRuleLength)))
        }
        if (totalRuleLength >= TOTAL_LENGTH_THRESHOLD) {
            add(LocalizedMessage(StackUpUpIds.RULE_COMPLEXITY_TOTAL_LENGTH_KEY, listOf(totalRuleLength)))
        }
        addAll(clampWarnings(snapshot))
    }

    private fun clampWarnings(snapshot: RuleSnapshot): List<LocalizedMessage> {
        val max = StackUpUpConfig.maxStackSize
        return snapshot.rules.mapNotNull { rule ->
            val setValue = rule.action.steps
                .firstOrNull { it.kind == RuleStepKind.SET }
                ?.value
                ?: return@mapNotNull null
            if (setValue <= max) {
                null
            } else {
                LocalizedMessage(StackUpUpIds.RULE_LIMIT_CLAMP_KEY, listOf(rule.lineNumber, max))
            }
        }
    }
}
