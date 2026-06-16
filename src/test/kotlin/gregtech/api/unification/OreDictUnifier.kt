package gregtech.api.unification

import net.minecraft.item.ItemStack

object OreDictUnifier {
    var materialStack: Any? = null

    @JvmStatic
    fun getMaterial(stack: ItemStack): Any? = materialStack
}
