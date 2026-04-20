package io.alexjoest.stackupup.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DynamicCompatEarlyPathBytecodeTest {
    @Test
    fun `动态兼容早期路径不应引用 Kotlin when 异常`() {
        assertFalse(classBytes(CompatibilityLimitPatch::class.java).containsAscii("kotlin/NoWhenBranchMatchedException"))
        assertFalse(classBytes(DynamicCompatTargetClassifier::class.java).containsAscii("kotlin/NoWhenBranchMatchedException"))
    }

    @Test
    fun `动态兼容早期路径不应引用 Kotlin Ref 包装类`() {
        assertFalse(classBytes(DynamicCompatMethodProbe::class.java).containsAscii("kotlin/jvm/internal/Ref${'$'}BooleanRef"))
    }

    @Test
    fun `动态兼容早期路径不应生成 Kotlin when 映射辅助类`() {
        assertFalse(hasSiblingClass(CompatibilityLimitPatch::class.java, "WhenMappings"))
        assertFalse(hasSiblingClass(DynamicCompatTargetClassifier::class.java, "WhenMappings"))
    }

    private fun classBytes(type: Class<*>): ByteArray {
        val resourceName = "${type.simpleName}.class"
        return requireNotNull(type.getResourceAsStream(resourceName)) {
            "无法读取类字节码: ${type.name}"
        }.use { it.readBytes() }
    }

    private fun hasSiblingClass(type: Class<*>, suffix: String): Boolean {
        val resourceName = "${type.simpleName}$$$suffix.class"
        return type.getResource(resourceName) != null
    }

    private fun ByteArray.containsAscii(value: String): Boolean {
        if (isEmpty()) {
            return false
        }

        val target = value.encodeToByteArray()
        val lastIndex = size - target.size
        if (lastIndex < 0) {
            return false
        }

        for (index in 0..lastIndex) {
            if (matchesAt(index, target)) {
                return true
            }
        }

        return false
    }

    private fun ByteArray.matchesAt(startIndex: Int, target: ByteArray): Boolean {
        for (offset in target.indices) {
            if (this[startIndex + offset] != target[offset]) {
                return false
            }
        }
        return true
    }
}
