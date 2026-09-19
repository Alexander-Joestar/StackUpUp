package io.alexjoest.stackupup.bootstrap

import io.alexjoest.stackupup.StackUpUpCore
import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.config.MixinToggles
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.ErrorHandler
import org.apache.logging.log4j.core.Layout
import org.apache.logging.log4j.core.LifeCycle.State
import org.apache.logging.log4j.core.LogEvent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import org.apache.logging.log4j.core.Logger as CoreLogger

/**
 * connector 跳过与冲突禁用的日志断言（T14.5 停止条件 2/3/5；自 StackUpUpLateMixinLoaderSkipLogTest 迁移）。
 *
 * 断言真实 logger 输出：项目 classpath 的 log4j-core 是裁剪 jar（无 ListAppender，
 * `./gradlew dependencies --configuration testCompileClasspath` 与 javap 证据），因此测试自带最小
 * [CollectingAppender]（实现 core.Appender 接口）挂到目标 logger 上捕获消息。
 * - mod 缺失 / MixinToggles 关闭 / 未知配置 → 结构化日志 + 拒绝装载；
 * - required:false 配置（brandonscore）mod 缺失同样记录；
 * - 冲突禁用保留设计语义（拒绝 early 装载）但必须 ERROR 说明原因。
 *
 * 对全局 logger 的 level/appender 修改在 finally 中恢复；项目未开启 JUnit 并行，
 * 且该 logger 名仅本 connector 使用，不影响其它测试。
 */
class StackUpUpMixinConnectorSkipLogTest {

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
                StackUpUpMixinConnector().shouldQueue(StackUpUpIds.LATE_BRANDONSCORE_MIXIN_CONFIG) { false },
            )
        }
        assertTrue(
            messages.any { it.contains(StackUpUpIds.LATE_BRANDONSCORE_MIXIN_CONFIG) },
            "required:false 配置缺失同样必须记录，实际: $messages",
        )
    }

    @Test
    fun toggleOff_shouldLogStructuredSkipAndReturnFalse() {
        val original = MixinToggles.ic2
        try {
            MixinToggles.ic2 = false
            val messages = capturedLogs {
                assertFalse(
                    StackUpUpMixinConnector().shouldQueue(StackUpUpIds.LATE_IC2_MIXIN_CONFIG) { it == "ic2" },
                )
            }
            assertTrue(
                messages.any { it.contains(StackUpUpIds.LATE_IC2_MIXIN_CONFIG) && it.contains("MixinToggles.ic2") },
                "toggle 关闭必须点名开关，实际: $messages",
            )
        } finally {
            MixinToggles.ic2 = original
        }
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
    fun connectorAe2Probe_shouldHonorKnownForgeAbsenceWithoutFallback() {
        var cleanroomProbeCalled = false
        val present = StackUpUpMixinConnector().isModPresentForConnector(
            "ae2",
            forgeProbe = { false },
            cleanroomProbe = {
                cleanroomProbeCalled = true
                true
            },
        )
        assertFalse(present, "Forge 已明确报告 mod 缺失时不得被 fallback 覆盖")
        assertFalse(cleanroomProbeCalled, "Forge 已明确报告结果时不得调用 fallback")
    }

    @Test
    fun connectorSource_shouldUseSafeAe2ProbeAndKeepQueuePredicate() {
        val source = String(
            Files.readAllBytes(
                Paths.get(
                    "src",
                    "main",
                    "kotlin",
                    "io",
                    "alexjoest",
                    "stackupup",
                    "bootstrap",
                    "StackUpUpMixinConnector.kt",
                ),
            ),
            Charsets.UTF_8,
        )
        assertTrue(source.contains("isModPresentForConnector(modId)"), "生产 connector 必须使用安全 mod probe")
        assertFalse(source.contains("Loader.isModLoaded"), "connector 阶段不得直接调用 Loader.isModLoaded")
        assertTrue(source.contains("indexedModList"), "Forge probe 必须读取 connector 阶段安全的 indexed mod map")
        assertTrue(source.contains("cleanroomAe2Presence"), "Forge probe 不可用时必须保留 Cleanroom fallback")
        assertTrue(source.contains("shouldQueue(module.config, isModPresent)"), "必须保留 shouldQueue 注入式谓词")
        assertTrue(source.contains("ModDiscoverer.isModPresent"), "非 ae2 模块必须保留 ModDiscoverer probe")
        assertTrue(source.contains("Mixins.addConfiguration"), "生产路径必须保留条件配置入队")
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
        try {
            val conflicts = StackUpUpCore.ensureConflictState()
            val messages = capturedLogs {
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
