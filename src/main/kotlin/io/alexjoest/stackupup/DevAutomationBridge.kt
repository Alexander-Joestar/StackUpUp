package io.alexjoest.stackupup

import net.minecraft.server.MinecraftServer
import net.minecraftforge.common.MinecraftForge
import java.io.File

internal object DevAutomationBridge {
    private const val CONFIG_CLASS_NAME = "io.alexjoest.stackupup.dev.DevAutomationConfig"
    private const val CLIENT_DRIVER_CLASS_NAME = "io.alexjoest.stackupup.dev.DevAutomationClientDriver"
    private const val SERVER_DRIVER_CLASS_NAME = "io.alexjoest.stackupup.dev.DevAutomationServerDriver"
    private const val CLIENT_ENABLED_GETTER = "getClientEnabled"
    private const val SERVER_ENABLED_GETTER = "getServerEnabled"
    private const val SERVER_RUN_METHOD = "run"

    /** 失败标记相对服务端运行目录（默认 `run/`）；Gradle Exec 任务按项目根的 `run/` 前缀检查。 */
    private const val FAILED_MARKER_DIRECTORY = "logs"
    private const val FAILED_MARKER_FILE_NAME = "autotest-failed.marker"

    fun registerClientAutomation(): Boolean = if (!isEnabled(CLIENT_ENABLED_GETTER)) {
        false
    } else {
        runCatching {
            val driver = Class.forName(CLIENT_DRIVER_CLASS_NAME).getDeclaredConstructor().newInstance()
            MinecraftForge.EVENT_BUS.register(driver)
            true
        }.getOrElse {
            StackUpUp.logger?.error("开发自动验收客户端桥接失败。", it)
            false
        }
    }

    fun runServerAutomation(server: MinecraftServer) {
        if (isEnabled(SERVER_ENABLED_GETTER)) {
            runCatching {
                val driverClass = Class.forName(SERVER_DRIVER_CLASS_NAME)
                driverClass
                    .getMethod(SERVER_RUN_METHOD, MinecraftServer::class.java)
                    .invoke(driverClass.getField("INSTANCE").get(null), server)
            }.onFailure { throwable ->
                // 驱动抛出的失败（failFast 语义）不能静默吞掉：
                // 先写失败标记供 Gradle 任务判定，再请求服务端停服，避免失败后无限挂起。
                StackUpUp.logger?.error("开发自动验收服务端桥接失败。", throwable)
                recordAutomationFailure(throwable, server)
            }
        }
    }

    private fun recordAutomationFailure(cause: Throwable, server: MinecraftServer) {
        // 反射 invoke 会把驱动抛出的异常包成 InvocationTargetException，marker 必须写真实失败摘要。
        val unwrapped = (cause as? java.lang.reflect.InvocationTargetException)?.targetException ?: cause
        val summary = unwrapped.message?.takeIf(String::isNotBlank) ?: unwrapped.javaClass.simpleName
        runCatching {
            val marker = File(FAILED_MARKER_DIRECTORY, FAILED_MARKER_FILE_NAME)
            marker.parentFile?.mkdirs()
            marker.writeText(summary, Charsets.UTF_8)
            StackUpUp.logger?.error("开发自动验收服务端桥接失败：已写失败标记 {}", marker.absolutePath)
        }.onFailure { writeFailure ->
            StackUpUp.logger?.error("开发自动验收服务端桥接失败：写入失败标记失败。", writeFailure)
        }
        runCatching {
            server.initiateShutdown()
        }.onFailure { shutdownFailure ->
            StackUpUp.logger?.error("开发自动验收服务端桥接失败：请求服务端停服失败。", shutdownFailure)
        }
    }

    private fun isEnabled(getterName: String): Boolean = runCatching {
        val configClass = Class.forName(CONFIG_CLASS_NAME)
        configClass.getMethod(getterName).invoke(configClass.getField("INSTANCE").get(null)) as Boolean
    }.getOrDefault(false)
}
