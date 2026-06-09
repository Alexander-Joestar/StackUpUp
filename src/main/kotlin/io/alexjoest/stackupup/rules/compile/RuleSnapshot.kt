package io.alexjoest.stackupup.rules.compile

data class RuleSnapshot(
    val version: Long,
    val rules: List<CompiledRule>,
    val requirements: RuleContextRequirements = RuleContextRequirements.fromRules(rules),
) {
    val hasRules: Boolean = rules.isNotEmpty()
    val needsOreNames: Boolean = requirements.needsOreNames
    val needsMaterial: Boolean = requirements.needsMaterial
}
