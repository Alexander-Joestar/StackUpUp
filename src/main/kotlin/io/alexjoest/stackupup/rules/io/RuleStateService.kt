package io.alexjoest.stackupup.rules.io

import java.io.File

internal class RuleStateService(
    private val resolveFile: () -> File?,
) {
    /**
     * 读取指定状态键。
     *
     * - 存储文件不可用时返回 null。
     * - 存储可用但状态键缺失时返回 false。
     */
    fun getState(name: String): Boolean? {
        val store = stateStore() ?: return null
        return store.readStates()[name] ?: false
    }

    /**
     * 写入指定状态键。
     *
     * 存储文件不可用时返回 null；可用时返回底层写入是否改变了文件内容。
     */
    fun setState(name: String, value: Boolean): Boolean? {
        val store = stateStore() ?: return null
        val states = store.readStates().toMutableMap()
        states[name] = value
        return store.writeStates(states)
    }

    private fun stateStore(): RuleStateStore? = resolveFile()?.let(::RuleStateStore)
}
