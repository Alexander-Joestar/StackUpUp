package io.alexjoest.stackupup

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.resources.I18n
import net.minecraftforge.event.entity.player.ItemTooltipEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import io.alexjoest.stackupup.client.StackCountTextLayout
import io.alexjoest.stackupup.client.StackRenderHooks
import io.alexjoest.stackupup.rules.io.RuleFeedback

class ProxyClient : ProxyCommon() {
    private var pendingRuleStatusReminder: Boolean = true

    @SubscribeEvent
    fun onTooltip(event: ItemTooltipEvent) {
        var renderer = event.itemStack.item.getFontRenderer(event.itemStack)
        if (renderer == null) {
            renderer = Minecraft.getMinecraft().fontRenderer ?: return
        }

        val stackCount = event.itemStack.count
        val count = stackCount.toString()
        val countA = StackCountTextLayout.abbreviate(renderer, count, StackRenderHooks.SLOT_MAX_WIDTH, true)
        if (countA.abbreviated) {
            event.toolTip.add("x ${StackCountTextLayout.formatGroupedCount(stackCount)}")
        }

        when (StackUpUpConfig.tooltipStackDisplayMode) {
            TooltipStackDisplayMode.OFF -> return
            TooltipStackDisplayMode.ADVANCED -> if (!event.flags.isAdvanced) return
            TooltipStackDisplayMode.ALWAYS -> Unit
        }

        event.toolTip.add(
            I18n.format(
                StackUpUpIds.TOOLTIP_CURRENT_MAX_KEY,
                StackCountTextLayout.formatGroupedCount(event.itemStack.count),
                StackCountTextLayout.formatGroupedCount(event.itemStack.maxStackSize)
            )
        )
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END || !pendingRuleStatusReminder) {
            return
        }

        val minecraft = Minecraft.getMinecraft()
        val player = minecraft.player ?: return
        minecraft.world ?: return

        val report = RuleRuntimeCoordinator.lastReport()
        RuleFeedback.emitReloadErrors(report, player::sendMessage)
        RuleFeedback.emitWarnings(report, player::sendMessage)
        pendingRuleStatusReminder = false
    }

    override fun getCurrentScaleFactor(): Int = ScaledResolution(Minecraft.getMinecraft()).scaleFactor

    override fun registerDevAutomation() {
        if (DevAutomationBridge.registerClientAutomation()) {
            StackUpUp.logger?.info("Enabled dev automation: client will automatically enter the test world and receive the target item.")
        }
    }

    override fun markRuleStatusDirty() {
        pendingRuleStatusReminder = true
    }
}
