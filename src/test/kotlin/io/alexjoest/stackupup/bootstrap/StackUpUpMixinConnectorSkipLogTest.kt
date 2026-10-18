package io.alexjoest.stackupup.bootstrap

import io.alexjoest.stackupup.StackUpUpCore
import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.core.InventoryStackLimitHook
import io.alexjoest.stackupup.core.resetInventoryExpansionForTesting
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.ErrorHandler
import org.apache.logging.log4j.core.Layout
import org.apache.logging.log4j.core.LifeCycle.State
import org.apache.logging.log4j.core.LogEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import org.apache.logging.log4j.core.Logger as CoreLogger

/**
 * connector 入队、跳过与冲突禁用的可执行断言（T14.5 停止条件 2/3/5）。
 *
 * 断言真实 logger 输出：项目 classpath 的 log4j-core 是裁剪 jar（无 ListAppender，
 * `./gradlew dependencies --configuration testCompileClasspath` 与 javap 证据），因此测试自带最小
 * [CollectingAppender]（实现 core.Appender 接口）挂到目标 logger 上捕获消息。
 * - mod 缺失 / 未知配置 → 结构化日志 + 拒绝装载；
 * - required:false 配置（brandonscore）mod 缺失同样记录；
 * - 冲突禁用保留设计语义（拒绝 early 装载）但必须 ERROR 说明原因。
 *
 * 对全局 logger 的 level/appender 修改在 finally 中恢复；项目未开启 JUnit 并行，
 * 且该 logger 名仅本 connector 使用，不影响其它测试。
 */
class StackUpUpMixinConnectorSkipLogTest {

    @BeforeEach
    fun resetExpansionBeforeTest() {
        resetInventoryExpansionForTesting()
    }

    @AfterEach
    fun resetExpansionAfterTest() {
        resetInventoryExpansionForTesting()
    }

    private fun capturedLogs(loggerName: String = "stackupup.mixin.connector", block: () -> Unit): List<String> {
        val collected = mutableListOf<String>()
        val appender = CollectingAppender(collected)
        val logger = LogManager.getLogger(loggerName) as CoreLogger
        val previousLevel = logger.level
        appender.start()
        logger.addAppender(appender)
        logger.setLevel(Level.INFO)
        try {
            block()
            return collected.toList()
        } finally {
            logger.removeAppender(appender)
            if (previousLevel != null) {
                logger.setLevel(previousLevel)
            }
            appender.stop()
        }
    }

    @Test
    fun modAbsent_shouldLogStructuredSkipAndReturnFalse() {
        val messages = capturedLogs {
            assertFalse(
                StackUpUpMixinConnector().shouldQueue(StackUpUpIds.LATE_AE2_MIXIN_CONFIG) { false },
            )
        }
        assertTrue(
            messages.any { it.contains(StackUpUpIds.LATE_AE2_MIXIN_CONFIG) && it.contains("appliedenergistics2") && it.contains("not present") },
            "mod 缺失必须留结构化日志，实际: $messages",
        )
    }

    @Test
    fun requiredFalseConfigModAbsent_shouldAlsoLogSkip() {
        val messages = capturedLogs {
            assertFalse(
                StackUpUpMixinConnector().shouldQueue(StackUpUpIds.LATE_ENDERIO_MIXIN_CONFIG) { false },
            )
        }
        assertTrue(
            messages.any { it.contains(StackUpUpIds.LATE_ENDERIO_MIXIN_CONFIG) },
            "required:false 配置缺失同样必须记录，实际: $messages",
        )
    }

    @Test
    fun unknownConfig_shouldLogErrorAndReturnFalseInsteadOfUnconditionalQueue() {
        val messages = capturedLogs {
            assertFalse(
                StackUpUpMixinConnector().shouldQueue("mixins.stackupup.late.ghost.json") { true },
            )
        }
        assertTrue(
            messages.any { it.contains("mixins.stackupup.late.ghost.json") && it.contains("not registered") },
            "未知配置不得无条件入队且必须留 ERROR 日志，实际: $messages",
        )
    }

    @Test
    fun capacityConfigsPresent_shouldQueueWhenRequiredModsArePresent() {
        val connector = StackUpUpMixinConnector()
        val queued = mutableListOf<String>()
        InventoryStackLimitHook.configureExpansionEnabled(true)
        assertTrue(InventoryStackLimitHook.isExpansionEnabled())

        connector.connectLate(emptyList(), { true }) { queued += it }

        val capacityConfigsOnDisk = Files.list(Paths.get("src", "main", "resources")).use { stream ->
            stream.map { it.fileName.toString() }
                .filter { it.startsWith("mixins.stackupup.capacity.late.") && it.endsWith(".json") }
                .collect(java.util.stream.Collectors.toSet())
        }
        assertEquals(
            capacityConfigsOnDisk,
            connector.capacityModules.map { it.config }.toSet(),
            "capacity registry 必须与 src/main/resources 中的 capacity late 配置一一对应",
        )
        assertEquals(
            (connector.modules + connector.capacityModules).map { it.config },
            queued,
            "实际 connectLate 必须按顺序将正常配置及全部 capacity 配置各入队一次",
        )
        connector.capacityModules.forEach { module ->
            assertTrue(
                Files.exists(Paths.get("src/main/resources", module.config)),
                "capacity registry entry must have a matching resource: ${module.config}",
            )
        }
    }

