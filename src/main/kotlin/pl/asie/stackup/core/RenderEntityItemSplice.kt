package pl.asie.stackup.core

import net.minecraft.client.renderer.RenderItem
import net.minecraft.client.renderer.entity.RenderEntityItem
import net.minecraft.client.renderer.entity.RenderManager
import net.minecraft.item.ItemStack
import pl.asie.stackup.client.StackUpClientHelpers

class RenderEntityItemSplice(renderManagerIn: RenderManager, renderItem: RenderItem) :
    RenderEntityItem(renderManagerIn, renderItem) {
    override fun getModelCount(stack: ItemStack): Int = StackUpClientHelpers.getModelCount(stack)
}
