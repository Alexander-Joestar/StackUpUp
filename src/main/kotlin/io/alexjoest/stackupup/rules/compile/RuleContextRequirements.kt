package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.RuleContextRequirement
import io.alexjoest.stackupup.rules.RuleField

data class RuleContextRequirements(
    val referencedFields: Set<RuleField>,
    val cacheKeyFields: List<RuleField>,
) {
    private val runtimeRequirementsCache: RuntimeContextRequirements =
        RuntimeContextRequirements.fromFields(referencedFields)

    fun runtimeRequirements(): RuntimeContextRequirements =
        runtimeRequirementsCache

    val needsOreNames: Boolean = runtimeRequirementsCache.requires(RuleContextRequirement.ORE_NAMES)
    val needsMaterial: Boolean = runtimeRequirementsCache.requires(RuleContextRequirement.MATERIAL)

    companion object {
        fun fromRules(rules: List<CompiledRule>): RuleContextRequirements {
            val referencedFields = rules.flatMapTo(LinkedHashSet(), CompiledRule::referencedFields)
            val cacheKeyFields = referencedFields.filterTo(LinkedHashSet()) { it.contributesToCacheKey() }
            return RuleContextRequirements(referencedFields, cacheKeyFields.toList())
        }
    }
}
