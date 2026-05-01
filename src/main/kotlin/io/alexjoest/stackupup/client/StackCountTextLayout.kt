package io.alexjoest.stackupup.client

import io.alexjoest.stackupup.StackUpUp
import io.alexjoest.stackupup.StackUpUpConfig
import net.minecraft.client.gui.FontRenderer
import java.util.Locale
import kotlin.math.roundToInt

object StackCountTextLayout {
    private const val FORMAT_CODE_MARKER: Char = '\u00A7'
    private const val DECIMAL_RADIX: Int = 10
    private const val THOUSAND: Int = 1_000
    private const val TEN_THOUSAND: Int = 10_000
    private const val HUNDRED_THOUSAND: Int = 100_000
    private const val MILLION: Int = 1_000_000
    private const val TEN_MILLION: Int = 10_000_000
    private const val HUNDRED_MILLION: Int = 100_000_000
    private const val BILLION: Int = 1_000_000_000

    private enum class CompactStyle {
        INTEGER,
        ONE_DECIMAL,
        TWO_DECIMALS,
        LEADING_DOT,
    }

    private data class CompactSegment(val minInclusive: Int, val maxInclusive: Int, val divisor: Int, val suffix: Char, val style: CompactStyle)

    private val shortCompactSegments = arrayOf(
        CompactSegment(THOUSAND, TEN_THOUSAND - 1, 100, 'K', CompactStyle.ONE_DECIMAL),
        CompactSegment(TEN_THOUSAND, HUNDRED_THOUSAND - 1, THOUSAND, 'K', CompactStyle.INTEGER),
        CompactSegment(HUNDRED_THOUSAND, MILLION - 1, HUNDRED_THOUSAND, 'M', CompactStyle.LEADING_DOT),
        CompactSegment(MILLION, HUNDRED_MILLION - 1, MILLION, 'M', CompactStyle.INTEGER),
        CompactSegment(HUNDRED_MILLION, BILLION - 1, HUNDRED_MILLION, 'B', CompactStyle.LEADING_DOT),
        CompactSegment(BILLION, Int.MAX_VALUE, BILLION, 'B', CompactStyle.INTEGER),
    )

    private val longCompactSegments = arrayOf(
        CompactSegment(THOUSAND, TEN_THOUSAND - 1, DECIMAL_RADIX, 'K', CompactStyle.TWO_DECIMALS),
        CompactSegment(TEN_THOUSAND, HUNDRED_THOUSAND - 1, 100, 'K', CompactStyle.ONE_DECIMAL),
        CompactSegment(HUNDRED_THOUSAND, MILLION - 1, THOUSAND, 'K', CompactStyle.INTEGER),
        CompactSegment(MILLION, TEN_MILLION - 1, 10_000, 'M', CompactStyle.TWO_DECIMALS),
        CompactSegment(TEN_MILLION, HUNDRED_MILLION - 1, HUNDRED_THOUSAND, 'M', CompactStyle.ONE_DECIMAL),
        CompactSegment(HUNDRED_MILLION, BILLION - 1, MILLION, 'M', CompactStyle.INTEGER),
        CompactSegment(BILLION, Int.MAX_VALUE, TEN_MILLION, 'B', CompactStyle.TWO_DECIMALS),
    )

    data class AbbreviationResult(val text: String, val scaleFactor: Float, val fits: Boolean, val abbreviated: Boolean)

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
    fun abbreviate(fr: FontRenderer, countIn: String, maxWidth: Int, justCheckAbbreviation: Boolean): AbbreviationResult {
        val (leadingFormatCodes, visibleCount) = splitLeadingFormatting(countIn)
        val numericCount = stripFormattingCodes(visibleCount)
        val result = abbreviateInner(fr, visibleCount, numericCount, maxWidth, justCheckAbbreviation)

        if (leadingFormatCodes.isNotEmpty()) {
            return result.copy(text = leadingFormatCodes + result.text)
        }

        return result
    }

    private fun abbreviateInner(fr: FontRenderer, count: String, numericCount: String, maxWidth: Int, justCheckAbbreviation: Boolean): AbbreviationResult {
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
                true,
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
                true,
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
            false,
        )
    }

    private fun tryFitString(
        fr: FontRenderer,
        maxWidth: Int,
        comparedText: String,
        text: String,
        maxScaleFactor: Int,
        abbreviated: Boolean,
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

        val cfgMinScaleFactor =
            (StackUpUpConfig.lowestScaleDown * maxScaleFactor).roundToInt().coerceIn(1, maxScaleFactor)
        val cfgMaxScaleFactor =
            (StackUpUpConfig.highestScaleDown * maxScaleFactor).roundToInt().coerceIn(1, maxScaleFactor)

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

    internal fun formatShortCompactCount(countI: Int): String = formatCompactCount(countI, shortCompactSegments)

    internal fun formatLongCompactCount(countI: Int): String = formatCompactCount(countI, longCompactSegments)

    internal fun formatGroupedCount(countI: Int): String = String.format(Locale.ROOT, "%,d", countI)

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
            return countI >= THOUSAND
        }

        return countI >= THOUSAND && getStringLenWithoutFmtCodes(rawText) >= 5
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

    private fun formatCompactCount(countI: Int, segments: Array<CompactSegment>): String {
        for (segment in segments) {
            if (countI < segment.minInclusive || countI > segment.maxInclusive) {
                continue
            }

            val scaledValue = countI / segment.divisor
            return when (segment.style) {
                CompactStyle.INTEGER -> "${scaledValue}${segment.suffix}"
                CompactStyle.ONE_DECIMAL -> formatSingleDecimalCompact(scaledValue, segment.suffix)
                CompactStyle.TWO_DECIMALS -> formatTwoDecimalCompact(scaledValue, segment.suffix)
                CompactStyle.LEADING_DOT -> ".${scaledValue}${segment.suffix}"
            }
        }

        return countI.toString()
    }
}
