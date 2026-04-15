package pl.asie.stackup.config

import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.resources.I18n
import net.minecraftforge.common.config.ConfigCategory
import net.minecraftforge.common.config.ConfigElement
import net.minecraftforge.common.config.Configuration
import net.minecraftforge.fml.client.config.DummyConfigElement
import net.minecraftforge.fml.client.config.GuiConfig
import net.minecraftforge.fml.client.config.IConfigElement
import pl.asie.stackup.StackUp

class ConfigGui(parentScreen: GuiScreen?) : GuiConfig(
    parentScreen,
    getConfigElements(),
    "stackup",
    "StackUp",
    false,
    false,
    I18n.format("config.stackup.title")
) {
    companion object {
        @JvmStatic
        fun generateList(category: ConfigCategory): List<IConfigElement> {
            val list = ArrayList<IConfigElement>()
            for (prop in category.values) {
                list.add(ConfigElement(prop))
            }
            return list
        }

        @JvmStatic
        fun generateList(config: Configuration): List<IConfigElement> {
            val list = ArrayList<IConfigElement>()
            for (name in config.categoryNames) {
                val category = config.getCategory(name)
                list.add(
                    DummyConfigElement.DummyCategoryElement(
                        category.name,
                        category.languagekey,
                        generateList(category)
                    )
                )
            }
            return list
        }

        private fun getConfigElements(): List<IConfigElement> = generateList(StackUp.getConfig())
    }
}
