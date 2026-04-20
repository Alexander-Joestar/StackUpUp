package io.alexjoest.stackupup

import gnu.trove.map.TObjectIntMap
import gnu.trove.map.hash.TObjectIntHashMap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack

internal object StackSizeBackupRegistry {
    private val values: TObjectIntMap<Item> = TObjectIntHashMap()

    fun backup(item: Item) {
        if (!values.containsKey(item)) {
            values.put(item, item.getItemStackLimit(ItemStack(item)))
        }
    }

    fun restoreAll() {
        for (item in values.keySet()) {
            item.setMaxStackSize(values.get(item))
        }
        values.clear()
    }
}
