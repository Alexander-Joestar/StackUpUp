package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException

class DevCompatProbeFailureFormattingTest {
    @Test
    fun `应解包 InvocationTargetException 并输出真实异常类型`() {
        val throwable = InvocationTargetException(IllegalStateException("grid exploded"))

        assertEquals(
            "IllegalStateException: grid exploded",
            formatProbeThrowable(throwable)
        )
    }

    @Test
    fun `存在异常时应将原因追加到探针摘要`() {
        assertEquals(
            "左键提取请求=-1 预期=128 原因=IllegalStateException: grid exploded",
            appendProbeFailureCause(
                "左键提取请求=-1 预期=128",
                InvocationTargetException(IllegalStateException("grid exploded"))
            )
        )
    }

    @Test
    fun `无异常时应保留原始探针摘要`() {
        assertEquals(
            "左键提取请求=128 预期=128",
            appendProbeFailureCause("左键提取请求=128 预期=128", null)
        )
    }
}
