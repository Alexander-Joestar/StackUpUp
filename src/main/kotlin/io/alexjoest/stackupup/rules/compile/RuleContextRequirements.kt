package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.RuleContextRequirement
import io.alexjoest.stackupup.rules.RuleField

data class RuleContextRequirements(
    val referencedFields: Set<RuleField>,
    val cacheKeyFields: List<RuleField>,
) {
    private val requirements: Set<RuleContextRequirement> = referencedFields
        .flatMapTo(LinkedHashSet(), RuleField::requirements)

    fun requires(requirement: RuleContextRequirement): Boolean = requirement in requirements

    val needsOreNames: Boolean = requires(RuleContextRequirement.ORE_NAMES)
    val needsMaterial: Boolean = requires(RuleContextRequirement.MATERIAL)

    companion object {
        fun fromRules(rules: List<CompiledRule>): RuleContextRequirements {
            val referencedFields = rules.flatMapTo(LinkedHashSet(), CompiledRule::referencedFields)
            val cacheKeyFields = referencedFields.filterTo(LinkedHashSet()) { it.contributesToCacheKey() }
            return RuleContextRequirements(referencedFields, cacheKeyFields.toList())
        }
    }
}
