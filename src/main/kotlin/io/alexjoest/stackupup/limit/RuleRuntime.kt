package io.alexjoest.stackupup.limit

import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import java.util.concurrent.atomic.AtomicReference

/** 用途：持有当前规则快照与矿石词典索引的全局运行态，原子替换。 */
object RuleRuntime {
    private val emptySnapshot = RuleSnapshot(version = 0L, rules = emptyList())
    private val emptyOreDictIndex = OreDictIndex({ _, _ -> emptySet() })
    private val runtimeStateRef = AtomicReference(RuntimeState(emptySnapshot, emptyOreDictIndex))

    @JvmStatic
    fun limitService(): StackLimitService = runtimeStateRef.get().limitService

    @JvmStatic
    fun currentSnapshot(): RuleSnapshot = runtimeStateRef.get().snapshot

    @JvmStatic
    fun replaceSnapshot(snapshot: RuleSnapshot) {
        replaceRuntime(snapshot, oreDictIndex())
    }

    @JvmStatic
    fun oreDictIndex(): OreDictIndex = runtimeStateRef.get().oreDictIndex

    @JvmStatic
    fun replaceOreDictIndex(index: OreDictIndex) {
        replaceRuntime(currentSnapshot(), index)
    }

    @JvmStatic
    fun replaceRuntime(snapshot: RuleSnapshot, oreDictIndex: OreDictIndex) {
        runtimeStateRef.set(RuntimeState(snapshot, oreDictIndex))
    }

    private class RuntimeState(val snapshot: RuleSnapshot, val oreDictIndex: OreDictIndex) {
        // 规则快照或矿辞索引发生替换时，直接整体刷新服务实例，
        // 这样热路径缓存无需额外加版本判断，结构更简单，也更不容易漏失效。
        val limitService = StackLimitService(snapshot)
    }
}
