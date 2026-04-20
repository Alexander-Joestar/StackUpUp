package io.alexjoest.stackupup.core

/**
 * 仅过滤“确定不需要进入 StackUpUp coremod 处理链”的基础运行时类。
 *
 * 注意：这里故意不用 Kotlin 标准库扩展函数，
 * 避免在 coremod 早期把 kotlin.text 等运行时再次卷入类加载链。
 */
object CoremodClassFilter {
    private val skippedPrefixes: Array<String> = arrayOf(
        "java/",
        "javax/",
        "jdk/",
        "kotlin/",
        "org/spongepowered/",
        "sun/",
        "zone/rong/mixinbooter/"
    )

    @JvmStatic
    fun shouldSkip(internalName: String): Boolean {
        if (internalName.isEmpty()) {
            return true
        }

        for (prefix in skippedPrefixes) {
            if (startsWith(internalName, prefix)) {
                return true
            }
        }

        return false
    }

    private fun startsWith(text: String, prefix: String): Boolean {
        if (text.length < prefix.length) {
            return false
        }

        for (index in prefix.indices) {
            if (text[index] != prefix[index]) {
                return false
            }
        }

        return true
    }
}


