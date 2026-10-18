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
 * [connectorSource_shouldKeepLoaderSemantics] 为结构检查：只证明源码保留冲突分支/条件 add。
 */
class MixinBooterIntegrationTest {
    @Test
    fun earlyConfigFileName_shouldBeStable() {
        assertEquals("mixins.stackupup.early.json", StackUpUpIds.EARLY_MIXIN_CONFIG)
        assertTrue(StackUpUpMixinConnector().shouldQueueEarly(emptyList()), "无冲突时 early 配置应可装载")
    }

    @Test
    fun lateConfigFileName_shouldBeStable() {
        // 只有仍携带 mixin 的 ordinary late 配置留在模块表；其余空配置已随 0.2.5 收尾删除，
        // capacity-only 覆盖改由列表下方的 capacity 模块表登记。
        assertEquals(
            listOf(
                "mixins.stackupup.late.ae2.json",
                "mixins.stackupup.late.enderio.json",
                "mixins.stackupup.late.nuclearcraft.json",
                "mixins.stackupup.late.gregtech.json",
            ),
            StackUpUpMixinConnector().modules.map { it.config },
        )
    }

    @Test
    fun farmersDelightCapacity_shouldNotBeRegisteredAsMixin() {
        // Farmer's Delight Legacy 的 getInventoryStackLimit() 是字面量 return 64;，容量扩展由
        // InventoryStackLimitTransformer 的字面量替换完成（行为证据见 FarmersDelightAsmLiteralPatchTest），
        // 因此 connector 不再注册其 ordinary/capacity 配置，随包也不再携带对应 mixin 源与 json。
        val connector = StackUpUpMixinConnector()
        val registrations = connector.modules + connector.classicModules + connector.capacityModules

        assertEquals(
            emptyList<String>(),
            registrations.filter { it.modId == "farmersdelight" }.map { it.config },
            "farmersdelight 不应再有 late/capacity Mixin 模块",
        )
        assertEquals(
            emptyList<String>(),
            registrations.map { it.config }.filter { it.contains("farmersdelight") },
            "farmersdelight 配置不应出现在模块表中",
        )
        assertEquals(
            emptyList<String>(),
            Files.list(Paths.get("src", "main", "resources")).use { stream ->
                stream.map { it.fileName.toString() }
                    .filter { it.contains("farmersdelight") }
                    .collect(java.util.stream.Collectors.toList())
            },
            "src/main/resources 不应残留 farmersdelight mixin 配置",
        )
        assertFalse(
            Files.exists(
                Paths.get("src", "main", "java", "io", "alexjoest", "stackupup", "mixin", "late", "farmersdelight"),
            ),
            "FarmersDelight mixin 源目录应已删除",
        )
    }

