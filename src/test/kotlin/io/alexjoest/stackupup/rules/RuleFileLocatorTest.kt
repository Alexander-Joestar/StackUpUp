package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.rules.io.RuleFileLocator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class RuleFileLocatorTest {
    @Test
    fun `shouldPreferExplicitConfigDir`() {
        val configDir = createTempDirectory("stackupup-config").toFile()
        RuleFileLocator.setConfigDirectory(configDir)

        assertEquals(
            File(File(configDir, StackUpUpIds.RULES_DIRECTORY_NAME), StackUpUpIds.RULES_FILE_NAME).absolutePath,
            RuleFileLocator.resolve().absolutePath,
        )
    }

    @Test
    fun `noExplicit_shouldFallbackToRunConfig`() {
        RuleFileLocator.resetForTests()

        assertEquals(
            File(File("run/config", StackUpUpIds.RULES_DIRECTORY_NAME), StackUpUpIds.RULES_FILE_NAME).absolutePath,
            RuleFileLocator.resolve().absolutePath,
        )
    }
}
