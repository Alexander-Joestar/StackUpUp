package io.alexjoest.stackupup.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class ConfigGuiFactorySourceTest {
    @Test
    fun `config gui factory should stay wired to gui config`() {
        val source = String(
            Files.readAllBytes(Paths.get("src/main/kotlin/io/alexjoest/stackupup/config/ConfigGuiFactory.kt")),
            StandardCharsets.UTF_8,
        )

        assertTrue(source.contains("override fun hasConfigGui(): Boolean = true"))
        assertTrue(source.contains("override fun createConfigGui(parentScreen: GuiScreen?): GuiScreen = ConfigGui(parentScreen)"))
        assertFalse(source.contains("runtimeGuiCategories(): MutableSet<IModGuiFactory.RuntimeOptionCategoryElement> = mutableSetOf"))
    }
}
