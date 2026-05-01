package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.alexjoest.stackupup.ClientSlotSyncHooks;
import java.util.List;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.network.play.server.SPacketWindowItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NetHandlerPlayClient.class)
abstract class NetHandlerPlayClientMixin {
    @WrapOperation(
        method = "handleSetSlot(Lnet/minecraft/network/play/server/SPacketSetSlot;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/inventory/Container;putStackInSlot(ILnet/minecraft/item/ItemStack;)V"
        )
    )
    private void stackupup$preserveLargeCountFromSetSlot(
        Container container,
        int slotId,
        ItemStack stack,
        Operation<Void> original,
        SPacketSetSlot packet
    ) {
        int transmittedCount = stack.getCount();
        original.call(container, slotId, stack);
        ClientSlotSyncHooks.restoreContainerSlotStackCount(container, slotId, stack, transmittedCount);
    }

    @WrapOperation(
        method = "handleWindowItems(Lnet/minecraft/network/play/server/SPacketWindowItems;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/inventory/Container;setAll(Ljava/util/List;)V"
        )
    )
    private void stackupup$preserveLargeCountFromWindowItems(
        Container container,
        List<ItemStack> stacks,
        Operation<Void> original,
        SPacketWindowItems packet
    ) {
        original.call(container, stacks);
        ClientSlotSyncHooks.restoreContainerSlotStackCounts(container, stacks);
    }
}
