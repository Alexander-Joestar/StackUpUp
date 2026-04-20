package io.alexjoest.stackupup.rules

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.rules.io.RuleFileLocator

class RuleFileLocatorTest {
    @Test
    fun `应当优先使用显式配置目录`() {
        val configDir = createTempDirectory("stackupup-config").toFile()
        RuleFileLocator.setConfigDirectory(configDir)

        assertEquals(
            File(File(configDir, StackUpUpIds.RULES_DIRECTORY_NAME), StackUpUpIds.RULES_FILE_NAME).absolutePath,
            RuleFileLocator.resolve().absolutePath
        )
    }

    @Test
    fun `未设置显式目录时应回退到 run config`() {
        RuleFileLocator.resetForTests()

        assertEquals(
            File(File("run/config", StackUpUpIds.RULES_DIRECTORY_NAME), StackUpUpIds.RULES_FILE_NAME).absolutePath,
            RuleFileLocator.resolve().absolutePath
        )
    }
}
