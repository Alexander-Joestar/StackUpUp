package io.alexjoest.stackupup.client

import io.alexjoest.stackupup.TooltipStackDisplayMode
import io.alexjoest.stackupup.resolveTooltipCountLines
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TooltipCountLinesTest {
    @Test
    fun off_hidesStackLine_andKeepsExactCountOnlyWhenAbbreviated() {
        val abbreviated = resolveTooltipCountLines(TooltipStackDisplayMode.OFF, isAdvanced = true, abbreviated = true)
        assertTrue(abbreviated.emitExactCount)
        assertFalse(abbreviated.emitStackCurrentMax)

        val plain = resolveTooltipCountLines(TooltipStackDisplayMode.OFF, isAdvanced = true, abbreviated = false)
        assertFalse(plain.emitExactCount)
        assertFalse(plain.emitStackCurrentMax)
    }

    @Test
    fun advancedNonAdvanced_behavesLikeOff() {
        val abbreviated = resolveTooltipCountLines(TooltipStackDisplayMode.ADVANCED, isAdvanced = false, abbreviated = true)
        assertTrue(abbreviated.emitExactCount)
        assertFalse(abbreviated.emitStackCurrentMax)

        val plain = resolveTooltipCountLines(TooltipStackDisplayMode.ADVANCED, isAdvanced = false, abbreviated = false)
        assertFalse(plain.emitExactCount)
        assertFalse(plain.emitStackCurrentMax)
    }

    @Test
    fun advancedAdvanced_showsStackLineOnly() {
        val abbreviated = resolveTooltipCountLines(TooltipStackDisplayMode.ADVANCED, isAdvanced = true, abbreviated = true)
        assertFalse(abbreviated.emitExactCount)
        assertTrue(abbreviated.emitStackCurrentMax)

        val plain = resolveTooltipCountLines(TooltipStackDisplayMode.ADVANCED, isAdvanced = true, abbreviated = false)
        assertFalse(plain.emitExactCount)
        assertTrue(plain.emitStackCurrentMax)
    }

    @Test
    fun always_showsStackLineOnly() {
        val abbreviated = resolveTooltipCountLines(TooltipStackDisplayMode.ALWAYS, isAdvanced = false, abbreviated = true)
        assertFalse(abbreviated.emitExactCount)
        assertTrue(abbreviated.emitStackCurrentMax)

        val plain = resolveTooltipCountLines(TooltipStackDisplayMode.ALWAYS, isAdvanced = false, abbreviated = false)
        assertFalse(plain.emitExactCount)
        assertTrue(plain.emitStackCurrentMax)
    }

    @Test
    fun neverEmitsBothCountLines() {
        for (mode in TooltipStackDisplayMode.values()) {
            for (isAdvanced in booleanArrayOf(true, false)) {
                for (abbreviated in booleanArrayOf(true, false)) {
                    val lines = resolveTooltipCountLines(mode, isAdvanced, abbreviated)
                    assertFalse(
                        lines.emitExactCount && lines.emitStackCurrentMax,
                        "mode=$mode isAdvanced=$isAdvanced abbreviated=$abbreviated must not emit both count lines",
                    )
                }
            }
        }
    }
}
