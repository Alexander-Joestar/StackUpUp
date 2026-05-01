package io.alexjoest.stackupup

import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.Container
import net.minecraft.inventory.Slot

/**
 * 测试用容器，公开 addSlotToContainer 以便在测试中直接添加槽位。
 */
open class TestContainer : Container() {
    override fun canInteractWith(player: EntityPlayer): Boolean = true
    public override fun addSlotToContainer(slot: Slot): Slot = super.addSlotToContainer(slot)
}
