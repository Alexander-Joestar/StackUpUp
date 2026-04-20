package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DevBuiltInMatrixOutcomeTest {
    @Test
    fun `gregtech 未加载时全部未解析可作为专项跳过`() {
        assertNull(
            unresolvedBuiltInMatrixFailure(
                unresolvedCount = 4,
                totalCount = 4,
                gregTechLoaded = false
            )
        )
    }

    @Test
    fun `gregtech 已加载时全部未解析必须视为失败`() {
        assertEquals(
            "built_in_matrix: all targets unresolved while gregtech is loaded",
            unresolvedBuiltInMatrixFailure(
                unresolvedCount = 4,
                totalCount = 4,
                gregTechLoaded = true
            )
        )
    }

    @Test
    fun `部分未解析时应保留失败计数`() {
        assertEquals(
            "built_in_matrix: unresolved=2",
            unresolvedBuiltInMatrixFailure(
                unresolvedCount = 2,
                totalCount = 4,
                gregTechLoaded = false
            )
        )
    }
}
