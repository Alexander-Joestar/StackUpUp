package io.alexjoest.stackupup.rules.io

import net.minecraftforge.fml.common.Loader

data class RuleGateContext(val loadedMods: Set<String> = emptySet(), val states: Map<String, Boolean> = emptyMap()) {
    fun modLoaded(modId: String): Boolean = modId.lowercase() in loadedMods

    fun state(name: String): Boolean = states[name.lowercase()] == true

    fun matches(expression: RuleGateExpression): Boolean = expression.evaluate(this)

    companion object {
        val EMPTY: RuleGateContext = RuleGateContext()

        fun fromLoadedMods(): RuleGateContext = RuleGateContext(
            loadedMods = Loader.instance().indexedModList.keys.map(String::lowercase).toSet(),
        )
    }
}
