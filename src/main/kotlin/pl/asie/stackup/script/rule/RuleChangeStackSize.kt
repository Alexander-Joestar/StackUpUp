package pl.asie.stackup.script.rule

import net.minecraft.item.Item
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.registry.ForgeRegistries
import pl.asie.stackup.StackUpHelpers

class RuleChangeStackSize() : Rule() {
    private var item: Item? = null
    private var newStackSize: Int = 0
    private var oldValue: Int = 0

    constructor(item: Item, newStackSize: Int) : this() {
        this.item = item
        this.newStackSize = newStackSize
    }

    override fun applyInternal(): Boolean {
        if (newStackSize < 0 || newStackSize > StackUpHelpers.getMaxStackSize()) {
            return false
        }

        val i = item ?: return false
        oldValue = i.itemStackLimit
        i.setMaxStackSize(newStackSize)
        return !(newStackSize != oldValue && i.itemStackLimit == oldValue)
    }

    override fun undoInternal(): Boolean {
        val i = item ?: return false
        val oldValueCmp = i.itemStackLimit
        i.setMaxStackSize(oldValue)
        return !(oldValueCmp != oldValue && i.itemStackLimit == oldValueCmp)
    }

    override fun serializeNBT(): NBTTagCompound {
        val compound = NBTTagCompound()
        compound.setString("id", item!!.registryName.toString())
        compound.setInteger("size", newStackSize)
        return compound
    }

    override fun deserializeNBT(nbt: NBTTagCompound) {
        item = ForgeRegistries.ITEMS.getValue(ResourceLocation(nbt.getString("id")))
        newStackSize = nbt.getInteger("size")
    }
}
