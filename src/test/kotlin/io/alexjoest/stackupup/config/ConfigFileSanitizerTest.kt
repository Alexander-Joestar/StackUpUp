package io.alexjoest.stackupup.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class ConfigFileSanitizerTest {
    @Test
    fun `sanitize keeps compatibility category and removes unknown roots`() {
        val tempDir = Files.createTempDirectory("stackupup-config-sanitize").toFile()
        val configFile = java.io.File(tempDir, "stackupup.cfg")
        Files.write(
            configFile.toPath(),
            """
                # Configuration file

                general {
                    I:maxStackSize=64
                }

                client {
                    B:fontScaleLinear=false
                }

                compatibility {
                    B:enderio=true
                }

                legacy {
                    B:oldSwitch=true
                }
            """.trimIndent().toByteArray(StandardCharsets.UTF_8),
        )

        ConfigFileSanitizer.sanitize(tempDir)

        val sanitized = String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8)
        assertTrue(sanitized.contains("general"))
        assertTrue(sanitized.contains("client"))
        assertTrue(sanitized.contains("compatibility"))
        assertFalse(sanitized.contains("legacy"))
    }
}
