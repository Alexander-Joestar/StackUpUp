package pl.asie.stackup.rules.model

data class RuleMatchContext(
    val itemId: String,
    val modId: String,
    val meta: Int,
    val baseSize: Int,
    val type: String,
    val oreNames: Set<String>
)