    @Test
    fun capacityConfigAbsent_shouldRejectUnknownConfig() {
        val messages = capturedLogs {
            assertFalse(
                StackUpUpMixinConnector().shouldQueue(
                    "mixins.stackupup.capacity.late.ghost.json",
                    { true },
                    StackUpUpMixinConnector().capacityModules,
                ),
                "未登记的 capacity 配置不得入队",
            )
        }

        assertTrue(
            messages.any { it.contains("mixins.stackupup.capacity.late.ghost.json") && it.contains("not registered") },
            "未知 capacity 配置必须留 ERROR 日志，实际: $messages",
        )
    }

    @Test
    fun capacityConfigsPresent_shouldSkipWhenRequiredModsAreAbsent() {
        val connector = StackUpUpMixinConnector()
        val queued = mutableListOf<String>()
        InventoryStackLimitHook.configureExpansionEnabled(true)
        assertTrue(InventoryStackLimitHook.isExpansionEnabled())
        val messages = capturedLogs {
            connector.connectLate(emptyList(), { false }) { queued += it }
        }

        assertEquals(emptyList<String>(), queued, "所有目标 mod 缺失时实际队列必须为空")
        connector.capacityModules.forEach { module ->
            assertTrue(
                messages.any { it.contains(module.config) && it.contains(module.modId) && it.contains("not present") },
                "目标 mod 缺失必须留结构化日志: ${module.config}; 实际: $messages",
            )
        }
    }

    @Test
    fun connectLate_shouldSkipOnlyConfigsWhoseRequiredModIsAbsent() {
        val connector = StackUpUpMixinConnector()
        InventoryStackLimitHook.configureExpansionEnabled(true)
        assertTrue(InventoryStackLimitHook.isExpansionEnabled())

        connector.capacityModules.forEach { missingModule ->
            val queued = mutableListOf<String>()
            connector.connectLate(emptyList(), { it != missingModule.modId }) { queued += it }
            assertEquals(
                (connector.modules + connector.capacityModules)
                    .filter { it.modId != missingModule.modId }
                    .map { it.config },
                queued,
                "缺失 ${missingModule.modId} 时只跳过依赖该 mod 的实际入队配置",
            )
        }
    }

    @Test
    fun connectLate_shouldSkipOnlyListedCapacityModulesAndKeepNonCapacityModules() {
        val connector = StackUpUpMixinConnector()
        InventoryStackLimitHook.configureExpansionEnabled(true)
        connector.capacityModules.forEach { listedModule ->
            val queued = mutableListOf<String>()
            val messages = capturedLogs {
                connector.connectLate(emptyList(), { true }, setOf(listedModule.modId)) { queued += it }
            }

            assertEquals(
                connector.modules.map { it.config } +
                    connector.capacityModules.filter { it.modId != listedModule.modId }.map { it.config },
                queued,
            )
            assertTrue(messages.any { it.contains(listedModule.config) && it.contains("blacklisted") })
        }
    }

    @Test
    fun connectLate_shouldNotQueueCapacityWhenExpansionIsDisabled() {
        val connector = StackUpUpMixinConnector()
        val queued = mutableListOf<String>()
        InventoryStackLimitHook.configureExpansionEnabled(false)
        assertFalse(InventoryStackLimitHook.isExpansionEnabled())

        connector.connectLate(emptyList(), { true }) { queued += it }

        assertEquals(connector.modules.map { it.config }, queued)
    }

    @Test
    fun connectLate_shouldNotQueueCapacityWhenConflictingModIsPresent() {
        val connector = StackUpUpMixinConnector()
        val queued = mutableListOf<String>()
        InventoryStackLimitHook.configureExpansionEnabled(true)
        assertTrue(InventoryStackLimitHook.isExpansionEnabled())
        val messages = capturedLogs {
            connector.connectLate(listOf("StackUp"), { true }) { queued += it }
        }

        assertEquals(connector.modules.map { it.config }, queued)
        assertTrue(messages.any { it.contains("StackUp") && it.contains("Capacity late mixin configs are NOT queued") })
    }

