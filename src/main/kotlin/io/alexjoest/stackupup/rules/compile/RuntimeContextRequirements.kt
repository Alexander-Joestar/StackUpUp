package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.RuleContextRequirement

data class RuntimeContextRequirements(
    private val requirements: Set<RuleContextRequirement>,
) {
    fun requires(requirement: RuleContextRequirement): Boolean = requirement in requirements

    companion object {
        val EMPTY: RuntimeContextRequirements = RuntimeContextRequirements(emptySet())

        val DEFAULT_STACK_CONTEXT: RuntimeContextRequirements =
            RuntimeContextRequirements(setOf(RuleContextRequirement.ORE_NAMES))

        fun of(vararg requirements: RuleContextRequirement): RuntimeContextRequirements =
            RuntimeContextRequirements(requirements.toCollection(LinkedHashSet()))
    }
}