    @Test
    fun lateAndCapacityConfigFiles_shouldReferenceExistingMixinSources() {
        val connector = StackUpUpMixinConnector()
        val registeredConfigs = (connector.modules + connector.classicModules + connector.capacityModules)
            .map { it.config }
        val onDisk = Files.list(Paths.get("src", "main", "resources")).use { stream ->
            stream.map { it.fileName.toString() }
                .filter { it.startsWith("mixins.stackupup.late.") || it.startsWith("mixins.stackupup.capacity.late.") }
                .collect(java.util.stream.Collectors.toSet())
        }
        assertEquals(
            registeredConfigs.toSet(),
            onDisk,
            "磁盘上的 late/capacity late 配置必须与模块表一一对应：既不允许未登记配置，也不允许登记缺失配置",
        )
        for (config in registeredConfigs) {
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
    fun knownProfiles_shouldKeepSafetyMixinsOutOfOptionalCapacityProfiles() {
        assertEquals(
            setOf("AppEngAdaptorItemHandlerMixin", "AppEngInternalAEInventoryWriterMixin"),
            configMixins("mixins.stackupup.late.ae2.json"),
        )
        assertEquals(
            setOf("AppEngInternalAEInventoryMixin", "AppEngInternalInventoryMixin"),
            configMixins("mixins.stackupup.capacity.late.ae2.json"),
        )
        // ContainerAdapter 的 getInventoryStackLimit() 用 `max = 64` 作为 Math.min 折叠初值，因此其 capacity hook
        // 是 AE2S 容器容量的显式决策点（见 ContainerAdapterMixin 的注释），必须留在 AE2S capacity profile 中。
        assertEquals(
            setOf("AppEngInternalInventoryMixin", "ContainerAdapterMixin"),
            configMixins("mixins.stackupup.capacity.late.ae2supergiant.json"),
        )

        assertEquals(
            setOf("EnderIOInventoryNoDropMixin"),
            configMixins("mixins.stackupup.late.enderio.json"),
        )
        assertEquals(
            setOf(
                "EnderIOMachineInventoryLimitMixin",
                "EnderIOSlottedInventoryLimitMixin",
                "EnderIOInventorySlotLimitMixin",
            ),
            configMixins("mixins.stackupup.capacity.late.enderio.json"),
        )

        assertEquals(
            setOf("NuclearCraftDistributorNoDropMixin", "NuclearCraftUpgradeStackLimitMixin"),
            configMixins("mixins.stackupup.late.nuclearcraft.json"),
        )
        assertEquals(
            setOf("NuclearCraftTileInventoryLimitMixin"),
            configMixins("mixins.stackupup.capacity.late.nuclearcraft.json"),
        )

        assertEquals(
            setOf("SlotBaseMixin"),
            configMixins("mixins.stackupup.late.ic2classic.json"),
        )
        // IC2 Classic 的 native-64 保护只存在于 ordinary profile；空的 capacity.late.ic2classic 配置
        // 与对应模块已删除，SlotBaseMixin 不得重新出现在任何 capacity 配置中。
        assertFalse(
            Files.exists(Paths.get("src", "main", "resources", "mixins.stackupup.capacity.late.ic2classic.json")),
            "空的 capacity.late.ic2classic 配置应已删除",
        )
        assertFalse(
            StackUpUpMixinConnector().capacityModules.any { it.config.contains("ic2classic") },
            "ic2classic 不应再有 capacity 模块",
        )
    }

    @Test
    fun capacityLateConfigs_shouldBeSkippedWhenConflictIsDetected() {
        assertFalse(StackUpUpMixinConnector().shouldQueueCapacity(listOf("StackUp")))
    }

    @Test
    fun lateConfig_shouldQueueByModPresence() {
        fun present(vararg modIds: String): (String) -> Boolean = { modId -> modId in modIds }

        val connector = StackUpUpMixinConnector()
        val ordinaryRegistry = connector.modules + connector.classicModules
        for (module in ordinaryRegistry) {
            assertTrue(
                connector.shouldQueue(module.config, present(module.modId), ordinaryRegistry),
                "${module.config} 应在 ${module.modId} 存在时入队",
            )
            assertFalse(
                connector.shouldQueue(module.config, present(), ordinaryRegistry),
                "${module.config} 应在 ${module.modId} 缺失时跳过",
            )
        }
        for (module in connector.capacityModules) {
            assertTrue(
                connector.shouldQueue(module.config, present(module.modId), connector.capacityModules),
                "${module.config} 应在 ${module.modId} 存在时入队",
            )
            assertFalse(
                connector.shouldQueue(module.config, present(), connector.capacityModules),
                "${module.config} 应在 ${module.modId} 缺失时跳过",
            )
        }
        assertFalse(
            connector.shouldQueue("mixins.stackupup.late.storagenetwork.json", present("storagenetwork")),
            "已删除的空配置不得再被模块表接受",
        )
    }

    @Test
    fun connectorSource_shouldKeepLoaderSemantics() {
        // 结构检查（非行为验证）：connect() 装载链必须保留冲突检测分支与条件 add，
        // 否则 dev/test 与生产装载入口会脱节；实际装载行为由 runServerAutoTest 运行验证覆盖。
        val source = String(
            Files.readAllBytes(
                Paths.get("src", "main", "kotlin", "io", "alexjoest", "stackupup", "bootstrap", "StackUpUpMixinConnector.kt"),
            ),
            Charsets.UTF_8,
        )
        assertTrue(source.contains("ensureConflictState"), "connect 必须先做冲突检测")
        assertTrue(source.contains("ModDiscoverer.isModPresent"), "late 装载必须按 mod 在场条件 add")
        assertTrue(source.contains("Mixins.addConfiguration"), "必须通过 Mixins.addConfiguration 入队")
    }

    private fun configMixins(config: String): Set<String> {
        val json = String(
            Files.readAllBytes(Paths.get("src", "main", "resources", config)),
            Charsets.UTF_8,
        )
        return extractJsonStringArray(json, "mixins").toSet()
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
