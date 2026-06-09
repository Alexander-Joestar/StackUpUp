package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.RuleContextRequirement
import io.alexjoest.stackupup.rules.RuleField

data class RuleContextRequirements(
    val referencedFields: Set<RuleField>,
    val cacheKeyFields: Set<RuleField>,
) {
    private val requirements: Set<RuleContextRequirement> = referencedFields
        .flatMapTo(LinkedHashSet(), RuleField::requirements)

    val needsOreNames: Boolean = RuleContextRequirement.ORE_NAMES in requirements
    val needsMaterial: Boolean = RuleContextRequirement.MATERIAL in requirements

    companion object {
        fun fromRules(rules: List<CompiledRule>): RuleContextRequirements {
            val referencedFields = rules.flatMapTo(LinkedHashSet(), CompiledRule::referencedFields)
            val cacheKeyFields = referencedFields.filterTo(LinkedHashSet()) { it.contributesToCacheKey }
            return RuleContextRequirements(referencedFields, cacheKeyFields)
        }
    }
}
