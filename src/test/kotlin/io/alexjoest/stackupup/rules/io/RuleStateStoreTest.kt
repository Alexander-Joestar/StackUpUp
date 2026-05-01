package io.alexjoest.stackupup.rules.io

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class RuleStateStoreTest {
    @Test
    fun `shouldPreserveCommentsWhileUpdatingStateLines`() {
        val tempDir = createTempDirectory("stackupup-state-store").toFile()
        val file = File(tempDir, "main.su.md").apply {
            writeText(
                """
                # intro
                documentation

                # state
                keep this note
                - phase1 = true
                - phase2 = false
                another note

                # rules
                item = minecraft:egg -> 64
                """.trimIndent() + System.lineSeparator(),
                Charsets.UTF_8,
            )
        }
        val store = RuleStateStore(file)

        assertEquals(mapOf("phase1" to true, "phase2" to false), store.readStates())

        store.writeStates(
            mapOf(
                "phase1" to false,
                "phase2" to true,
                "phase3" to true,
            ),
        )

        val text = file.readText(Charsets.UTF_8)
        assertTrue(text.contains("keep this note"))
        assertTrue(text.contains("another note"))
        assertTrue(text.contains("- phase1 = false"))
        assertTrue(text.contains("- phase2 = true"))
        assertTrue(text.contains("- phase3 = true"))
        assertTrue(text.contains("item = minecraft:egg -> 64"))
    }
}
