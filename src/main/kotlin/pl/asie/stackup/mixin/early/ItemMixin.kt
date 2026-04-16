package pl.asie.stackup.mixin.early

import net.minecraft.item.Item
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import pl.asie.stackup.limit.StackIdentity
import pl.asie.stackup.limit.StackUpServices

@Mixin(Item::class)
abstract class ItemMixin {
    @Inject(
        method = ["getItemStackLimit(Lnet/minecraft/item/ItemStack;)I", "func_77639_j(Lnet/minecraft/item/ItemStack;)I"],
        at = [At("RETURN")],
        cancellable = true
    )
    private fun applyRules(stack: ItemStack, cir: CallbackInfoReturnable<Int>) {
        val registryName = stack.item.registryName ?: return
        val type = if (stack.item is ItemBlock) "block" else "item"
        cir.returnValue = StackUpServices.limitService().resolve(
            StackIdentity(
                itemId = registryName.toString(),
                modId = registryName.namespace,
                meta = stack.metadata,
                type = type
            ),
            cir.returnValue,
            StackUpServices.oreDictIndex().getOreNames(registryName.toString(), stack.metadata)
        )
    }
}
