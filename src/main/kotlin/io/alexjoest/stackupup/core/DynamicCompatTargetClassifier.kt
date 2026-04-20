package io.alexjoest.stackupup.core

internal object DynamicCompatTargetProfile {
    const val NONE = 0
    const val INVENTORY = 1
    const val ITEM_HANDLER = 2
    const val SLOT = 4

    private const val INVENTORY_TARGET: String = "net.minecraft.inventory.IInventory"
    private const val ITEM_HANDLER_TARGET: String = "net.minecraftforge.items.IItemHandler"
    private const val SLOT_TARGET: String = "net.minecraft.inventory.Slot"

    private val inventoryMethods = arrayOf("getInventoryStackLimit", "func_70297_j_")
    private val itemHandlerMethods = arrayOf("getSlotLimit")
    private val slotMethods = arrayOf("getItemStackLimit", "func_178170_b", "getSlotStackLimit", "func_75219_a")

    fun methodsFor(profile: Int): Array<String>? {
        if (profile == INVENTORY) {
            return inventoryMethods
        }
        if (profile == ITEM_HANDLER) {
            return itemHandlerMethods
        }
        if (profile == SLOT) {
            return slotMethods
        }
        return null
    }

    fun inventoryTarget(): String = INVENTORY_TARGET

    fun itemHandlerTarget(): String = ITEM_HANDLER_TARGET

    fun slotTarget(): String = SLOT_TARGET

    fun includes(mask: Int, profile: Int): Boolean = mask and profile != 0
}

internal object DynamicCompatTargetClassifier {
    fun classify(className: String): Int {
        return classify(className, DynamicCompatTargetProfile.INVENTORY or DynamicCompatTargetProfile.ITEM_HANDLER or DynamicCompatTargetProfile.SLOT)
    }

    fun classify(className: String, declaredProfiles: Int): Int {
        if (FixedCompatTargets.contains(className)) {
            return DynamicCompatTargetProfile.NONE
        }

        if (
            DynamicCompatTargetProfile.includes(declaredProfiles, DynamicCompatTargetProfile.SLOT)
            && TypeRelationshipResolver.extends(className, DynamicCompatTargetProfile.slotTarget())
        ) {
            return DynamicCompatTargetProfile.SLOT
        }

        if (
            DynamicCompatTargetProfile.includes(declaredProfiles, DynamicCompatTargetProfile.ITEM_HANDLER)
            && TypeRelationshipResolver.implements(className, DynamicCompatTargetProfile.itemHandlerTarget())
        ) {
            return DynamicCompatTargetProfile.ITEM_HANDLER
        }

        if (
            DynamicCompatTargetProfile.includes(declaredProfiles, DynamicCompatTargetProfile.INVENTORY)
            && TypeRelationshipResolver.implements(className, DynamicCompatTargetProfile.inventoryTarget())
        ) {
            return DynamicCompatTargetProfile.INVENTORY
        }

        return DynamicCompatTargetProfile.NONE
    }
}
