package io.alexjoest.stackupup.config

import net.minecraftforge.fml.common.Loader
import java.io.File

/**
 * 在 @Config 注解尚未生效的阶段，直接读取 config/stackupup.cfg 中
 * compatibility 段的开关，提供给 MixinBooter 的 late-mixin 门控。
 *
 * 因为 MixinBooter 调用 ILateMixinLoader 的时机早于 Forge 填充
 * @Config 对象（FMLPreInitializationEvent），所以不能依赖
 * StackUpUpConfig.compatibility.*——只能直接读原始配置文件。
 */
object EarlyCompatConfigReader {
    private const val COMPAT_CATEGORY = "compatibility"

    @Volatile
    private var cached: Map<String, Boolean>? = null

    /**
     * 读取指定 mod 的兼容性开关。首次调用加载并缓存整个 compatibility 段。
     */
    @JvmStatic
    fun isModuleEnabled(modName: String): Boolean {
        val snapshot = cached ?: synchronized(this) {
            cached ?: loadConfig().also { cached = it }
        }
        return snapshot[modName] ?: true
    }

    private fun loadConfig(): Map<String, Boolean> {
        val configFile = locateConfigFile() ?: return emptyMap()
        if (!configFile.exists()) return emptyMap()

        return parseConfigText(configFile.readText(Charsets.UTF_8))
    }

    internal fun parseConfigText(source: String): Map<String, Boolean> = RawConfigFileScanner.readBooleanCategory(source, COMPAT_CATEGORY)

    private fun locateConfigFile(): File? {
        val fmlConfigDir = Loader.instance().configDir
        if (fmlConfigDir != null) {
            val candidate = File(fmlConfigDir, "stackupup.cfg")
            if (candidate.exists()) return candidate
        }
        val devFallback = File("config", "stackupup.cfg")
        return if (devFallback.exists()) devFallback else null
    }
}
