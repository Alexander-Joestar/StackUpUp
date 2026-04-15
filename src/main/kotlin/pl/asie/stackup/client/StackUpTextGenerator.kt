package pl.asie.stackup.client

import net.minecraft.client.gui.FontRenderer
import net.minecraft.util.math.MathHelper
import pl.asie.stackup.StackUp
import pl.asie.stackup.StackUpConfig

object StackUpTextGenerator {
    class AbbreviationResult(
        private var text: String,
        private val scaleFactor: Float,
        private val fits: Boolean,
        private val abbreviated: Boolean
    ) {
        fun getText(): String = text

        fun setText(text: String) {
            this.text = text
        }

        fun getScaleFactor(): Float = scaleFactor

        fun isFits(): Boolean = fits

        fun isAbbreviated(): Boolean = abbreviated
    }

    @JvmStatic
    fun getStringLenWithoutFmtCodes(count: String): Int {
        var i = 0
        while (i < count.length && count.codePointAt(i) == 0xA7) {
            i += 2
        }
        return count.length - i
    }

    @JvmStatic
    fun abbreviate(fr: FontRenderer, countIn: String, maxWidth: Int, justCheckAbbreviation: Boolean): AbbreviationResult {
        var count = countIn
        val fmtCodes = StringBuilder()
        var fmtCodeCount = 0
        while (count.isNotEmpty() && count.codePointAt(0) == 0xA7) {
            fmtCodes.append(count, 0, 2)
            fmtCodeCount++
            count = count.substring(2)
        }

        val result = abbreviateInner(fr, count, maxWidth, justCheckAbbreviation)
        if (fmtCodeCount > 0) {
            result.setText(fmtCodes.append(result.getText()).toString())
        }
        return result
    }

    private fun abbreviateInner(fr: FontRenderer, count: String, maxWidth: Int, justCheckAbbreviation: Boolean): AbbreviationResult {
        val countI = count.toIntOrNull() ?: -1
        var paddedCountI = 0
        val maxScaleFactor = requireNotNull(StackUp.proxy).getCurrentScaleFactor()

        if (countI < 0) {
            return tryFitString(fr, maxWidth, count, count, maxScaleFactor, false)
        }

        run {
            var tmpCountI = countI
            paddedCountI = 1
            while (tmpCountI >= 10) {
                tmpCountI /= 10
                paddedCountI *= 10
            }
            paddedCountI *= tmpCountI
        }

        var result = tryFitString(fr, maxWidth, paddedCountI.toString(), count, maxScaleFactor, false)
        if (!result.isFits()) {
            result = tryFitString(
                fr,
                maxWidth,
                abbreviateInnerLong(paddedCountI),
                abbreviateInnerLong(countI),
                maxScaleFactor,
                true
            )
            if (!result.isFits()) {
                result = tryFitString(
                    fr,
                    maxWidth,
                    abbreviateInnerShort(paddedCountI),
                    abbreviateInnerShort(countI),
                    maxScaleFactor,
                    true
                )
            }
        }

        return result
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

        if (StackUpConfig.scaleTextLinearly) {
            var scaleFactor = maxWidth.toFloat() / strWidth
            var fits = true
            if (scaleFactor > StackUpConfig.highestScaleDown) {
                scaleFactor = StackUpConfig.highestScaleDown
            } else if (scaleFactor < StackUpConfig.lowestScaleDown) {
                scaleFactor = StackUpConfig.lowestScaleDown
                fits = false
            }
            return AbbreviationResult(text, scaleFactor, fits, abbreviated)
        }

        val cfgMinScaleFactor =
            MathHelper.clamp(Math.round(StackUpConfig.lowestScaleDown * maxScaleFactor), 1, maxScaleFactor)
        val cfgMaxScaleFactor =
            MathHelper.clamp(Math.round(StackUpConfig.highestScaleDown * maxScaleFactor), 1, maxScaleFactor)

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

    private fun abbreviateInnerShort(countI: Int): String {
        return when {
            countI in 1000..99999 -> "${countI / 1000}K"
            countI in 100000..999999 -> ".${countI / 100000}M"
            countI in 1000000..99999999 -> "${countI / 1000000}M"
            countI in 100000000..999999999 -> ".${countI / 100000000}B"
            countI >= 1000000000 -> "${countI / 1000000000}B"
            else -> countI.toString()
        }
    }

    private fun abbreviateInnerLong(countI: Int): String {
        return when {
            countI in 100000..999999 -> "${countI / 1000}K"
            countI in 1000000..9999999 -> {
                val a = countI / 10000
                "${a / 100}.${"%02d".format(a % 100)}M"
            }

            countI in 10000000..99999999 -> {
                val a = countI / 100000
                "${a / 10}.${a % 10}M"
            }

            countI in 100000000..999999999 -> {
                val a = countI / 1000000
                "${a}M"
            }

            countI >= 1000000000 -> {
                val a = countI / 10000000
                "${a / 100}.${"%02d".format(a % 100)}B"
            }

            else -> countI.toString()
        }
    }
}
