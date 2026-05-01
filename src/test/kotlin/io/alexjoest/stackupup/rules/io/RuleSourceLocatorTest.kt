package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.StackUpUpIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class RuleSourceLocatorTest {
    @Test
    fun `shouldReturnGlobalWorldUserInOrder`() {
        val tempDir = createTempDirectory("stackupup-source-locator").toFile()
        val configDir = File(tempDir, "config").apply { mkdirs() }
        val rulesDir = File(configDir, StackUpUpIds.MOD_ID).apply { mkdirs() }
        File(rulesDir, "a-pack.su").writeText("", Charsets.UTF_8)
        File(rulesDir, "z-pack.su").writeText("", Charsets.UTF_8)
        File(rulesDir, StackUpUpIds.USER_RULES_FILE_NAME).writeText("", Charsets.UTF_8)

        val worldDir = File(tempDir, "saves/demo/data/${StackUpUpIds.MOD_ID}").apply { mkdirs() }
        File(worldDir, StackUpUpIds.WORLD_RULES_FILE_NAME).writeText("", Charsets.UTF_8)

        RuleFileLocator.setConfigDirectory(configDir)
        RuleSourceLocator.setWorldDirectoryForTests(File(tempDir, "saves/demo"))

        assertEquals(
            listOf(
                File(rulesDir, "a-pack.su").absolutePath,
                File(rulesDir, "z-pack.su").absolutePath,
                File(worldDir, "world.su").absolutePath,
                File(rulesDir, "user.su").absolutePath,
            ),
            RuleSourceLocator.resolveLoadOrder().map(File::getAbsolutePath),
        )
    }
}
