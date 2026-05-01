package io.alexjoest.stackupup.rules.io

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class RuleFileTemplateTest {
    @Test
    fun `defaultTemplate_shouldCreateEmptyRuleFile`() {
        val tempDir = createTempDirectory("stackupup-rule-template").toFile()
        val file = File(tempDir, "main.su")

        RuleFileTemplate.ensureExists(file)
        val text = file.readText(Charsets.UTF_8)

        assertEquals("", text)
    }
}
