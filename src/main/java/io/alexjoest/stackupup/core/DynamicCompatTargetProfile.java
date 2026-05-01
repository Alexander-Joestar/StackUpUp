package io.alexjoest.stackupup.core;

final class DynamicCompatTargetProfile {

    private DynamicCompatTargetProfile() {}

    static final int NONE = 0;
    static final int INVENTORY = 1;
    static final int ITEM_HANDLER = 2;
    static final int SLOT = 4;

    private static final String INVENTORY_TARGET = "net.minecraft.inventory.IInventory";
    private static final String ITEM_HANDLER_TARGET = "net.minecraftforge.items.IItemHandler";
    private static final String SLOT_TARGET = "net.minecraft.inventory.Slot";

    private static final String[] INVENTORY_METHODS = {"getInventoryStackLimit", "func_70297_j_"};
    private static final String[] ITEM_HANDLER_METHODS = {"getSlotLimit"};
    private static final String[] SLOT_METHODS = {"getItemStackLimit", "func_178170_b", "getSlotStackLimit", "func_75219_a"};

    static String[] methodsFor(int profile) {
        if (profile == INVENTORY) {
            return INVENTORY_METHODS;
        }
        if (profile == ITEM_HANDLER) {
            return ITEM_HANDLER_METHODS;
        }
        if (profile == SLOT) {
            return SLOT_METHODS;
        }
        return null;
    }

    static String inventoryTarget() {
        return INVENTORY_TARGET;
    }

    static String itemHandlerTarget() {
        return ITEM_HANDLER_TARGET;
    }

    static String slotTarget() {
        return SLOT_TARGET;
    }

    static boolean includes(int mask, int profile) {
        return (mask & profile) != 0;
    }
}

final class DynamicCompatTargetClassifier {

    private DynamicCompatTargetClassifier() {}

    static int classify(String className) {
        return classify(className, DynamicCompatTargetProfile.INVENTORY
            | DynamicCompatTargetProfile.ITEM_HANDLER
            | DynamicCompatTargetProfile.SLOT);
    }

    static int classify(String className, int declaredProfiles) {
        if (FixedCompatTargets.contains(className)) {
            return DynamicCompatTargetProfile.NONE;
        }

        if (DynamicCompatTargetProfile.includes(declaredProfiles, DynamicCompatTargetProfile.SLOT)
            && TypeRelationshipResolver.extendsClass(className, DynamicCompatTargetProfile.slotTarget())) {
            return DynamicCompatTargetProfile.SLOT;
        }

        if (DynamicCompatTargetProfile.includes(declaredProfiles, DynamicCompatTargetProfile.ITEM_HANDLER)
            && TypeRelationshipResolver.implementsInterface(className, DynamicCompatTargetProfile.itemHandlerTarget())) {
            return DynamicCompatTargetProfile.ITEM_HANDLER;
        }

        if (DynamicCompatTargetProfile.includes(declaredProfiles, DynamicCompatTargetProfile.INVENTORY)
            && TypeRelationshipResolver.implementsInterface(className, DynamicCompatTargetProfile.inventoryTarget())) {
            return DynamicCompatTargetProfile.INVENTORY;
        }

        return DynamicCompatTargetProfile.NONE;
    }
}
