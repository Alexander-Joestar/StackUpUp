package io.alexjoest.stackupup.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RawConfigFileScannerTest {
    @Test
    fun `sanitizeRootCategories should keep only allowed roots`() {
        val source = """
            # Configuration file

            general { // keep this category
                I:maxStackSize=64
            }

            compatibility {
                B:ae2=true
            }

            legacy { // remove this root category
                B:oldSwitch=true
            }
        """.trimIndent()

        val sanitized = RawConfigFileScanner.sanitizeRootCategories(
            source,
            setOf("general", "client", "compatibility"),
        )

        assertTrue(sanitized.text.contains("general"))
        assertTrue(sanitized.text.contains("compatibility"))
        assertFalse(sanitized.text.contains("legacy"))
        assertEquals(listOf("legacy"), sanitized.removedRootCategories)
    }

    @Test
    fun `readBooleanCategory should read only compatibility booleans`() {
        val source = """
            general { // ignored by compatibility reader
                I:maxStackSize=64
            }

            compatibility {
                B:ae2=false // disable AE2
                B:brandonsCore=true
                // comment with { braces } that should not affect parsing
                B:displayNameFallback=false
            }

            client {
                B:fontScaleLinear=true
            }
        """.trimIndent()

        val values = RawConfigFileScanner.readBooleanCategory(source, "compatibility")

        assertEquals(false, values["ae2"])
        assertEquals(true, values["brandonsCore"])
        assertEquals(false, values["displayNameFallback"])
        assertFalse(values.containsKey("maxStackSize"))
        assertFalse(values.containsKey("fontScaleLinear"))
    }

    @Test
    fun `scanner should ignore utf8 bom`() {
        val source = "\uFEFFcompatibility {\n    B:ae2=false\n}\n"

        val values = RawConfigFileScanner.readBooleanCategory(source, "compatibility")
        val sanitized = RawConfigFileScanner.sanitizeRootCategories(source, setOf("compatibility"))

        assertEquals(false, values["ae2"])
        assertTrue(sanitized.text.contains("compatibility"))
    }

    @Test
    fun `sanitizeRootCategories should preserve crlf line endings`() {
        val source = "general {\r\n    I:maxStackSize=64\r\n}\r\nlegacy {\r\n    B:oldSwitch=true\r\n}\r\n"

        val sanitized = RawConfigFileScanner.sanitizeRootCategories(source, setOf("general", "compatibility"))

        assertTrue(sanitized.text.contains("\r\n"))
        assertFalse(sanitized.text.replace("\r\n", "").contains("\n"))
        assertFalse(sanitized.text.contains("legacy"))
    }

    @Test
    fun `sanitizeRootCategories should not leave a lone newline when all roots are removed`() {
        val source = "legacy {\n    B:oldSwitch=true\n}\n"

        val sanitized = RawConfigFileScanner.sanitizeRootCategories(source, setOf("general", "compatibility"))

        assertEquals("", sanitized.text)
        assertEquals(listOf("legacy"), sanitized.removedRootCategories)
    }
}
