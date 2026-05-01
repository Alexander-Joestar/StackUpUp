package io.alexjoest.stackupup.config

import io.alexjoest.stackupup.StackUpUp
import io.alexjoest.stackupup.StackUpUpIds
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.resources.I18n
import net.minecraftforge.common.config.ConfigElement
import net.minecraftforge.common.config.ConfigManager
import net.minecraftforge.fml.client.config.GuiConfig
import net.minecraftforge.fml.client.config.IConfigElement

class ConfigGui(parentScreen: GuiScreen?) :
    GuiConfig(
        parentScreen,
        collectConfigElements(),
        StackUpUp.MOD_ID,
        StackUpUp.CONFIG_ID,
        false,
        false,
        I18n.format(StackUpUpIds.CONFIG_TITLE_KEY),
    ) {
    companion object {
        private fun collectConfigElements(): List<IConfigElement> = ConfigManager.getModConfigClasses(StackUpUp.CONFIG_ID).let { classes ->
            if (classes.size == 1) {
                ConfigElement.from(classes.single()).childElements
            } else {
                classes.map(ConfigElement::from)
            }
        }
    }
}
