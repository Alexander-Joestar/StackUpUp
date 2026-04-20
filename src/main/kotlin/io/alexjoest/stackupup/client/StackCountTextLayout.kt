package io.alexjoest.stackupup.client

import net.minecraft.client.gui.FontRenderer
import io.alexjoest.stackupup.StackUpUp
import io.alexjoest.stackupup.StackUpUpConfig
import java.util.Locale
import kotlin.math.roundToInt

object StackCountTextLayout {
    private const val FORMAT_CODE_MARKER: Char = '\u00A7'
    private const val DECIMAL_RADIX: Int = 10

    data class AbbreviationResult(
        val text: String,
        val scaleFactor: Float,
        val fits: Boolean,
        val abbreviated: Boolean
    )

    @JvmStatic
    fun getStringLenWithoutFmtCodes(count: String): Int {
        var visibleLength = 0
        var i = 0
        while (i < count.length) {
            if (count[i] == FORMAT_CODE_MARKER && i + 1 < count.length) {
                i += 2
                continue
            }

            visibleLength++
            i++
        }
        return visibleLength
    }

    @JvmStatic
    fun abbreviate(
        fr: FontRenderer,
        countIn: String,
        maxWidth: Int,
        justCheckAbbreviation: Boolean
    ): AbbreviationResult {
        val (leadingFormatCodes, visibleCount) = splitLeadingFormatting(countIn)
        val numericCount = stripFormattingCodes(visibleCount)
        val result = abbreviateInner(fr, visibleCount, numericCount, maxWidth, justCheckAbbreviation)

        if (leadingFormatCodes.isNotEmpty()) {
            return result.copy(text = leadingFormatCodes + result.text)
        }

        return result
    }

    private fun abbreviateInner(
        fr: FontRenderer,
        count: String,
        numericCount: String,
        maxWidth: Int,
        justCheckAbbreviation: Boolean
    ): AbbreviationResult {
        val countI = numericCount.toIntOrNull() ?: -1
        val maxScaleFactor = requireNotNull(StackUpUp.proxy).getCurrentScaleFactor()

        if (countI < 0) {
            return tryFitString(fr, maxWidth, count, count, maxScaleFactor, false)
        }

        val paddedCountI = floorToMostSignificantDigit(countI)
        if (shouldPreferAbbreviation(countI, count, justCheckAbbreviation)) {
            val longResult = tryFitString(
                fr,
                maxWidth,
                formatLongCompactCount(paddedCountI),
                formatLongCompactCount(countI),
                maxScaleFactor,
                true
            )
            if (longResult.fits || justCheckAbbreviation) {
                return longResult
            }

            val shortResult = tryFitString(
                fr,
                maxWidth,
                formatShortCompactCount(paddedCountI),
                formatShortCompactCount(countI),
                maxScaleFactor,
                true
            )
            if (shortResult.fits) {
                return shortResult
            }
        }

        return tryFitString(
            fr,
            maxWidth,
            formatGroupedCount(paddedCountI),
            formatGroupedCount(countI),
            maxScaleFactor,
            false
        )
    }

    private fun tryFitString(
        fr: FontRenderer,
        maxWidth: Int,
        comparedText: String,
        text: String,
        maxScaleFactor: Int,
        abbreviated: Boolean
    ): AbbreviationResult {
        val strWidth = maxOf(fr.getStringWidth(text), fr.getStringWidth(comparedText))

        if (StackUpUpConfig.scaleTextLinearly) {
            var scaleFactor = maxWidth.toFloat() / strWidth
            var fits = true
            if (scaleFactor > StackUpUpConfig.highestScaleDown) {
                scaleFactor = StackUpUpConfig.highestScaleDown
            } else if (scaleFactor < StackUpUpConfig.lowestScaleDown) {
                scaleFactor = StackUpUpConfig.lowestScaleDown
                fits = false
            }
            return AbbreviationResult(text, scaleFactor, fits, abbreviated)
        }

        val cfgMinScaleFactor = (StackUpUpConfig.lowestScaleDown * maxScaleFactor).roundToInt().coerceIn(1, maxScaleFactor)
        val cfgMaxScaleFactor = (StackUpUpConfig.highestScaleDown * maxScaleFactor).roundToInt().coerceIn(1, maxScaleFactor)

        if (cfgMinScaleFactor != cfgMaxScaleFactor) {
            for (currScaleFactor in cfgMaxScaleFactor downTo cfgMinScaleFactor) {
                val scale = currScaleFactor.toFloat() / maxScaleFactor
                val scaledStrWidth = scale * strWidth
                if (scaledStrWidth <= maxWidth) {
                    return AbbreviationResult(text, scale, true, abbreviated)
                }
            }
        }

        val scale = cfgMinScaleFactor.toFloat() / maxScaleFactor
        val scaledStrWidth = scale * strWidth
        return AbbreviationResult(text, scale, scaledStrWidth <= maxWidth, abbreviated)
    }

