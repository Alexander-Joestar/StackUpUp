package io.alexjoest.stackupup.rules.io

import java.io.File

internal class RuleStateService(
    private val resolveFile: () -> File?,
) {
    fun getState(name: String): Boolean? {
        val store = stateStore() ?: return null
        return store.readStates()[name] ?: false
    }

    fun setState(name: String, value: Boolean): Boolean? {
        val store = stateStore() ?: return null
        val states = store.readStates().toMutableMap()
        states[name] = value
        return store.writeStates(states)
    }

    private fun stateStore(): RuleStateStore? = resolveFile()?.let(::RuleStateStore)
}
