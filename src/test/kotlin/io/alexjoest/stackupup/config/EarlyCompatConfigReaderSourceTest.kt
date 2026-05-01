package io.alexjoest.stackupup.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class EarlyCompatConfigReaderSourceTest {
    @Test
    fun `early reader should read raw cfg instead of Forge Config object`() {
        val source = readSource("src/main/kotlin/io/alexjoest/stackupup/config/EarlyCompatConfigReader.kt")

        assertFalse(source.contains("Configuration(configFile)"))
        assertTrue(source.contains("readText(Charsets.UTF_8)"))
        assertTrue(source.contains("COMPAT_CATEGORY = \"compatibility\""))
        assertTrue(source.contains("stackupup.cfg"))
        assertTrue(!source.contains("import io.alexjoest.stackupup.StackUpUpConfig"))
    }

    @Test
    fun `early reader parses compatibility booleans from raw cfg`() {
        val values = EarlyCompatConfigReader.parseConfigText(
            """
                general {
                    I:maxStackSize=128
                }

                compatibility {
                    B:ae2=false
                    B:brandonsCore=true
                    B:displayNameFallback=false
                }
            """.trimIndent(),
        )

        assertEquals(false, values["ae2"])
        assertEquals(true, values["brandonsCore"])
        assertEquals(false, values["displayNameFallback"])
        assertFalse(values.containsKey("maxStackSize"))
    }

    @Test
    fun `missing compat entries should default to enabled`() {
        val values = EarlyCompatConfigReader.parseConfigText(
            """
                compatibility {
                    B:ae2=false
                }
            """.trimIndent(),
        )

        assertTrue(values["brandonsCore"] ?: true)
        assertTrue(values["refinedStorage"] ?: true)
    }

    @Test
    fun `config sanitizer should preserve compatibility category`() {
        val source = readSource("src/main/kotlin/io/alexjoest/stackupup/config/ConfigFileSanitizer.kt")

        assertTrue(source.contains("\"compatibility\""))
    }

    @Test
    fun `late mixin loader should gate by raw compat reader`() {
        val source = readSource("src/main/kotlin/io/alexjoest/stackupup/bootstrap/StackUpUpLateMixinLoader.kt")

        assertTrue(source.contains("EarlyCompatConfigReader.isModuleEnabled(module.configName)"))
        assertFalse(source.contains("return StackUpUpConfig.compatibility"))
    }

    private fun readSource(path: String): String = String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
}
