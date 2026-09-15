package io.alexjoest.stackupup.dev

/**
 * 开发期自动验收状态机。
 *
 * 该类只负责根据当前客户端快照决定“下一步该做什么”，
 * 不直接依赖 Minecraft 运行时对象，便于单元测试和后续删除。
 */
class DevAutomationController(private val confirmationRetryTicks: Int = 40) {
    private var state: DevAutomationState = DevAutomationState.WAITING_FOR_ENTRY
    private var confirmationTicks: Int = 0
    private var retriedGive: Boolean = false
    private var reloadGuidancePending: Boolean = false

    fun advance(snapshot: DevAutomationSnapshot): List<DevAutomationAction> = when (state) {
        DevAutomationState.WAITING_FOR_ENTRY -> handleWaitingForEntry(snapshot)
        DevAutomationState.WAITING_FOR_WORLD -> handleWaitingForWorld(snapshot)
        DevAutomationState.WAITING_FOR_CONFIRMATION -> handleWaitingForConfirmation(snapshot)
        DevAutomationState.COMPLETED,
        DevAutomationState.ABORTED,
        -> emptyList()
    }

    fun abort() {
        state = DevAutomationState.ABORTED
    }

    private fun handleWaitingForEntry(snapshot: DevAutomationSnapshot): List<DevAutomationAction> {
        if (snapshot.hasWorld && snapshot.hasPlayer) {
            return enterWorld(snapshot)
        }

        if (snapshot.atMainMenu) {
            // T8.0：主菜单下 currentScreen != null，1.12.2 的 F3 处理块被整块跳过，F3+T 必然 no-op，
            // 因此主菜单阶段只进世界、不输出必须按键的指引；指引推迟到世界内未暂停后再输出。
            reloadGuidancePending = snapshot.resourceReloadRequested
            state = DevAutomationState.WAITING_FOR_WORLD
            return listOf(DevAutomationAction.LaunchWorld)
        }

        return emptyList()
    }

    private fun handleWaitingForWorld(snapshot: DevAutomationSnapshot): List<DevAutomationAction> {
        if (!snapshot.hasWorld || !snapshot.hasPlayer) {
            return emptyList()
        }

        return enterWorld(snapshot)
    }

    /**
     * 世界已就绪后的发放入口。
     *
     * T8.0：启用资源重载观察时必须等 `currentScreen == null`（世界内、未打开任何界面）才输出指引；
     * 只要存在界面，1.12.2 的 F3 处理块就被跳过，此时提示按键必然无效。
     */
    private fun enterWorld(snapshot: DevAutomationSnapshot): List<DevAutomationAction> {
        val guidanceWanted = reloadGuidancePending || snapshot.resourceReloadRequested
        if (guidanceWanted && !snapshot.inUnpausedWorld) {
            return emptyList()
        }

        state = DevAutomationState.WAITING_FOR_CONFIRMATION
        confirmationTicks = 0
        if (guidanceWanted) {
            reloadGuidancePending = false
            return listOf(DevAutomationAction.ReloadResources, DevAutomationAction.GiveTargetItem)
        }
        return listOf(DevAutomationAction.GiveTargetItem)
    }

    private fun handleWaitingForConfirmation(snapshot: DevAutomationSnapshot): List<DevAutomationAction> {
        if (snapshot.targetItemObserved) {
            state = DevAutomationState.COMPLETED
            return emptyList()
        }

        confirmationTicks++
        if (confirmationTicks < confirmationRetryTicks) {
            return emptyList()
        }

        if (retriedGive) {
            state = DevAutomationState.ABORTED
            return emptyList()
        }

        retriedGive = true
        confirmationTicks = 0
        return listOf(DevAutomationAction.GiveTargetItem)
    }
}

enum class DevAutomationState {
    WAITING_FOR_ENTRY,
    WAITING_FOR_WORLD,
    WAITING_FOR_CONFIRMATION,
    COMPLETED,
    ABORTED,
}

data class DevAutomationSnapshot(
    val atMainMenu: Boolean = false,
    val hasWorld: Boolean = false,
    val hasPlayer: Boolean = false,
    val targetItemObserved: Boolean = false,
    /**
     * T8.0：世界内且 `currentScreen == null`（未打开任何界面/未暂停）。
     *
     * 1.12.2 的 F3 处理块被 `currentScreen != null` 门控，只有该字段为 true 时按键的 F3+T 才可能生效，
     * 因此必须按键的 F3+T 指引只允许在此状态下输出。
     */
    val inUnpausedWorld: Boolean = false,
    /** T8.0：是否请求输出资源重载操作指示（由人工按 F3+T 完成真实重载）。 */
    val resourceReloadRequested: Boolean = false,
)

sealed class DevAutomationAction {
    data object LaunchWorld : DevAutomationAction()
    data object GiveTargetItem : DevAutomationAction()

    /** T8.0：输出客户端操作指示（人工按 F3+T 触发真实资源重载）并记录重载前消息状态。 */
    data object ReloadResources : DevAutomationAction()
}
