package io.alexjoest.stackupup.mixin

import io.alexjoest.stackupup.StackUpUpCore
import io.alexjoest.stackupup.bootstrap.StackUpUpLateMixinLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import zone.rong.mixinbooter.Context
import java.nio.file.Files
import java.nio.file.Paths

class MixinBooterIntegrationTest {
    @Test
    fun `earlyConfigFileName_shouldBeStable`() {
        assertEquals(listOf("mixins.stackupup.early.json"), StackUpUpCore().getMixinConfigs())
    }

    @Test
    fun `lateConfigFileName_shouldBeStable`() {
        assertEquals(
            listOf(
                "mixins.stackupup.late.ae2.json",
                "mixins.stackupup.late.brandonscore.json",
                "mixins.stackupup.late.actuallyadditions.json",
                "mixins.stackupup.late.cyclopscore.json",
                "mixins.stackupup.late.enderio.json",
                "mixins.stackupup.late.ic2.json",
                "mixins.stackupup.late.mantle.json",
                "mixins.stackupup.late.refinedstorage.json",
                "mixins.stackupup.late.storagenetwork.json",
                "mixins.stackupup.late.integrateddynamics.json",
                "mixins.stackupup.late.limelib.json",
                "mixins.stackupup.late.immersiveengineering.json",
            ),
            StackUpUpLateMixinLoader().getMixinConfigs(),
        )
    }

    @Test
    fun `lateConfigFiles_shouldReferenceExistingMixinSources`() {
        for (config in StackUpUpLateMixinLoader().getMixinConfigs()) {
            val configPath = Paths.get("src", "main", "resources", config)
            assertTrue(Files.isRegularFile(configPath), "Missing mixin config: $config")

            val json = String(Files.readAllBytes(configPath), Charsets.UTF_8)
            val packageName = requireNotNull(extractJsonString(json, "package")) {
                "Missing package in mixin config: $config"
            }.replace('.', '/')
            val mixins = listOf("mixins", "client", "server").flatMap { key -> extractJsonStringArray(json, key) }

            for (mixin in mixins) {
                val sourcePath = Paths.get("src", "main", "java", packageName, "$mixin.java")
                assertTrue(Files.isRegularFile(sourcePath), "Missing mixin source for $config: $mixin")
            }
        }
    }

    @Test
    fun `lateConfig_shouldQueueByModPresence`() {
        val loader = StackUpUpLateMixinLoader()
        assertTrue(
            loader.shouldMixinConfigQueue(
                Context(
                    "mixins.stackupup.late.ae2.json",
                    listOf("appliedenergistics2"),
                ),
            ),
        )
        assertFalse(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.ae2.json", emptyList())))

        assertTrue(
            loader.shouldMixinConfigQueue(
                Context("mixins.stackupup.late.actuallyadditions.json", listOf("actuallyadditions")),
            ),
        )
        assertFalse(
            loader.shouldMixinConfigQueue(
                Context(
                    "mixins.stackupup.late.actuallyadditions.json",
                    emptyList(),
                ),
            ),
        )

        assertTrue(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.brandonscore.json", listOf("brandonscore"))))
        assertFalse(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.brandonscore.json", emptyList())))

        assertTrue(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.mantle.json", listOf("mantle"))))
        assertFalse(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.mantle.json", emptyList())))

        assertTrue(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.ic2.json", listOf("ic2"))))
        assertTrue(
            loader.shouldMixinConfigQueue(
                Context(
                    "mixins.stackupup.late.cyclopscore.json",
                    listOf("cyclopscore"),
                ),
            ),
        )
        assertFalse(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.cyclopscore.json", emptyList())))

        assertTrue(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.enderio.json", listOf("enderio"))))
        assertFalse(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.enderio.json", emptyList())))

        assertTrue(
            loader.shouldMixinConfigQueue(
                Context(
                    "mixins.stackupup.late.refinedstorage.json",
                    listOf("refinedstorage"),
                ),
            ),
        )
        assertFalse(
            loader.shouldMixinConfigQueue(
                Context(
                    "mixins.stackupup.late.refinedstorage.json",
                    emptyList(),
                ),
            ),
        )

        assertTrue(
            loader.shouldMixinConfigQueue(
                Context("mixins.stackupup.late.integrateddynamics.json", listOf("integrateddynamics")),
            ),
        )
        assertFalse(
            loader.shouldMixinConfigQueue(
                Context("mixins.stackupup.late.integrateddynamics.json", emptyList()),
            ),
        )

        assertTrue(
            loader.shouldMixinConfigQueue(
                Context("mixins.stackupup.late.limelib.json", listOf("limelib")),
            ),
        )
        assertFalse(
            loader.shouldMixinConfigQueue(
                Context("mixins.stackupup.late.limelib.json", emptyList()),
            ),
        )

        assertTrue(
            loader.shouldMixinConfigQueue(
                Context("mixins.stackupup.late.immersiveengineering.json", listOf("immersiveengineering")),
            ),
        )
        assertFalse(
            loader.shouldMixinConfigQueue(
                Context("mixins.stackupup.late.immersiveengineering.json", emptyList()),
            ),
        )
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = Regex(""""$key"\s*:\s*"([^"]+)"""")
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun extractJsonStringArray(json: String, key: String): List<String> {
        val pattern = Regex(""""$key"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
        val body = pattern.find(json)?.groupValues?.get(1) ?: return emptyList()
        return Regex(""""([^"]+)"""")
            .findAll(body)
            .map { it.groupValues[1] }
            .toList()
    }
}
