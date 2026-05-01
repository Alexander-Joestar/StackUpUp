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
            state = DevAutomationState.WAITING_FOR_CONFIRMATION
            confirmationTicks = 0
            return listOf(DevAutomationAction.GiveTargetItem)
        }

        if (snapshot.atMainMenu) {
            state = DevAutomationState.WAITING_FOR_WORLD
            return listOf(DevAutomationAction.LaunchWorld)
        }

        return emptyList()
    }

    private fun handleWaitingForWorld(snapshot: DevAutomationSnapshot): List<DevAutomationAction> {
        if (!snapshot.hasWorld || !snapshot.hasPlayer) {
            return emptyList()
        }

        state = DevAutomationState.WAITING_FOR_CONFIRMATION
        confirmationTicks = 0
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
)

sealed class DevAutomationAction {
    data object LaunchWorld : DevAutomationAction()
    data object GiveTargetItem : DevAutomationAction()
}
