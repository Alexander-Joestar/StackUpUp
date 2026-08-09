package io.alexjoest.stackupup.mixin

import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.bootstrap.StackUpUpMixinConnector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * MixinBooter 装载链集成测试（自 IEarlyMixinLoader/ILateMixinLoader 时代迁移到 IMixinConnector）。
 *
 * 决策函数（shouldQueueEarly/shouldQueue）为行为验证：调用真实代码；mod 在场判断经注入谓词模拟
 * （生产路径由 connector 传入 ModDiscoverer.isModPresent，见结构检查用例）。
 * [connectorSource_shouldKeepLoaderSemantics] 为结构检查：只证明源码保留冲突分支/条件 add/校验调用。
 */
class MixinBooterIntegrationTest {
    @Test
    fun `earlyConfigFileName_shouldBeStable`() {
        assertEquals("mixins.stackupup.early.json", StackUpUpIds.EARLY_MIXIN_CONFIG)
        assertTrue(StackUpUpMixinConnector().shouldQueueEarly(emptyList()), "无冲突时 early 配置应可装载")
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
                "mixins.stackupup.late.nuclearcraft.json",
                "mixins.stackupup.late.colossalchests.json",
                "mixins.stackupup.late.gregtech.json",
            ),
            StackUpUpMixinConnector().modules.map { it.config },
        )
    }

    @Test
    fun `lateConfigFiles_shouldReferenceExistingMixinSources`() {
        for (config in StackUpUpMixinConnector().modules.map { it.config }) {
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
        fun present(vararg modIds: String): (String) -> Boolean = { modId -> modId in modIds }

        val connector = StackUpUpMixinConnector()
        assertTrue(connector.shouldQueue("mixins.stackupup.late.ae2.json", present("appliedenergistics2")))
        assertFalse(connector.shouldQueue("mixins.stackupup.late.ae2.json", present()))

        assertTrue(connector.shouldQueue("mixins.stackupup.late.actuallyadditions.json", present("actuallyadditions")))
        assertFalse(connector.shouldQueue("mixins.stackupup.late.actuallyadditions.json", present()))

        assertTrue(connector.shouldQueue("mixins.stackupup.late.brandonscore.json", present("brandonscore")))
        assertFalse(connector.shouldQueue("mixins.stackupup.late.brandonscore.json", present()))

        assertTrue(connector.shouldQueue("mixins.stackupup.late.mantle.json", present("mantle")))
        assertFalse(connector.shouldQueue("mixins.stackupup.late.mantle.json", present()))

        assertTrue(connector.shouldQueue("mixins.stackupup.late.ic2.json", present("ic2")))
        assertTrue(connector.shouldQueue("mixins.stackupup.late.cyclopscore.json", present("cyclopscore")))
        assertFalse(connector.shouldQueue("mixins.stackupup.late.cyclopscore.json", present()))

        assertTrue(connector.shouldQueue("mixins.stackupup.late.enderio.json", present("enderio")))
        assertFalse(connector.shouldQueue("mixins.stackupup.late.enderio.json", present()))

        assertTrue(connector.shouldQueue("mixins.stackupup.late.refinedstorage.json", present("refinedstorage")))
        assertFalse(connector.shouldQueue("mixins.stackupup.late.refinedstorage.json", present()))

        assertTrue(connector.shouldQueue("mixins.stackupup.late.integrateddynamics.json", present("integrateddynamics")))
        assertFalse(connector.shouldQueue("mixins.stackupup.late.integrateddynamics.json", present()))

        assertTrue(connector.shouldQueue("mixins.stackupup.late.limelib.json", present("limelib")))
        assertFalse(connector.shouldQueue("mixins.stackupup.late.limelib.json", present()))

        assertTrue(connector.shouldQueue("mixins.stackupup.late.immersiveengineering.json", present("immersiveengineering")))
        assertFalse(connector.shouldQueue("mixins.stackupup.late.immersiveengineering.json", present()))

        assertTrue(connector.shouldQueue("mixins.stackupup.late.colossalchests.json", present("colossalchests")))
        assertFalse(connector.shouldQueue("mixins.stackupup.late.colossalchests.json", present()))

        assertTrue(connector.shouldQueue("mixins.stackupup.late.gregtech.json", present("gregtech")))
        assertFalse(connector.shouldQueue("mixins.stackupup.late.gregtech.json", present()))
    }

    @Test
    fun `connectorSource_shouldKeepLoaderSemantics`() {
        // 结构检查（非行为验证）：connect() 装载链必须保留冲突检测分支、mod 在场条件 add 与校验调用，
        // 否则 dev/test 与生产装载入口会脱节；实际装载行为由 runServerAutoTest 运行验证覆盖。
        val source = String(
            Files.readAllBytes(
                Paths.get("src", "main", "kotlin", "io", "alexjoest", "stackupup", "bootstrap", "StackUpUpMixinConnector.kt"),
            ),
            Charsets.UTF_8,
        )
        assertTrue(source.contains("ensureConflictState"), "connect 必须先做冲突检测")
        assertTrue(source.contains("ModDiscoverer.isModPresent"), "late 装载必须按 mod 在场条件 add")
        assertTrue(source.contains("MixinConfigValidator"), "装载前必须保留正向校验")
        assertTrue(source.contains("Mixins.addConfiguration"), "必须通过 Mixins.addConfiguration 入队")
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
