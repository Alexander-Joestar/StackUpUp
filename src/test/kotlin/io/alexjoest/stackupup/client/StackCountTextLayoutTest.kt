package io.alexjoest.stackupup.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StackCountTextLayoutTest {
    @Test
    fun `shouldStripFormattingCodes`() {
        assertEquals("1024", StackCountTextLayout.stripFormattingCodes("§e10§l24"))
        assertEquals("64", StackCountTextLayout.stripFormattingCodes("64"))
    }

    @Test
    fun `shouldTruncateToMostSignificantDigit`() {
        assertEquals(7, StackCountTextLayout.floorToMostSignificantDigit(7))
        assertEquals(900, StackCountTextLayout.floorToMostSignificantDigit(987))
        assertEquals(10000, StackCountTextLayout.floorToMostSignificantDigit(12345))
    }

    @Test
    fun `thousands_shouldUseCompactAbbreviation`() {
        assertEquals("10.2K", StackCountTextLayout.formatLongCompactCount(10240))
        assertEquals("10K", StackCountTextLayout.formatShortCompactCount(10240))
    }

    @Test
    fun `compactAbbreviation_shouldCoverBoundaries`() {
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
    fun `cappedAbbreviation_shouldCoverAlwaysCompactBoundaries`() {
        assertEquals("999", StackCountTextLayout.formatCappedCount(999))
        assertEquals("1K", StackCountTextLayout.formatCappedCount(1000))
        assertEquals("1.5K", StackCountTextLayout.formatCappedCount(1500))
        assertEquals("99K", StackCountTextLayout.formatCappedCount(99999))
        assertEquals("0.1M", StackCountTextLayout.formatCappedCount(100000))
        assertEquals("9.9M", StackCountTextLayout.formatCappedCount(9999999))
        assertEquals("99M", StackCountTextLayout.formatCappedCount(99999999))
        assertEquals("0.1B", StackCountTextLayout.formatCappedCount(100000000))
        assertEquals("2.1B", StackCountTextLayout.formatCappedCount(Int.MAX_VALUE))
    }

    @Test
    fun `rawInteger_shouldUseThousandsSeparator`() {
        assertEquals("1,024", StackCountTextLayout.formatGroupedCount(1024))
        assertEquals("80,000", StackCountTextLayout.formatGroupedCount(80000))
        assertEquals("1,000,000", StackCountTextLayout.formatGroupedCount(1000000))
    }
}
