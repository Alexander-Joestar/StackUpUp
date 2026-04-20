package io.alexjoest.stackupup.rules.model

import io.alexjoest.stackupup.rules.RuleStepKind

data class RuleStep(
    val kind: RuleStepKind,
    val value: Int
) {
    val debugName: String
        get() = kind.debugName
}

data class RuleAction(
    val steps: List<RuleStep>
) {
    fun apply(base: Int): Int {
        var result = base
        for (step in steps) {
            result = when (step.kind) {
                RuleStepKind.SET -> step.value
                RuleStepKind.ADD -> result + step.value
                RuleStepKind.SUBTRACT -> result - step.value
                RuleStepKind.MULTIPLY -> result * step.value
                RuleStepKind.DIVIDE -> if (step.value == 0) result else result / step.value
            }
        }
        return result.coerceAtLeast(1)
    }
}

