package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DevAutomationControllerTest {
    @Test
    fun `位于主菜单时应只启动一次测试世界`() {
        val controller = DevAutomationController()

        val firstActions = controller.advance(DevAutomationSnapshot(atMainMenu = true))
        val secondActions = controller.advance(DevAutomationSnapshot(atMainMenu = true))

        assertEquals(listOf(DevAutomationAction.LaunchWorld), firstActions)
        assertEquals(emptyList<DevAutomationAction>(), secondActions)
    }

    @Test
    fun `进入世界后应只发放一次目标物品`() {
        val controller = DevAutomationController()

        controller.advance(DevAutomationSnapshot(atMainMenu = true))
        val firstActions = controller.advance(DevAutomationSnapshot(hasWorld = true, hasPlayer = true))
        val secondActions = controller.advance(DevAutomationSnapshot(hasWorld = true, hasPlayer = true))

        assertEquals(listOf(DevAutomationAction.GiveTargetItem), firstActions)
        assertEquals(emptyList<DevAutomationAction>(), secondActions)
    }

    @Test
    fun `观察到目标物品后应结束流程`() {
        val controller = DevAutomationController()

        controller.advance(DevAutomationSnapshot(atMainMenu = true))
        controller.advance(DevAutomationSnapshot(hasWorld = true, hasPlayer = true))
        val completionActions = controller.advance(
            DevAutomationSnapshot(
                hasWorld = true,
                hasPlayer = true,
                targetItemObserved = true
            )
        )
        val laterActions = controller.advance(
            DevAutomationSnapshot(
                hasWorld = true,
                hasPlayer = true,
                targetItemObserved = true
            )
        )

        assertEquals(emptyList<DevAutomationAction>(), completionActions)
        assertEquals(emptyList<DevAutomationAction>(), laterActions)
    }
}

