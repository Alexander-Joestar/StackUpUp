package io.alexjoest.stackupup.config

import io.alexjoest.stackupup.StackUpUpConfig
import net.minecraftforge.fml.common.Loader
import java.io.File

object EarlyCompatConfigReader {
    private const val COMPAT_CATEGORY = "compatibility"

    @Volatile
    private var fileCached: Map<String, Boolean>? = null

    @Volatile
    private var configReady: Boolean = false

    @JvmStatic
    fun isModuleEnabled(modName: String): Boolean {
        // After @Config has been populated, use the config object directly
        if (configReady) {
            return readFromConfigObject(modName)
        }
        // Early access: use file-based cache
        val snapshot = fileCached ?: synchronized(this) {
            fileCached ?: loadConfigFromFile().also { fileCached = it }
        }
        return snapshot[modName] ?: true
    }

    /** Call after @Config has been populated (e.g. in preInit) to switch to object-based reads. */
    @JvmStatic
    fun markConfigReady() {
        configReady = true
        fileCached = null // release file cache
    }

    /** Re-read from config object after runtime changes. */
    @JvmStatic
    fun refresh() {
        // If config is ready, just the flag is enough; next isModuleEnabled reads the live object.
        if (configReady) return
        // Otherwise invalidate file cache so next call re-reads
        synchronized(this) { fileCached = null }
    }

    private fun readFromConfigObject(modName: String): Boolean = when (modName) {
        "ae2" -> StackUpUpConfig.compatibility.ae2
        "brandonsCore" -> StackUpUpConfig.compatibility.brandonsCore
        "actuallyAdditions" -> StackUpUpConfig.compatibility.actuallyAdditions
        "cyclopsCore" -> StackUpUpConfig.compatibility.cyclopsCore
        "enderIo" -> StackUpUpConfig.compatibility.enderIo
        "ic2" -> StackUpUpConfig.compatibility.ic2
        "mantle" -> StackUpUpConfig.compatibility.mantle
        "refinedStorage" -> StackUpUpConfig.compatibility.refinedStorage
        "storageNetwork" -> StackUpUpConfig.compatibility.storageNetwork
        else -> true
    }

    private fun loadConfigFromFile(): Map<String, Boolean> {
        val configFile = locateConfigFile() ?: return emptyMap()
        if (!configFile.exists()) return emptyMap()
        return parseConfigText(configFile.readText(Charsets.UTF_8))
    }

    internal fun parseConfigText(source: String): Map<String, Boolean> =
        RawConfigFileScanner.readBooleanCategory(source, COMPAT_CATEGORY)

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
