package io.alexjoest.stackupup

import io.alexjoest.stackupup.client.StackCountTextLayout
import io.alexjoest.stackupup.client.StackRenderHooks
import io.alexjoest.stackupup.rules.io.RuleFeedback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.gui.toasts.SystemToast
import net.minecraft.client.resources.I18n
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.event.entity.player.ItemTooltipEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

class ProxyClient : ProxyCommon() {
    private var pendingRuleStatusReminder: Boolean = true
    private var pendingConflictToast: List<String> = emptyList()
    private var lastSyncedLanguageCode: String? = null

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

        when (StackUpUpConfig.general.tooltipStackDisplayMode) {
            TooltipStackDisplayMode.OFF -> return
            TooltipStackDisplayMode.ADVANCED -> if (!event.flags.isAdvanced) return
            TooltipStackDisplayMode.ALWAYS -> Unit
        }

        event.toolTip.add(
            I18n.format(
                StackUpUpIds.TOOLTIP_CURRENT_MAX_KEY,
                StackCountTextLayout.formatGroupedCount(event.itemStack.count),
                StackCountTextLayout.formatGroupedCount(event.itemStack.maxStackSize),
            ),
        )
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) {
            syncRuleLanguage()
            return
        }

        syncRuleLanguage()
        val minecraft = Minecraft.getMinecraft()
        if (pendingConflictToast.isNotEmpty()) {
            val title = TextComponentString("StackUpUp disabled itself")
            val detail = TextComponentString("Conflicting stacking mod: ${pendingConflictToast.joinToString(", ")}")
            SystemToast.addOrUpdate(
                minecraft.toastGui,
                SystemToast.Type.TUTORIAL_HINT,
                title,
                detail,
            )
            pendingConflictToast = emptyList()
        }

        val player = minecraft.player
        if (player == null || minecraft.world == null) {
            return
        }

        if (pendingRuleStatusReminder) {
            val report = RuleRuntimeCoordinator.lastReport()
            RuleFeedback.emitReloadErrors(report, player::sendMessage)
            RuleFeedback.emitWarnings(report, player::sendMessage)
            pendingRuleStatusReminder = false
        }
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

    override fun markConflictDisabled(modNames: List<String>) {
        pendingConflictToast = modNames
    }

    private fun syncRuleLanguage() {
        val languageCode = Minecraft.getMinecraft().languageManager.currentLanguage.languageCode
        if (languageCode == lastSyncedLanguageCode) {
            return
        }
        io.alexjoest.stackupup.rules.RuleMessages.syncLanguage(languageCode)
        lastSyncedLanguageCode = languageCode
    }
}
