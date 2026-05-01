package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException

class DevCompatProbeFailureFormattingTest {
    @Test
    fun `shouldUnwrapInvocationTargetException`() {
        val throwable = InvocationTargetException(IllegalStateException("grid exploded"))

        assertEquals(
            "IllegalStateException: grid exploded",
            formatProbeThrowable(throwable),
        )
    }

    @Test
    fun `exception_shouldAppendCauseToSummary`() {
        assertEquals(
            "左键提取请求=-1 预期=128 原因=IllegalStateException: grid exploded",
            appendProbeFailureCause(
                "左键提取请求=-1 预期=128",
                InvocationTargetException(IllegalStateException("grid exploded")),
            ),
        )
    }

    @Test
    fun `noException_shouldKeepOriginalSummary`() {
        assertEquals(
            "左键提取请求=128 预期=128",
            appendProbeFailureCause("左键提取请求=128 预期=128", null),
        )
    }
}
