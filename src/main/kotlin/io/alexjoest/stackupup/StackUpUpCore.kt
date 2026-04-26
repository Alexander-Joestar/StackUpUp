package io.alexjoest.stackupup

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin
import zone.rong.mixinbooter.IEarlyMixinLoader

@IFMLLoadingPlugin.Name("StackUpUpCore")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
// 只排除 core 包，避免把 mixin 包一起挡在 LaunchClassLoader 的变换链外。
@IFMLLoadingPlugin.TransformerExclusions(StackUpUpIds.CORE_PACKAGE_NAME, StackUpUpIds.CONFIG_CLASS_NAME)
class StackUpUpCore : IFMLLoadingPlugin, IEarlyMixinLoader {
    companion object {
        private const val COREMOD_ACTIVE_PROPERTY: String = "${StackUpUpIds.MOD_ID}.coremod.active"
        private const val CONFLICT_DISABLED_PROPERTY: String = "${StackUpUpIds.MOD_ID}.conflict.disabled"
        private const val CONFLICT_MODS_PROPERTY: String = "${StackUpUpIds.MOD_ID}.conflict.mods"
        private val conflictingCoremodNames: Map<String, String> = mapOf(
            "StackUpCore" to "StackUp"
        )

        @JvmStatic
        fun isCoremodInjected(): Boolean = java.lang.Boolean.getBoolean(COREMOD_ACTIVE_PROPERTY)

        @JvmStatic
        fun isDisabledForConflict(): Boolean = java.lang.Boolean.getBoolean(CONFLICT_DISABLED_PROPERTY)

        @JvmStatic
        fun conflictingMods(): List<String> =
            System.getProperty(CONFLICT_MODS_PROPERTY)
                ?.split(';')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                .orEmpty()

        private fun detectConflictingCoremods(): List<String> {
            return try {
                val managerClass = Class.forName("net.minecraftforge.fml.relauncher.CoreModManager")
                val loadPluginsField = managerClass.getDeclaredField("loadPlugins")
                loadPluginsField.isAccessible = true
                val wrappers = loadPluginsField.get(null) as? Iterable<*> ?: return emptyList()
                wrappers.mapNotNull { wrapper ->
                    val nameField = wrapper?.javaClass?.getDeclaredField("name") ?: return@mapNotNull null
                    nameField.isAccessible = true
                    val coremodName = nameField.get(wrapper) as? String ?: return@mapNotNull null
                    conflictingCoremodNames[coremodName]
                }.distinct()
            } catch (_: Throwable) {
                emptyList()
            }
        }

        private fun ensureConflictState(): List<String> {
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

    override fun getASMTransformerClass(): Array<String> =
        if (ensureConflictState().isEmpty()) {
            arrayOf(StackUpUpIds.DYNAMIC_COMPAT_TRANSFORMER_CLASS_NAME)
        } else {
            emptyArray()
        }

    override fun getModContainerClass(): String? = null

    override fun getSetupClass(): String? = null

    override fun injectData(data: MutableMap<String, Any>) {
        // coremod 注入阶段不要主动触发业务配置类加载，避免被自己的 transformer 反向卷入。
        if (isDisabledForConflict()) {
            return
        }
        System.setProperty(COREMOD_ACTIVE_PROPERTY, "true")
    }

    override fun getAccessTransformerClass(): String? = null

    override fun getMixinConfigs(): List<String> =
        if (ensureConflictState().isEmpty()) {
            listOf(StackUpUpIds.EARLY_MIXIN_CONFIG)
        } else {
            emptyList()
        }
}




