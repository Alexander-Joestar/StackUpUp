package pl.asie.stackup.limit

import pl.asie.stackup.rules.compile.RuleSnapshot
import java.util.concurrent.atomic.AtomicReference

object StackUpServices {
    private val snapshotRef = AtomicReference(RuleSnapshot(version = 0L, rules = emptyList()))
    private val oreDictIndexRef = AtomicReference(OreDictIndex { _, _ -> emptySet() })

    @JvmStatic
    fun limitService(): StackLimitService = StackLimitService(snapshotRef.get())

    @JvmStatic
    fun currentSnapshot(): RuleSnapshot = snapshotRef.get()

    @JvmStatic
    fun replaceSnapshot(snapshot: RuleSnapshot) {
        snapshotRef.set(snapshot)
    }

    @JvmStatic
    fun oreDictIndex(): OreDictIndex = oreDictIndexRef.get()

    @JvmStatic
    fun replaceOreDictIndex(index: OreDictIndex) {
        oreDictIndexRef.set(index)
    }
}
