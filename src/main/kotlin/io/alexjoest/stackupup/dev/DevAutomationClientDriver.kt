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
    private var reloadGuidanceLogged: Boolean = false

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
            resourceReloadRequested = DevAutomationConfig.resourceReload,
        )

        if (player != null && snapshot.targetItemObserved && !observationLogged) {
            logObservedTarget(player.inventory.mainInventory.filter(::matchesTargetItem))
        }

        for (action in controller.advance(snapshot)) {
            when (action) {
                DevAutomationAction.LaunchWorld -> launchTestWorld(minecraft)
                DevAutomationAction.GiveTargetItem -> giveTargetItem(minecraft)
                DevAutomationAction.ReloadResources -> logReloadResourcesGuidance()
            }
        }
    }

    /**
     * T8.0：输出客户端操作指示。客户端资源重载（F3+T）必须由人工在真实窗口操作——锁屏/无头环境下
     * 自动化注入不可行，且 F3+T 属于真实交互。本方法把「请在客户端主菜单按 F3+T」写入日志与指导文件，
     * 并记录重载前自有消息解析状态，供人工操作后对比。
     */
    private fun logReloadResourcesGuidance() {
        if (reloadGuidanceLogged) {
            return
        }
        reloadGuidanceLogged = true
        val message = resolveLocalizedMessageOrUnavailable()
        StackUpUp.logger?.info(
            "开发自动验收[T8.0]：请在客户端主菜单按 F3+T 触发完整资源重载（SimpleReloadableResourceManager 链路）；" +
                "重载前自有消息 key=message.stackupup.command.reload.success -> {}",
            message,
        )
        val guidanceFile = java.io.File("run/logs/t8.0-client-guidance.txt")
        try {
            guidanceFile.parentFile?.mkdirs()
            guidanceFile.writeText(
                buildString {
                    appendLine("T8.0 客户端资源重载基线（人工操作指引）")
                    appendLine("1. 在客户端主菜单（本日志出现时）按 F3+T 触发完整资源重载。")
                    appendLine("2. 观察游戏内消息/日志，确认资源重载完成（第二次 Reloading ResourceManager）。")
                    appendLine("3. 重载前自有消息状态：$message")
                    appendLine("4. 重载完成后（进世界后）在日志中查看 '重载后自有消息' 记录。")
                    appendLine("参考判据：run/logs/latest.log 出现第二次 'Reloading ResourceManager' 行。")
                },
            )
            StackUpUp.logger?.info("开发自动验收[T8.0]：操作指引已写入 {}", guidanceFile.absolutePath)
        } catch (t: Throwable) {
            StackUpUp.logger?.error("开发自动验收[T8.0]：写入操作指引失败：{}", t.message)
        }
    }

    private fun resolveLocalizedMessageOrUnavailable(): String = try {
        val message = io.alexjoest.stackupup.LocalizedMessages.format("message.stackupup.command.reload.success")
        message.ifBlank { "<blank>" }
    } catch (t: Throwable) {
        "<unavailable:${t.javaClass.simpleName}>"
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
