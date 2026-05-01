package io.alexjoest.stackupup.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class ConfigGuiSourceTest {
    @Test
    fun `config gui should filter out singleton instance entry`() {
        val source = String(
            Files.readAllBytes(Paths.get("src/main/kotlin/io/alexjoest/stackupup/config/ConfigGui.kt")),
            StandardCharsets.UTF_8,
        )

        assertTrue(source.contains("sanitizeConfigElements"))
        assertTrue(source.contains("it.name.equals(\"instance\", ignoreCase = true)"))
        assertTrue(source.contains("sanitizeConfigElements(children)"))
        assertTrue(source.contains("element.childElements"))
    }
}
