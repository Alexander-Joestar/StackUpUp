package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.RuleField

data class RuleContextRequirements(
    val referencedFields: Set<RuleField>,
    val cacheKeyFields: Set<RuleField>,
) {
    val needsOreNames: Boolean = RuleField.ORE in referencedFields
    val needsMaterial: Boolean = RuleField.MATERIAL in referencedFields

    companion object {
        fun fromRules(rules: List<CompiledRule>): RuleContextRequirements {
            val referencedFields = rules.flatMapTo(LinkedHashSet(), CompiledRule::referencedFields)
            val cacheKeyFields = buildSet {
                if (RuleField.MATERIAL in referencedFields) add(RuleField.MATERIAL)
            }
            return RuleContextRequirements(referencedFields, cacheKeyFields)
        }
    }
}
