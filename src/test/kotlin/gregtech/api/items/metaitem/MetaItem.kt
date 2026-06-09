package gregtech.api.items.metaitem

import net.minecraft.item.Item
import net.minecraft.item.ItemStack

open class MetaItem : Item() {
    open fun getItem(stack: ItemStack): Any? = null
}
