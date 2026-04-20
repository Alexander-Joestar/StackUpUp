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
    fun `紧凑缩写应覆盖关键边界值`() {
        assertEquals("999", StackCountTextLayout.formatLongCompactCount(999))
        assertEquals("999", StackCountTextLayout.formatShortCompactCount(999))

        assertEquals("1.00K", StackCountTextLayout.formatLongCompactCount(1000))
        assertEquals("1.0K", StackCountTextLayout.formatShortCompactCount(1000))

        assertEquals("9.99K", StackCountTextLayout.formatLongCompactCount(9999))
        assertEquals("9.9K", StackCountTextLayout.formatShortCompactCount(9999))

        assertEquals("10.0K", StackCountTextLayout.formatLongCompactCount(10000))
        assertEquals("10K", StackCountTextLayout.formatShortCompactCount(10000))

        assertEquals("999K", StackCountTextLayout.formatLongCompactCount(999999))
        assertEquals(".9M", StackCountTextLayout.formatShortCompactCount(999999))

        assertEquals("1.00M", StackCountTextLayout.formatLongCompactCount(1000000))
        assertEquals("1M", StackCountTextLayout.formatShortCompactCount(1000000))
    }

    @Test
    fun `原始整数显示应使用千分位分隔`() {
        assertEquals("1,024", StackCountTextLayout.formatGroupedCount(1024))
        assertEquals("80,000", StackCountTextLayout.formatGroupedCount(80000))
        assertEquals("1,000,000", StackCountTextLayout.formatGroupedCount(1000000))
    }
}