    internal fun formatShortCompactCount(countI: Int): String {
        return when {
            countI in 1000..9999           -> formatSingleDecimalCompact(countI / 100, 'K')
            countI in 10000..99999         -> "${countI / 1000}K"
            countI in 100000..999999       -> ".${countI / 100000}M"
            countI in 1000000..99999999    -> "${countI / 1000000}M"
            countI in 100000000..999999999 -> ".${countI / 100000000}B"
            countI >= 1000000000           -> "${countI / 1000000000}B"
            else                           -> countI.toString()
        }
    }

    internal fun formatLongCompactCount(countI: Int): String {
        return when {
            countI in 1000..9999           -> formatTwoDecimalCompact(countI / DECIMAL_RADIX, 'K')
            countI in 10000..99999         -> formatSingleDecimalCompact(countI / 100, 'K')
            countI in 100000..999999       -> "${countI / 1000}K"
            countI in 1000000..9999999     -> formatTwoDecimalCompact(countI / 10000, 'M')
            countI in 10000000..99999999   -> formatSingleDecimalCompact(countI / 100000, 'M')
            countI in 100000000..999999999 -> "${countI / 1000000}M"
            countI >= 1000000000           -> formatTwoDecimalCompact(countI / 10000000, 'B')
            else                           -> countI.toString()
        }
    }

    internal fun formatGroupedCount(countI: Int): String =
        String.format(Locale.ROOT, "%,d", countI)

    internal fun stripFormattingCodes(text: String): String {
        if (FORMAT_CODE_MARKER !in text) {
            return text
        }

        return buildString(text.length) {
            var index = 0
            while (index < text.length) {
                if (text[index] == FORMAT_CODE_MARKER && index + 1 < text.length) {
                    index += 2
                    continue
                }

                append(text[index])
                index++
            }
        }
    }

    internal fun floorToMostSignificantDigit(value: Int): Int {
        if (value < DECIMAL_RADIX) {
            return value
        }

        var magnitude = 1
        var reducedValue = value
        while (reducedValue >= DECIMAL_RADIX) {
            reducedValue /= DECIMAL_RADIX
            magnitude *= DECIMAL_RADIX
        }
        return reducedValue * magnitude
    }

    private fun shouldPreferAbbreviation(countI: Int, rawText: String, justCheckAbbreviation: Boolean): Boolean {
        if (justCheckAbbreviation) {
            return countI >= 1000
        }

        return countI >= 1000 && getStringLenWithoutFmtCodes(rawText) >= 5
    }

    private fun splitLeadingFormatting(text: String): Pair<String, String> {
        var prefixEnd = 0
        while (prefixEnd + 1 < text.length && text[prefixEnd] == FORMAT_CODE_MARKER) {
            prefixEnd += 2
        }

        return if (prefixEnd == 0) {
            "" to text
        } else {
            text.substring(0, prefixEnd) to text.substring(prefixEnd)
        }
    }

    private fun formatSingleDecimalCompact(value: Int, suffix: Char): String {
        val integerPart = value / DECIMAL_RADIX
        val fractionalPart = value % DECIMAL_RADIX
        return buildString(5) {
            append(integerPart)
            append('.')
            append(fractionalPart)
            append(suffix)
        }
    }

    private fun formatTwoDecimalCompact(value: Int, suffix: Char): String {
        val integerPart = value / 100
        val fractionalPart = value % 100
        return buildString(6) {
            append(integerPart)
            append('.')
            if (fractionalPart < DECIMAL_RADIX) {
                append('0')
            }
            append(fractionalPart)
            append(suffix)
        }
    }
}

