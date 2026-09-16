package io.alexjoest.stackupup

import io.alexjoest.stackupup.client.StackCountTextLayout
import io.alexjoest.stackupup.client.StackRenderHooks
import io.alexjoest.stackupup.rules.RuleMessageKey
import io.alexjoest.stackupup.rules.io.RuleFeedback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.gui.toasts.SystemToast
import net.minecraft.client.resources.I18n
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.event.entity.player.ItemTooltipEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

/**
 * 决定 tooltip 上数量相关行的输出去重结果：
 * `emitExactCount` 对应 `x <精确数量>` 行，`emitStackCurrentMax` 对应 `Stack: 当前/上限` 行。
 */
internal data class TooltipCountLines(val emitExactCount: Boolean, val emitStackCurrentMax: Boolean)

/**
 * 纯函数：同一数量只保留一条数量行。
 * `Stack: 当前/上限` 行会显示时跳过 `x` 行；否则 `x` 行作为唯一精确值来源。
 */
internal fun resolveTooltipCountLines(mode: TooltipStackDisplayMode, isAdvanced: Boolean, abbreviated: Boolean): TooltipCountLines {
    val emitStackCurrentMax = when (mode) {
        TooltipStackDisplayMode.OFF -> false
        TooltipStackDisplayMode.ADVANCED -> isAdvanced
        TooltipStackDisplayMode.ALWAYS -> true
    }
    return TooltipCountLines(
        emitExactCount = abbreviated && !emitStackCurrentMax,
        emitStackCurrentMax = emitStackCurrentMax,
    )
}

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
        val countA = StackCountTextLayout.abbreviate(renderer, stackCount.toString(), StackRenderHooks.SLOT_MAX_WIDTH, true)
        val lines = resolveTooltipCountLines(
            StackUpUpConfig.client.tooltipStackDisplayMode,
            event.flags.isAdvanced,
            countA.abbreviated,
        )

        if (lines.emitExactCount) {
            event.toolTip.add("x ${StackCountTextLayout.formatGroupedCount(stackCount)}")
        }

        if (lines.emitStackCurrentMax) {
            event.toolTip.add(
                I18n.format(
                    RuleMessageKey.TOOLTIP_CURRENT_MAX.translationKey,
                    StackCountTextLayout.formatGroupedCount(event.itemStack.count),
                    StackCountTextLayout.formatGroupedCount(event.itemStack.maxStackSize),
                ),
            )
        }
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
