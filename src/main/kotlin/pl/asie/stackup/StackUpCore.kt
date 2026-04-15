package pl.asie.stackup

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin

@IFMLLoadingPlugin.Name("StackUpCore")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions("pl.asie.stackup")
class StackUpCore : IFMLLoadingPlugin {
    override fun getASMTransformerClass(): Array<String> = arrayOf("pl.asie.stackup.core.StackUpTransformer")

    override fun getModContainerClass(): String? = null

    override fun getSetupClass(): String? = null

    override fun injectData(data: MutableMap<String, Any>) {
        StackUpConfig.coremodActive = true
    }

    override fun getAccessTransformerClass(): String? = null
}
