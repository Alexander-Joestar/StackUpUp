package io.alexjoest.stackupup.limit

import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import java.util.concurrent.atomic.AtomicReference

object RuleRuntime {
    private val snapshotRef = AtomicReference(RuleSnapshot(version = 0L, rules = emptyList()))
    private val oreDictIndexRef = AtomicReference(OreDictIndex({ _, _ -> emptySet() }))
    private val limitServiceRef = AtomicReference(StackLimitService(snapshotRef.get()))

    @JvmStatic
    fun limitService(): StackLimitService = limitServiceRef.get()

    @JvmStatic
    fun currentSnapshot(): RuleSnapshot = snapshotRef.get()

    @JvmStatic
    fun replaceSnapshot(snapshot: RuleSnapshot) {
        snapshotRef.set(snapshot)
        refreshLimitService()
    }

    @JvmStatic
    fun oreDictIndex(): OreDictIndex = oreDictIndexRef.get()

    @JvmStatic
    fun replaceOreDictIndex(index: OreDictIndex) {
        oreDictIndexRef.set(index)
        refreshLimitService()
    }

    private fun refreshLimitService() {
        // 规则快照或矿辞索引发生替换时，直接整体刷新服务实例，
        // 这样热路径缓存无需额外加版本判断，结构更简单，也更不容易漏失效。
        limitServiceRef.set(StackLimitService(snapshotRef.get()))
    }
}
