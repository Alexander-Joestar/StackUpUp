package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.RuleContextRequirement

data class RuleSnapshot(
    val version: Long,
    val rules: List<CompiledRule>,
    val requirements: RuleContextRequirements = RuleContextRequirements.fromRules(rules),
) {
    val hasRules: Boolean = rules.isNotEmpty()
    val runtimeRequirements: RuntimeContextRequirements = requirements.runtimeRequirements()
    fun requires(requirement: RuleContextRequirement): Boolean = runtimeRequirements.requires(requirement)
    val needsOreNames: Boolean = requirements.needsOreNames
    val needsMaterial: Boolean = requirements.needsMaterial
}
