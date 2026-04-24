package io.alexjoest.stackupup.rules.io

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuleFileTemplateTest {
    @Test
    fun `默认模板应覆盖 DSL 核心特性`() {
        val tempDir = createTempDirectory("stackupup-rule-template").toFile()
        val file = File(tempDir, "main.su")

        RuleFileTemplate.ensureExists(file)
        val text = file.readText(Charsets.UTF_8)

        assertTrue(text.contains("item, mod, type, ore, meta, metadata, size"))
        assertTrue(text.contains("type = item"))
        assertTrue(text.contains("type = block"))
        assertTrue(text.contains("2 < size < 64"))
        assertTrue(text.contains("item = gregtech:meta_dust:324"))
        assertTrue(text.contains("item = gregtech:meta_dust@324"))
        assertTrue(text.contains("type = block -> 1024"))
        assertTrue(text.contains("item in [minecraft:egg, minecraft:snowball]"))
        assertTrue(text.contains("&& is evaluated before ||"))
        assertTrue(text.contains("Parentheses are not supported"))
    }
}
