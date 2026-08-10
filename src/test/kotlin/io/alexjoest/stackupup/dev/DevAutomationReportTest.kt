package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DevAutomationReportTest {
    @Test
    fun renderReport_withoutFailure_shouldEndWithPass() {
        assertEquals(
            "矩阵 IngotSteel: 通过。目标=gregtech:meta_ingot@324 解析=1024\n最终状态: PASS\n",
            renderAutomationReport(
                lines = listOf("矩阵 IngotSteel: 通过。目标=gregtech:meta_ingot@324 解析=1024"),
                failed = false,
            ),
        )
    }

    @Test
    fun renderReport_withFailure_shouldEndWithFail() {
        assertEquals(
            "矩阵 VacuumTube: 失败。目标=gregtech:meta_item_1@516 解析=512\n最终状态: FAIL\n",
            renderAutomationReport(
                lines = listOf("矩阵 VacuumTube: 失败。目标=gregtech:meta_item_1@516 解析=512"),
                failed = true,
            ),
        )
    }
}
