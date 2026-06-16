package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.RuleField
import io.alexjoest.stackupup.rules.field.RuleFieldContextProvider

data class RuleContextRequirements(
    val referencedFields: Set<RuleField>,
    val cacheKeyFields: List<RuleField>,
) {
    private val contextProviders: Set<RuleFieldContextProvider> =
        referencedFields.flatMapTo(LinkedHashSet(), RuleField::contextProviders)
    private val runtimeRequirementsCache: RuntimeContextRequirements =
        RuntimeContextRequirements.fromProviders(contextProviders)

    fun runtimeRequirements(): RuntimeContextRequirements =
        runtimeRequirementsCache

    val needsOreNames: Boolean = RuleFieldContextProvider.ORE_NAMES in contextProviders
    val needsMaterial: Boolean = RuleFieldContextProvider.MATERIAL in contextProviders

    companion object {
        fun fromRules(rules: List<CompiledRule>): RuleContextRequirements {
            val referencedFields = rules.flatMapTo(LinkedHashSet(), CompiledRule::referencedFields)
            val cacheKeyFields = referencedFields.filterTo(LinkedHashSet()) { it.contributesToCacheKey() }
            return RuleContextRequirements(referencedFields, cacheKeyFields.toList())
        }
    }
}
