package io.alexjoest.stackupup.config

import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.resources.I18n
import net.minecraftforge.common.config.ConfigElement
import net.minecraftforge.common.config.ConfigManager
import net.minecraftforge.fml.client.config.GuiConfig
import net.minecraftforge.fml.client.config.IConfigElement
import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.StackUpUp

class ConfigGui(parentScreen: GuiScreen?) : GuiConfig(
    parentScreen,
    collectConfigElements(),
    StackUpUp.MOD_ID,
    StackUpUp.CONFIG_ID,
    false,
    false,
    I18n.format("${StackUpUpIds.CONFIG_LANG_ROOT}.title")
) {
    companion object {
        private fun collectConfigElements(): List<IConfigElement> {
            val classes = ConfigManager.getModConfigClasses(StackUpUp.CONFIG_ID)
            return if (classes.size == 1) {
                ConfigElement.from(classes.single()).childElements
            } else {
                classes.map(ConfigElement::from)
            }
        }
    }
}


