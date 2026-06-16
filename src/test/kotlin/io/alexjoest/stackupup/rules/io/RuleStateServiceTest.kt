package io.alexjoest.stackupup.rules.io

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class RuleStateServiceTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `getState_shouldReturnNullWhenStoreUnavailable`() {
        val service = RuleStateService { null }

        assertNull(service.getState("phase1"))
    }

    @Test
    fun `getState_shouldReturnFalseWhenStateKeyMissing`() {
        val stateFile = createStateFile(
            """
            # state
            - phase1 = true
            """.trimIndent(),
        )
        val service = RuleStateService { stateFile }

        assertFalse(service.getState("phase2")!!)
    }

    @Test
    fun `getState_shouldReturnStoredStateWhenKeyExists`() {
        val stateFile = createStateFile(
            """
            # state
            - phase1 = true
            - phase2 = false
            """.trimIndent(),
        )
        val service = RuleStateService { stateFile }

        assertTrue(service.getState("phase1")!!)
        assertEquals(false, service.getState("phase2"))
    }

    private fun createStateFile(content: String): File {
        return tempDir.resolve("main.su.md").toFile().apply {
            writeText(content + System.lineSeparator(), Charsets.UTF_8)
        }
    }
}
