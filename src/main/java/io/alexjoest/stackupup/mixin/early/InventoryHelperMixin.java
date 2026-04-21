package io.alexjoest.stackupup.mixin.early;

import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryHelper.class)
public abstract class InventoryHelperMixin {
    private static final int MIN_SPLIT_THRESHOLD = 8;

    @Inject(
        method = "spawnItemStack(Lnet/minecraft/world/World;DDDLnet/minecraft/item/ItemStack;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void useLargeStackSplit(World worldIn, double x, double y, double z, ItemStack stack, CallbackInfo ci) {
        spawnLargeStack(worldIn, x, y, z, stack);
        ci.cancel();
    }

    private static void spawnLargeStack(World world, double x, double y, double z, ItemStack stack) {
        float xOffset = StackLimitHooks.RANDOM.nextFloat() * 0.8F + 0.1F;
        float yOffset = StackLimitHooks.RANDOM.nextFloat() * 0.8F + 0.1F;
        float zOffset = StackLimitHooks.RANDOM.nextFloat() * 0.8F + 0.1F;

        int minStackSize = (stack.getMaxStackSize() + 7) / 8;
        int maxStackSize = (stack.getMaxStackSize() + 1) / 2;
        if (stack.getCount() <= MIN_SPLIT_THRESHOLD) {
            minStackSize = stack.getCount();
            maxStackSize = stack.getCount();
        }

        while (!stack.isEmpty()) {
            EntityItem entityItem = new EntityItem(
                world,
                x + xOffset,
                y + yOffset,
                z + zOffset,
                stack.splitStack(StackLimitHooks.RANDOM.nextInt(maxStackSize - minStackSize + 1) + minStackSize)
            );
            entityItem.motionX = StackLimitHooks.RANDOM.nextGaussian() * 0.05D;
            entityItem.motionY = StackLimitHooks.RANDOM.nextGaussian() * 0.05D + 0.2D;
            entityItem.motionZ = StackLimitHooks.RANDOM.nextGaussian() * 0.05D;
            world.spawnEntity(entityItem);
        }
    }
}
