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
import zone.rong.mixinbooter.Context
import org.apache.logging.log4j.core.Logger as CoreLogger

/**
 * late/early loader 跳过与冲突禁用的日志断言（T14.5 停止条件 2/3/5）。
 *
 * 断言真实 logger 输出：项目 classpath 的 log4j-core 是裁剪 jar（无 ListAppender，
 * `./gradlew dependencies --configuration testCompileClasspath` 与 javap 证据），因此测试自带最小
 * [CollectingAppender]（实现 core.Appender 接口）挂到目标 logger 上捕获消息。
 * - mod 缺失 / MixinToggles 关闭 / 未知配置 → 结构化日志 + 返回 false（原零日志跳过/无条件入队）；
 * - required:false 配置（brandonscore）mod 缺失同样记录；
 * - 冲突禁用保留设计语义（返回空表）但必须 ERROR 说明原因。
 *
 * 对全局 logger 的 level/appender 修改在 finally 中恢复；项目未开启 JUnit 并行，
 * 且该 logger 名仅本 loader 使用，不影响其它测试。
 */
class StackUpUpLateMixinLoaderSkipLogTest {

    private fun capturedLogs(loggerName: String = "stackupup.mixin.late", block: () -> Unit): List<String> {
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
    fun `modAbsent_shouldLogStructuredSkipAndReturnFalse`() {
        val messages = capturedLogs {
            assertFalse(
                StackUpUpLateMixinLoader().shouldMixinConfigQueue(
                    Context(StackUpUpIds.LATE_AE2_MIXIN_CONFIG, emptyList()),
                ),
            )
        }
        assertTrue(
            messages.any { it.contains(StackUpUpIds.LATE_AE2_MIXIN_CONFIG) && it.contains("appliedenergistics2") && it.contains("not present") },
            "mod 缺失必须留结构化日志，实际: $messages",
        )
    }

    @Test
    fun `requiredFalseConfigModAbsent_shouldAlsoLogSkip`() {
        val messages = capturedLogs {
            assertFalse(
                StackUpUpLateMixinLoader().shouldMixinConfigQueue(
                    Context(StackUpUpIds.LATE_BRANDONSCORE_MIXIN_CONFIG, emptyList()),
                ),
            )
        }
        assertTrue(
            messages.any { it.contains(StackUpUpIds.LATE_BRANDONSCORE_MIXIN_CONFIG) },
            "required:false 配置缺失同样必须记录，实际: $messages",
        )
    }

    @Test
    fun `toggleOff_shouldLogStructuredSkipAndReturnFalse`() {
        val original = MixinToggles.ic2
        try {
            MixinToggles.ic2 = false
            val messages = capturedLogs {
                assertFalse(
                    StackUpUpLateMixinLoader().shouldMixinConfigQueue(
                        Context(StackUpUpIds.LATE_IC2_MIXIN_CONFIG, listOf("ic2")),
                    ),
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
    fun `unknownConfig_shouldLogErrorAndReturnFalseInsteadOfUnconditionalQueue`() {
        val messages = capturedLogs {
            assertFalse(
                StackUpUpLateMixinLoader().shouldMixinConfigQueue(
                    Context("mixins.stackupup.late.ghost.json", listOf("ghostmod")),
                ),
            )
        }
        assertTrue(
            messages.any { it.contains("mixins.stackupup.late.ghost.json") && it.contains("not registered") },
            "未知配置不得无条件入队且必须留 ERROR 日志，实际: $messages",
        )
    }

    @Test
    fun `conflictDisabled_shouldKeepDesignSemanticsAndLogErrorExplainingWhy`() {
        val disabledProperty = "${StackUpUpIds.MOD_ID}.conflict.disabled"
        val modsProperty = "${StackUpUpIds.MOD_ID}.conflict.mods"
        System.setProperty(disabledProperty, "true")
        System.setProperty(modsProperty, "StackUp")
        try {
            val messages = capturedLogs("stackupup.coremod") {
                assertTrue(StackUpUpCore().getMixinConfigs().isEmpty(), "冲突禁用必须保留空表设计语义")
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
