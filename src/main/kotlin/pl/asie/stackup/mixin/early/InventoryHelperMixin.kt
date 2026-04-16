package pl.asie.stackup.mixin.early

import net.minecraft.inventory.InventoryHelper
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import pl.asie.stackup.core.InventoryHelperPerformanceSplice

@Mixin(InventoryHelper::class)
abstract class InventoryHelperMixin {
    companion object {
        @Inject(method = ["spawnItemStack", "func_180173_a"], at = [At("HEAD")], cancellable = true)
        @JvmStatic
        private fun useLargeStackSplit(world: World, x: Double, y: Double, z: Double, stack: ItemStack, ci: CallbackInfo) {
            InventoryHelperPerformanceSplice.spawnItemStack(world, x, y, z, stack)
            ci.cancel()
        }
    }
}
