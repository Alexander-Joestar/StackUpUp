package io.alexjoest.stackupup

/**
 * 运行时守卫，允许在测试或特定环境中临时关闭 remainder 恢复逻辑，
 * 以便隔离验证 slot limit 扩展本身的行为。
 *
 * 用法：
 * ```kotlin
 * RemainderGuard.withoutRemainder {
 *     // 操作 Slot → 不会被 remainder 干涉
 * }
 * ```
 */
object RemainderGuard {
    @Volatile
    @JvmField
    var enabled: Boolean = true

    @JvmStatic
    fun <T> withoutRemainder(block: () -> T): T {
        val previous = enabled
        enabled = false
        try {
            return block()
        } finally {
            enabled = previous
        }
    }
}
