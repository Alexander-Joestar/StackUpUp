package io.alexjoest.stackupup.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StackCountTextLayoutTest {
    @Test
    fun `应剥离整串文本中的格式码`() {
        assertEquals("1024", StackCountTextLayout.stripFormattingCodes("§e10§l24"))
        assertEquals("64", StackCountTextLayout.stripFormattingCodes("64"))
    }

    @Test
    fun `应将数量截断到最高有效位`() {
        assertEquals(7, StackCountTextLayout.floorToMostSignificantDigit(7))
        assertEquals(900, StackCountTextLayout.floorToMostSignificantDigit(987))
        assertEquals(10000, StackCountTextLayout.floorToMostSignificantDigit(12345))
    }

    @Test
    fun `千位数量应生成紧凑缩写`() {
        assertEquals("10.2K", StackCountTextLayout.formatLongCompactCount(10240))
        assertEquals("10K", StackCountTextLayout.formatShortCompactCount(10240))
    }

    @Test
    fun `原始整数显示应使用千分位分隔`() {
        assertEquals("1,024", StackCountTextLayout.formatGroupedCount(1024))
        assertEquals("80,000", StackCountTextLayout.formatGroupedCount(80000))
    }
}
