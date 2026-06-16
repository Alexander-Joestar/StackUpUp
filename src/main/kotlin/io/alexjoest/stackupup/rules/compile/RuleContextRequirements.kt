package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.RuleField
import io.alexjoest.stackupup.rules.field.RuleFieldContextProvider

data class RuleContextRequirements(
    val referencedFields: Set<RuleField>,
    val cacheKeyFields: List<RuleField>,
) {
    private val runtimeRequirementsCache: RuntimeContextRequirements =
        RuntimeContextRequirements.fromFields(referencedFields)

    fun runtimeRequirements(): RuntimeContextRequirements =
        runtimeRequirementsCache

    val needsOreNames: Boolean = runtimeRequirementsCache.requires(RuleFieldContextProvider.ORE_NAMES)
    val needsMaterial: Boolean = runtimeRequirementsCache.requires(RuleFieldContextProvider.MATERIAL)

    companion object {
        fun fromRules(rules: List<CompiledRule>): RuleContextRequirements {
            val referencedFields = rules.flatMapTo(LinkedHashSet(), CompiledRule::referencedFields)
            val cacheKeyFields = referencedFields.filterTo(LinkedHashSet()) { it.contributesToCacheKey() }
            return RuleContextRequirements(referencedFields, cacheKeyFields.toList())
        }
    }
}
