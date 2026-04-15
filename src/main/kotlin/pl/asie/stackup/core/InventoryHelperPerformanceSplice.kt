package pl.asie.stackup.core

import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import pl.asie.stackup.StackUpHelpers

object InventoryHelperPerformanceSplice {
    @JvmStatic
    fun func_180173_a(world: World, x: Double, y: Double, z: Double, stack: ItemStack) {
        spawnItemStack(world, x, y, z, stack)
    }

    @JvmStatic
    fun spawnItemStack(world: World, x: Double, y: Double, z: Double, stack: ItemStack) {
        val xOffset = StackUpHelpers.RANDOM.nextFloat() * 0.8F + 0.1F
        val yOffset = StackUpHelpers.RANDOM.nextFloat() * 0.8F + 0.1F
        val zOffset = StackUpHelpers.RANDOM.nextFloat() * 0.8F + 0.1F

        var minStackSize = (stack.maxStackSize + 7) / 8
        var maxStackSize = (stack.maxStackSize + 1) / 2

        if (stack.count <= 8) {
            minStackSize = stack.count
            maxStackSize = stack.count
        }

        while (!stack.isEmpty) {
            val entityitem = EntityItem(
                world,
                x + xOffset, y + yOffset, z + zOffset,
                stack.splitStack(StackUpHelpers.RANDOM.nextInt(maxStackSize - minStackSize + 1) + minStackSize)
            )
            entityitem.motionX = StackUpHelpers.RANDOM.nextGaussian() * 0.05
            entityitem.motionY = StackUpHelpers.RANDOM.nextGaussian() * 0.05 + 0.2
            entityitem.motionZ = StackUpHelpers.RANDOM.nextGaussian() * 0.05
            world.spawnEntity(entityitem)
        }
    }
}
