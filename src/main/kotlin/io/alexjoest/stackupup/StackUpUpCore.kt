package io.alexjoest.stackupup

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@IFMLLoadingPlugin.Name("StackUpUpCore")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
// 只排除 core 包，避免把 mixin 包一起挡在 LaunchClassLoader 的变换链外。
@IFMLLoadingPlugin.TransformerExclusions(StackUpUpIds.CORE_PACKAGE_NAME, StackUpUpIds.CONFIG_CLASS_NAME)
class StackUpUpCore : IFMLLoadingPlugin {
    companion object {
        private val logger: Logger = LogManager.getLogger("stackupup.coremod")
        private const val COREMOD_ACTIVE_PROPERTY: String = "${StackUpUpIds.MOD_ID}.coremod.active"
        private const val CONFLICT_DISABLED_PROPERTY: String = "${StackUpUpIds.MOD_ID}.conflict.disabled"
        private const val CONFLICT_MODS_PROPERTY: String = "${StackUpUpIds.MOD_ID}.conflict.mods"
        private val conflictingCoremodNames: Map<String, String> = mapOf(
            "StackUpCore" to "StackUp",
        )

        @JvmStatic
        fun isCoremodInjected(): Boolean = java.lang.Boolean.getBoolean(COREMOD_ACTIVE_PROPERTY)

        @JvmStatic
        fun isDisabledForConflict(): Boolean = java.lang.Boolean.getBoolean(CONFLICT_DISABLED_PROPERTY)

        @JvmStatic
        fun conflictingMods(): List<String> = System.getProperty(CONFLICT_MODS_PROPERTY)
            ?.split(';')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()

        private fun detectConflictingCoremods(): List<String> {
            val managerClass = try {
                Class.forName("net.minecraftforge.fml.relauncher.CoreModManager")
            } catch (e: ClassNotFoundException) {
                // 非 Forge 环境（如单元测试）没有 CoreModManager，冲突检测不可用；按无冲突处理并留痕，不算检测失败。
                logger.warn("CoreModManager not found - conflict detection unavailable (non-Forge environment), skipping conflict check", e)
                return emptyList()
            }
            return try {
                val loadPluginsField = managerClass.getDeclaredField("loadPlugins")
                loadPluginsField.isAccessible = true
                val wrappers = loadPluginsField.get(null) as? Iterable<*> ?: return emptyList()
                wrappers.mapNotNull { wrapper ->
                    val nameField = wrapper?.javaClass?.getDeclaredField("name") ?: return@mapNotNull null
                    nameField.isAccessible = true
                    val coremodName = nameField.get(wrapper) as? String ?: return@mapNotNull null
                    conflictingCoremodNames[coremodName]
                }.distinct()
            } catch (e: Throwable) {
                // T14.5：冲突检测失败不再静默按“无冲突”继续——冲突禁用是安全机制，检测坏了必须明确失败。
                logger.error("Conflict detection failed: {}", e.toString())
                throw IllegalStateException("StackUpUp conflict detection failed: ${e.message}", e)
            }
        }

        // internal：early mixin 装载入口（StackUpUpMixinConnector.connect）使用的冲突判定。
        internal fun ensureConflictState(): List<String> {
            if (isDisabledForConflict()) {
                return conflictingMods()
            }

            val detectedMods = detectConflictingCoremods()
            if (detectedMods.isEmpty()) {
                return emptyList()
            }

            System.setProperty(CONFLICT_DISABLED_PROPERTY, "true")
            System.setProperty(CONFLICT_MODS_PROPERTY, detectedMods.joinToString(";"))
            return detectedMods
        }
    }

    override fun getASMTransformerClass(): Array<String> = emptyArray()

    override fun getModContainerClass(): String? = null

    override fun getSetupClass(): String? = null

    override fun injectData(data: MutableMap<String, Any>) {
        // coremod 注入阶段不要主动触发业务配置类加载，避免引入业务初始化副作用。
        if (isDisabledForConflict()) {
            return
        }
        System.setProperty(COREMOD_ACTIVE_PROPERTY, "true")
    }

    override fun getAccessTransformerClass(): String? = null
}
