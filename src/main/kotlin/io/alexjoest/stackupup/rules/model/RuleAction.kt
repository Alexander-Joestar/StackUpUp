package io.alexjoest.stackupup.rules.model

import io.alexjoest.stackupup.rules.RuleStepKind

data class RuleStep(val kind: RuleStepKind, val value: Int) {
    val debugName: String get() = kind.id
}

data class RuleAction(val steps: List<RuleStep>) {
    fun apply(base: Int): Int {
        fun divideOrKeep(current: Int, stepValue: Int): Int = if (stepValue == 0) current else current / stepValue

        var result = base
        for ((kind, value) in steps) {
            result = when (kind) {
                RuleStepKind.SET -> value
                RuleStepKind.ADD -> result + value
                RuleStepKind.SUBTRACT -> result - value
                RuleStepKind.MULTIPLY -> result * value
                RuleStepKind.DIVIDE -> divideOrKeep(result, value)
            }
        }
        return result.coerceAtLeast(1)
    }
}
