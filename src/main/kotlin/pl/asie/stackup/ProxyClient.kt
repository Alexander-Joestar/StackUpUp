package pl.asie.stackup

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraftforge.event.entity.player.ItemTooltipEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import pl.asie.stackup.client.StackUpClientHelpers
import pl.asie.stackup.client.StackUpTextGenerator

class ProxyClient : ProxyCommon() {
    @SubscribeEvent
    fun onTooltip(event: ItemTooltipEvent) {
        var renderer = event.itemStack.item.getFontRenderer(event.itemStack)
        if (renderer == null) {
            renderer = Minecraft.getMinecraft().fontRenderer ?: return
        }
        val count = event.itemStack.count.toString()
        val countA = StackUpTextGenerator.abbreviate(renderer!!, count, StackUpClientHelpers.SLOT_MAX_WIDTH, true)
        if (countA.isAbbreviated()) {
            event.toolTip.add("x $count")
        }
    }

    override fun getCurrentScaleFactor(): Int = ScaledResolution(Minecraft.getMinecraft()).scaleFactor
}
