package io.alexjoest.stackupup.client

import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemStack
import net.minecraft.util.math.MathHelper
import io.alexjoest.stackupup.StackUpUpConfig

object StackRenderHooks {
    const val SLOT_MAX_WIDTH: Int = 16

    @JvmStatic
    fun getModelCount(stack: ItemStack): Int {
        return if (stack.count > 2) MathHelper.log2DeBruijn(stack.count) else stack.count
    }

    @JvmStatic
    fun getItemRenderDistanceNeg(item: EntityItem): Float = -getItemRenderDistance(item)

    @JvmStatic
    fun getItemRenderDistance(item: EntityItem): Float {
        val mc = getModelCount(item.item)
        return if (mc <= 2) 0.09375F else 0.125F / MathHelper.sqrt(mc - 1f)
    }

    @JvmStatic
    fun drawItemCountWithShadow(fr: FontRenderer, text: String, xIn: Float, yIn: Float, color: Int): Int {
        if (text.isEmpty()) {
            return 0
        }

        if (StackUpUpConfig.highestScaleDown >= 1.0f && StackCountTextLayout.getStringLenWithoutFmtCodes(text) <= 2) {
            return fr.drawStringWithShadow(text, xIn, yIn, color)
        }

        var x = xIn
        var y = yIn
        val xOffset = 19 - 2 - fr.getStringWidth(text)
        val yOffset = 6 + 3

        x -= xOffset.toFloat()
        y -= yOffset.toFloat()
        val result = StackCountTextLayout.abbreviate(fr, text, SLOT_MAX_WIDTH, false)
        val scaleDiff = result.scaleFactor

        x += 16 - (fr.getStringWidth(result.text) * scaleDiff)
        y += 16 - (8 * scaleDiff)

        GlStateManager.pushMatrix()
        GlStateManager.translate(x, y, 0.0f)
        GlStateManager.scale(scaleDiff, scaleDiff, 1f)
        GlStateManager.translate(-x, -y, 0.0f)
        val i = fr.drawStringWithShadow(result.text, x, y, color)
        GlStateManager.popMatrix()
        return i
    }
}


