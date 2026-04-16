package pl.asie.stackup

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin
import zone.rong.mixinbooter.IEarlyMixinLoader

@IFMLLoadingPlugin.Name("StackUpCore")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
// 只排除 core 包，避免把 mixin 包一起挡在 LaunchClassLoader 的变换链外。
@IFMLLoadingPlugin.TransformerExclusions("pl.asie.stackup.core", "pl.asie.stackup.StackUpConfig")
class StackUpCore : IFMLLoadingPlugin, IEarlyMixinLoader {
    companion object {
        private const val COREMOD_ACTIVE_PROPERTY: String = "stackup.coremod.active"

        @JvmStatic
        fun isCoremodInjected(): Boolean = java.lang.Boolean.getBoolean(COREMOD_ACTIVE_PROPERTY)
    }

    override fun getASMTransformerClass(): Array<String> = arrayOf("pl.asie.stackup.core.StackUpTransformer")

    override fun getModContainerClass(): String? = null

    override fun getSetupClass(): String? = null

    override fun injectData(data: MutableMap<String, Any>) {
        // coremod 注入阶段不要主动触发业务配置类加载，避免被自己的 transformer 反向卷入。
        System.setProperty(COREMOD_ACTIVE_PROPERTY, "true")
    }

    override fun getAccessTransformerClass(): String? = null

    override fun getMixinConfigs(): List<String> = listOf("mixins.stackup.early.json")
}
