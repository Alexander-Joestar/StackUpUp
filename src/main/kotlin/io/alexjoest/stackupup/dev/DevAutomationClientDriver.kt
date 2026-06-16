package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.StackLimitHooks
import io.alexjoest.stackupup.StackUpUp
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.limit.StackContextResolver
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.item.ItemStack
import net.minecraft.world.GameType
import net.minecraft.world.WorldSettings
import net.minecraft.world.WorldType
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

/**
 * 开发期全自动验收客户端桥接器。
 */
class DevAutomationClientDriver(private val controller: DevAutomationController = DevAutomationController()) {
    private var resolvedTarget: ResolvedDevTarget? = null
    private var observationLogged: Boolean = false
    private var inventoryCleared: Boolean = false

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) {
            return
        }

        val minecraft = Minecraft.getMinecraft()
        val player = minecraft.player
        val snapshot = DevAutomationSnapshot(
            atMainMenu = minecraft.world == null && minecraft.currentScreen is GuiMainMenu,
            hasWorld = minecraft.world != null,
            hasPlayer = player != null,
            targetItemObserved = player?.inventory?.mainInventory?.any(::matchesTargetItem) == true,
        )

        if (player != null && snapshot.targetItemObserved && !observationLogged) {
            logObservedTarget(player.inventory.mainInventory.filter(::matchesTargetItem))
        }

        for (action in controller.advance(snapshot)) {
            when (action) {
                DevAutomationAction.LaunchWorld -> launchTestWorld(minecraft)
                DevAutomationAction.GiveTargetItem -> giveTargetItem(minecraft)
            }
        }
    }

    private fun launchTestWorld(minecraft: Minecraft) {
        val settings = WorldSettings(
            0L,
            GameType.CREATIVE,
            false,
            false,
            WorldType.DEFAULT,
        ).enableCommands()

        StackUpUp.logger?.info(
            "开发自动验收：准备进入测试世界 folder={} name={}",
            DevAutomationConfig.worldFolder,
            DevAutomationConfig.worldName,
        )
        minecraft.launchIntegratedServer(
            DevAutomationConfig.worldFolder,
            DevAutomationConfig.worldName,
            settings,
        )
    }

    private fun giveTargetItem(minecraft: Minecraft) {
        val player = minecraft.player ?: return
        when (val injection = DevRuleInjector.ensureInjected(DevAutomationConfig.tempRule)) {
            is DevRuleInjectionResult.Applied -> {
                StackUpUp.logger?.info(
                    "开发自动验收：已注入临时规则 `{}`，规则数 {} -> {}。",
                    injection.ruleLine,
                    injection.previousRuleCount,
                    injection.newRuleCount,
                )
            }

            is DevRuleInjectionResult.Failed -> {
                StackUpUp.logger?.error("开发自动验收：临时规则注入失败：{}", injection.errors.joinToString("；"))
                controller.abort()
                return
            }

            DevRuleInjectionResult.Skipped -> Unit
        }

        if (DevAutomationConfig.clearInventoryBeforeGive && !inventoryCleared) {
            inventoryCleared = true
            player.sendChatMessage("/clear @p")
        }

        val target = resolveTarget()
        val probeStack = target?.stack ?: ItemStack.EMPTY
        if (probeStack.isEmpty) {
            StackUpUp.logger?.error(
                "开发自动验收：未找到目标物品 item={} meta={} ore={}，自动测试中止。",
                DevAutomationConfig.itemId.ifBlank { "<未指定>" },
                DevAutomationConfig.itemMeta,
                DevAutomationConfig.oreName,
            )
            controller.abort()
            return
        }
        val resolvedTarget = target ?: return

        val context = StackContextResolver.fromStack(
            stack = probeStack,
            baseLimit = StackLimitHooks.resolveOriginalBaseline(probeStack),
            requirements = RuleRuntime.limitService().contextRequirements(),
        ) ?: return
        val resolvedLimit = RuleRuntime.limitService().resolve(context)

        StackUpUp.logger?.info(
            "开发自动验收：准备发放 {}@{} x{}，矿辞={}，解析堆叠上限={}，实际栈上限={}。",
            resolvedTarget.itemId,
            resolvedTarget.meta,
            DevAutomationConfig.itemCount,
            context.oreNames.joinToString(prefix = "[", postfix = "]"),
            resolvedLimit,
            probeStack.maxStackSize,
        )
        player.sendChatMessage(
            "/give @p ${resolvedTarget.itemId} ${DevAutomationConfig.itemCount} ${resolvedTarget.meta}",
        )
    }

    private fun resolveTarget(): ResolvedDevTarget? {
        resolvedTarget?.let { return it }
        val target = DevTargetRuntimeResolver.resolve()
        resolvedTarget = target
        return target
    }

    private fun matchesTargetItem(stack: ItemStack): Boolean {
        if (stack.isEmpty) {
            return false
        }

        val target = resolvedTarget ?: return false
        val registryName = stack.item.registryName?.toString() ?: return false
        return registryName == target.itemId && stack.metadata == target.meta
    }

    private fun logObservedTarget(matchingStacks: List<ItemStack>) {
        val first = matchingStacks.firstOrNull() ?: return
        observationLogged = true
        StackUpUp.logger?.info(
            "开发自动验收：已观察到目标物品 {}@{}，匹配栈数={}，总数量={}，首栈数量={}，首栈上限={}，矿辞={}。",
            first.item.registryName,
            first.metadata,
            matchingStacks.size,
            matchingStacks.sumOf { it.count },
            first.count,
            first.maxStackSize,
            RuleRuntime.oreDictIndex().getOreNames(first).joinToString(prefix = "[", postfix = "]"),
        )
    }
}
