package io.alexjoest.stackupup

import net.minecraft.server.MinecraftServer
import net.minecraftforge.common.MinecraftForge

internal object DevAutomationBridge {
    private const val CONFIG_CLASS_NAME = "io.alexjoest.stackupup.dev.DevAutomationConfig"
    private const val CLIENT_DRIVER_CLASS_NAME = "io.alexjoest.stackupup.dev.DevAutomationClientDriver"
    private const val SERVER_DRIVER_CLASS_NAME = "io.alexjoest.stackupup.dev.DevAutomationServerDriver"
    private const val CLIENT_ENABLED_GETTER = "getClientEnabled"
    private const val SERVER_ENABLED_GETTER = "getServerEnabled"
    private const val SERVER_RUN_METHOD = "run"

    fun registerClientAutomation(): Boolean {
        if (!isEnabled(CLIENT_ENABLED_GETTER)) {
            return false
        }

        return runCatching {
            val driverClass = Class.forName(CLIENT_DRIVER_CLASS_NAME)
            val driver = driverClass.getDeclaredConstructor().newInstance()
            MinecraftForge.EVENT_BUS.register(driver)
            true
        }.getOrElse {
            StackUpUp.logger?.error("开发自动验收客户端桥接失败。", it)
            false
        }
    }

    fun runServerAutomation(server: MinecraftServer) {
        if (!isEnabled(SERVER_ENABLED_GETTER)) {
            return
        }

        runCatching {
            val driverClass = Class.forName(SERVER_DRIVER_CLASS_NAME)
            val instance = driverClass.getField("INSTANCE").get(null)
            val runMethod = driverClass.getMethod(SERVER_RUN_METHOD, MinecraftServer::class.java)
            runMethod.invoke(instance, server)
        }.onFailure {
            StackUpUp.logger?.error("开发自动验收服务端桥接失败。", it)
        }
    }

    private fun isEnabled(getterName: String): Boolean {
        return runCatching {
            val configClass = Class.forName(CONFIG_CLASS_NAME)
            val instance = configClass.getField("INSTANCE").get(null)
            configClass.getMethod(getterName).invoke(instance) as Boolean
        }.getOrDefault(false)
    }
}
