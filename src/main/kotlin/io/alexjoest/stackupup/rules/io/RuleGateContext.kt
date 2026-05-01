package io.alexjoest.stackupup.rules.io

import net.minecraftforge.fml.common.Loader

data class RuleGateContext(val loadedMods: Set<String> = emptySet()) {
    fun matches(gate: RuleGate): Boolean = when (gate.key) {
        "mod" -> gate.value.lowercase() in loadedMods
        else -> false
    }

    companion object {
        val EMPTY: RuleGateContext = RuleGateContext()

        fun fromLoadedMods(): RuleGateContext = RuleGateContext(
            loadedMods = Loader.instance().indexedModList.keys.map(String::lowercase).toSet(),
        )
    }
}

data class RuleGate(val key: String, val value: String)
