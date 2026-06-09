package io.alexjoest.stackupup.rules.model

import io.alexjoest.stackupup.limit.StackContext

@Deprecated("Rule matching now evaluates StackContext directly.")
data class RuleMatchContext(
    val itemId: String,
    val modId: String,
    val meta: Int,
    val baseSize: Int,
    val type: String,
    val oreNames: Set<String>,
    val tab: String = "",
    val material: String = "",
) {
    fun toStackContext(): StackContext = StackContext(
        itemId = itemId,
        modId = modId,
        metadata = meta,
        type = type,
        baseLimit = baseSize,
        oreNames = oreNames,
        tab = tab,
        material = material,
    )
}