    @Test
    fun connectorAe2Probe_shouldFallbackWhenForgePresenceIsUnavailable() {
        var cleanroomProbeCalled = false
        val present = StackUpUpMixinConnector().isModPresentForConnector(
            "ae2",
            forgeProbe = { throw NullPointerException("namedMods") },
            cleanroomProbe = {
                cleanroomProbeCalled = true
                true
            },
        )
        assertTrue(present, "Forge connector probe 过早失败时必须尝试 Cleanroom fallback")
        assertTrue(cleanroomProbeCalled, "Forge connector probe 不可用时必须调用 Cleanroom fallback")
    }

    @Test
    fun connectorAe2Probe_shouldFallbackWhenModDiscovererReportsAbsence() {
        var cleanroomProbeCalled = false
        val present = StackUpUpMixinConnector().isModPresentForConnector(
            "ae2",
            forgeProbe = { false },
            cleanroomProbe = {
                cleanroomProbeCalled = true
                true
            },
        )
        assertTrue(present, "ModDiscoverer 未发现 mod 时必须尝试 Cleanroom fallback")
        assertTrue(cleanroomProbeCalled, "ModDiscoverer 返回 false 时必须调用 fallback")
    }

    @Test
    fun offStartup_stillQueuesCoreBaselineButNotGenericExpansion() {
        InventoryStackLimitHook.configureExpansionEnabled(false)
        val queued = mutableListOf<String>()
        StackUpUpMixinConnector().connectEarly(emptyList()) { queued += it }
        assertEquals(listOf(StackUpUpIds.EARLY_MIXIN_CONFIG, "mixins.stackupup.baseline.early.json"), queued)
    }

    @Test
    fun earlyQueueFailure_shouldDescribePartialRegistration() {
        val queued = mutableListOf<String>()
        val messages = capturedLogs {
            StackUpUpMixinConnector().connectEarly(emptyList()) { config ->
                if (config == "mixins.stackupup.baseline.early.json") {
                    throw IllegalStateException("queue failure")
                }
                queued += config
            }
        }

        assertEquals(listOf(StackUpUpIds.EARLY_MIXIN_CONFIG), queued)
        assertTrue(
            messages.any {
                it.contains("previously queued early configs remain registered") && it.contains("late mixins continue")
            },
            "部分入队失败日志必须说明已入队配置不会回滚且 late 仍继续，实际: $messages",
        )
    }

    @Test
    fun noConflict_shouldAllowEarlyQueue() {
        assertTrue(
            StackUpUpMixinConnector().shouldQueueEarly(emptyList()),
            "无冲突时 early 配置必须允许装载",
        )
    }

    @Test
    fun conflictDisabled_shouldKeepDesignSemanticsAndLogErrorExplainingWhy() {
        val disabledProperty = "${StackUpUpIds.MOD_ID}.conflict.disabled"
        val modsProperty = "${StackUpUpIds.MOD_ID}.conflict.mods"
        System.setProperty(disabledProperty, "true")
        System.setProperty(modsProperty, "StackUp")
        InventoryStackLimitHook.configureExpansionEnabled(true)
        InventoryStackLimitHook.prepare(256, null)
        try {
            val conflicts = StackUpUpCore.ensureConflictState()
            val messages = capturedLogs {
                assertFalse(
                    StackUpUpMixinConnector().shouldQueueCapacity(conflicts),
                    "冲突时即使扩容已启用也必须拒绝 capacity 配置",
                )
                assertFalse(
                    StackUpUpMixinConnector().shouldQueueEarly(conflicts),
                    "冲突禁用必须拒绝 early 装载",
                )
            }
            assertTrue(
                messages.any { it.contains(StackUpUpIds.EARLY_MIXIN_CONFIG) && it.contains("StackUp") && it.contains("NOT queued") },
                "冲突禁用必须 ERROR 说明原因，实际: $messages",
            )
        } finally {
            InventoryStackLimitHook.configureExpansionEnabled(false)
            System.clearProperty(disabledProperty)
            System.clearProperty(modsProperty)
        }
    }

    /**
     * 最小内存 appender：只收集格式化消息，不写任何输出。
     * 项目 classpath 的 log4j-core 为裁剪 jar（无 ListAppender），故测试自带实现。
     */
    private class CollectingAppender(private val collected: MutableList<String>) : Appender {
        @Volatile
        private var started: Boolean = false

        override fun append(event: LogEvent) {
            collected += event.message.formattedMessage
        }

        override fun getName(): String = "t14-5-collecting"

        override fun getLayout(): Layout<*>? = null

        override fun ignoreExceptions(): Boolean = true

        override fun getHandler(): ErrorHandler? = null

        override fun setHandler(handler: ErrorHandler?) = Unit

        override fun getState(): State = if (started) State.STARTED else State.INITIALIZED

        override fun initialize() = Unit

        override fun start() {
            started = true
        }

        override fun stop() {
            started = false
        }

        override fun isStarted(): Boolean = started

        override fun isStopped(): Boolean = !started
    }
}
