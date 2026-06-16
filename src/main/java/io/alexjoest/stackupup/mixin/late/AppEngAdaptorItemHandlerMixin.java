package io.alexjoest.stackupup.mixin.late;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.alexjoest.stackupup.core.Ae2ItemHandlerInsertLimiter;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "appeng.util.inv.AdaptorItemHandler", remap = false)
abstract class AppEngAdaptorItemHandlerMixin {
    @WrapOperation(
        method = "addItems",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/items/IItemHandler;insertItem(ILnet/minecraft/item/ItemStack;Z)Lnet/minecraft/item/ItemStack;"
        ),
        require = 0
    )
    private ItemStack stackupup$capUnknownHandlerInsert(
        IItemHandler handler,
        int slot,
        ItemStack stack,
        boolean simulate,
        Operation<ItemStack> original
    ) {
        return Ae2ItemHandlerInsertLimiter.insertCapped(handler, slot, stack, simulate);
    }
}
