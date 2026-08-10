package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.StackUpUpIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URLClassLoader

private const val DEVAUTOMATION_CONFIG_CLASS_NAME = "io.alexjoest.stackupup.dev.DevAutomationConfig"

private data class DevAutomationFlags(val clientEnabled: Boolean, val serverEnabled: Boolean)

/**
 * 用隔离类加载器读取一次真实的 `readSettings(System::getProperty)` 结果。
 *
 * `DevAutomationConfig` 是 Kotlin `object`：`clientEnabled`/`serverEnabled` 在类初始化时按当时的系统属性
 * 一次性求值，同一类加载器内不可重算。要覆盖两种 mode 就必须各做一次全新初始化，故对
 * `io.alexjoest.stackupup.dev.*` 走 child-first 加载（其余类仍由父加载器提供），属性在 finally 清理。
 */
private fun readAutomationFlags(enabled: String, mode: String): DevAutomationFlags {
    val prefix = StackUpUpIds.DEV_AUTOMATION_PREFIX
    System.setProperty("$prefix.enabled", enabled)
    System.setProperty("$prefix.mode", mode)
    try {
        val urls =
            System.getProperty("java.class.path").orEmpty()
                .split(File.pathSeparator)
                .filter(String::isNotBlank)
                .map { File(it).toURI().toURL() }
                .toTypedArray()
        val loader =
            object : URLClassLoader(urls, DevAutomationPropertyReaderTest::class.java.classLoader) {
                override fun loadClass(name: String, resolve: Boolean): Class<*> {
                    if (!name.startsWith("io.alexjoest.stackupup.dev.")) {
                        return super.loadClass(name, resolve)
                    }
                    synchronized(getClassLoadingLock(name)) {
                        findLoadedClass(name)?.let { return it }
                        val loaded = findClass(name)
                        if (resolve) {
                            resolveClass(loaded)
                        }
                        return loaded
                    }
                }
            }
        loader.use {
            val configClass = Class.forName(DEVAUTOMATION_CONFIG_CLASS_NAME, true, it)
            val instance = configClass.getField("INSTANCE").get(null)
            return DevAutomationFlags(
                clientEnabled = configClass.getMethod("getClientEnabled").invoke(instance) as Boolean,
                serverEnabled = configClass.getMethod("getServerEnabled").invoke(instance) as Boolean,
            )
        }
    } finally {
        System.clearProperty("$prefix.enabled")
        System.clearProperty("$prefix.mode")
    }
}

class DevAutomationPropertyReaderTest {
    @Test
    fun newPrefix_shouldOverrideLegacy() {
        val values =
            mapOf(
                "stackup.dev.autoTest" to "false",
                "stackup.dev.autoTest.mode" to "client",
                "${StackUpUpIds.DEV_AUTOMATION_PREFIX}.enabled" to "true",
                "${StackUpUpIds.DEV_AUTOMATION_PREFIX}.mode" to "server",
                "${StackUpUpIds.DEV_AUTOMATION_PREFIX}.worldFolder" to "stackupup_dev_autotest_new",
            )

        val settings = readSettings(values::get)

        assertTrue(settings.enabled)
        assertEquals("server", settings.mode)
        assertEquals("stackupup_dev_autotest_new", settings.worldFolder)
    }

    @Test
    fun noNewPrefix_shouldFallbackToLegacy() {
        val values =
            mapOf(
                "stackup.dev.autoTest" to "true",
                "stackup.dev.autoTest.mode" to "both",
                "stackup.dev.autoTest.item" to "gregtech:meta_item_1",
                "stackup.dev.autoTest.meta" to "516",
            )

        val settings = readSettings(values::get)

        assertTrue(settings.enabled)
        assertEquals("both", settings.mode)
        assertEquals("gregtech:meta_item_1", settings.itemId)
        assertEquals(516, settings.itemMeta)
    }

    @Test
    fun `未关闭自动化的非法 mode 不抛异常且两侧都不启用`() {
        val flags = readAutomationFlags(enabled = "true", mode = "clinet")

        assertFalse(flags.clientEnabled, "非法 mode 不得让客户端自动化启用")
        assertFalse(flags.serverEnabled, "非法 mode 不得让服务端自动化启用")
    }

    @Test
    fun `mode 为 server 时只启用服务端自动化`() {
        val flags = readAutomationFlags(enabled = "true", mode = "server")

        assertFalse(flags.clientEnabled)
        assertTrue(flags.serverEnabled)
    }
}
