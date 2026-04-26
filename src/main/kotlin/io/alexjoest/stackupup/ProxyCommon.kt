package io.alexjoest.stackupup

open class ProxyCommon {
    open fun getCurrentScaleFactor(): Int = 2

    open fun registerDevAutomation() = Unit

    open fun markRuleStatusDirty() = Unit

    open fun markConflictDisabled(modNames: List<String>) = Unit
}
