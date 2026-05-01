package io.alexjoest.stackupup.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DevAutomationControllerTest {
    @Test
    fun `atMainMenu_shouldStartWorldOnce`() {
        val controller = DevAutomationController()

        val firstActions = controller.advance(DevAutomationSnapshot(atMainMenu = true))
        val secondActions = controller.advance(DevAutomationSnapshot(atMainMenu = true))

        assertEquals(listOf(DevAutomationAction.LaunchWorld), firstActions)
        assertEquals(emptyList<DevAutomationAction>(), secondActions)
    }

    @Test
    fun `enteredWorld_shouldGiveTargetItemOnce`() {
        val controller = DevAutomationController()

        controller.advance(DevAutomationSnapshot(atMainMenu = true))
        val firstActions = controller.advance(DevAutomationSnapshot(hasWorld = true, hasPlayer = true))
        val secondActions = controller.advance(DevAutomationSnapshot(hasWorld = true, hasPlayer = true))

        assertEquals(listOf(DevAutomationAction.GiveTargetItem), firstActions)
        assertEquals(emptyList<DevAutomationAction>(), secondActions)
    }

    @Test
    fun `itemObserved_shouldEndFlow`() {
        val controller = DevAutomationController()

        controller.advance(DevAutomationSnapshot(atMainMenu = true))
        controller.advance(DevAutomationSnapshot(hasWorld = true, hasPlayer = true))
        val completionActions = controller.advance(
            DevAutomationSnapshot(
                hasWorld = true,
                hasPlayer = true,
                targetItemObserved = true,
            ),
        )
        val laterActions = controller.advance(
            DevAutomationSnapshot(
                hasWorld = true,
                hasPlayer = true,
                targetItemObserved = true,
            ),
        )

        assertEquals(emptyList<DevAutomationAction>(), completionActions)
        assertEquals(emptyList<DevAutomationAction>(), laterActions)
    }
}
