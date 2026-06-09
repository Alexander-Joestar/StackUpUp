package io.alexjoest.stackupup.rules.compile

data class RuleSnapshot(val version: Long, val rules: List<CompiledRule>) {
    val hasRules: Boolean = rules.isNotEmpty()
    val needsOreNames: Boolean = rules.any { "ore" in it.referencedFields }
    val needsMaterial: Boolean = rules.any { "material" in it.referencedFields }
}
