package pl.asie.stackup.rules.compile

data class RuleSnapshot(
    val version: Long,
    val rules: List<CompiledRule>
)
