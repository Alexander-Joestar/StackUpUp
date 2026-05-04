package io.alexjoest.stackupup;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.item.ItemStack;

public final class ContainerMergeShrink {
    public final ItemStack cursorStack;
    public final int quantity;
    public final Operation<Void> originalShrink;

    public ContainerMergeShrink(ItemStack cursorStack, int quantity, Operation<Void> originalShrink) {
        this.cursorStack = cursorStack;
        this.quantity = quantity;
        this.originalShrink = originalShrink;
    }
}
