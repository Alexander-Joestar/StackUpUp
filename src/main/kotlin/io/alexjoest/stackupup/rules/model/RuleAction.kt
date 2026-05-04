package io.alexjoest.stackupup.rules.model

import io.alexjoest.stackupup.rules.RuleStepKind

data class RuleStep(val kind: RuleStepKind, val value: Int) {
    val debugName: String get() = kind.id
}

data class RuleAction(val steps: List<RuleStep>) {
    fun apply(base: Int): Int {
        fun divideOrKeep(current: Int, stepValue: Int): Int = if (stepValue == 0) current else current / stepValue

        var result = base
        for (step in steps) {
            result = when (step.kind) {
                RuleStepKind.SET -> step.value
                RuleStepKind.ADD -> result + step.value
                RuleStepKind.SUBTRACT -> result - step.value
                RuleStepKind.MULTIPLY -> result * step.value
                RuleStepKind.DIVIDE -> divideOrKeep(result, step.value)
            }
        }
        return result.coerceAtLeast(1)
    }
}
