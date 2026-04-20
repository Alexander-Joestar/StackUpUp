package io.alexjoest.stackupup.config

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import net.minecraftforge.fml.client.IModGuiFactory

class ConfigGuiFactory : IModGuiFactory {
    override fun initialize(minecraftInstance: Minecraft?) {
    }

    override fun hasConfigGui(): Boolean = true

    override fun createConfigGui(parentScreen: GuiScreen?): GuiScreen = ConfigGui(parentScreen)

    override fun runtimeGuiCategories(): MutableSet<IModGuiFactory.RuntimeOptionCategoryElement>? = null
}

