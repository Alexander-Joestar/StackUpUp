package io.alexjoest.stackupup.core

import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import io.alexjoest.stackupup.StackLimitHooks

object InventoryHelperPerformanceSplice {
    @JvmStatic
    fun func_180173_a(world: World, x: Double, y: Double, z: Double, stack: ItemStack) {
        spawnItemStack(world, x, y, z, stack)
    }

    @JvmStatic
    fun spawnItemStack(world: World, x: Double, y: Double, z: Double, stack: ItemStack) {
        val xOffset = StackLimitHooks.RANDOM.nextFloat() * 0.8F + 0.1F
        val yOffset = StackLimitHooks.RANDOM.nextFloat() * 0.8F + 0.1F
        val zOffset = StackLimitHooks.RANDOM.nextFloat() * 0.8F + 0.1F

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
                stack.splitStack(StackLimitHooks.RANDOM.nextInt(maxStackSize - minStackSize + 1) + minStackSize)
            )
            entityitem.motionX = StackLimitHooks.RANDOM.nextGaussian() * 0.05
            entityitem.motionY = StackLimitHooks.RANDOM.nextGaussian() * 0.05 + 0.2
            entityitem.motionZ = StackLimitHooks.RANDOM.nextGaussian() * 0.05
            world.spawnEntity(entityitem)
        }
    }
}


