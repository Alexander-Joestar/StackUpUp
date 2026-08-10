package io.alexjoest.stackupup.mixin

import io.alexjoest.stackupup.mixin.early.VanillaInventoryTargets
import net.minecraft.init.Bootstrap
import net.minecraft.inventory.InventoryLargeChest
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntityChest
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 两半箱不一致测试（docs/agent/t2b-原版目标表.md §3.1）：证明 InventoryLargeChest 是转发包装器
 * 而非独立容量来源——广告转发上箱、写入按 index 分发两半各自夹取；两半上限不一致时
 * 广告（上箱）与下箱写入夹取脱节（条件断链），因此不在目标表。
 */
class VanillaInventoryLargeChestTest {

    private companion object {
        const val RAISED_LIMIT = 512
        const val OFFERED = 10240
    }

    @BeforeEach
    fun registerVanillaItems() {
        Bootstrap.register()
    }

    private fun raisedItem(): Item = object : Item() {
        override fun getItemStackLimit(stack: ItemStack): Int = OFFERED
    }.setRegistryName(ResourceLocation("stackupup_test", "large_chest_test_item"))

    @Test
    fun advertisedLimit_followsUpperHalf_whileLowerHalfWriteClampsToItsOwnLimit() {
        val upper = object : TileEntityChest() {
            override fun getInventoryStackLimit(): Int = RAISED_LIMIT
        }
        val lower = TileEntityChest()
        val largeChest = InventoryLargeChest("stackupup_test_double", upper, lower)

        // 广告转发上箱：512。
        assertEquals(RAISED_LIMIT, largeChest.getInventoryStackLimit())

        // 下箱槽位写入按 index 分发到 lower，夹取到 lower 自身上限（64）——广告（512）与写入夹取（64）
        // 脱节 = 条件断链：若下箱槽经大箱广告放行 512，落库只剩 64，超出部分被丢弃。
        val offeredToLower = ItemStack(raisedItem(), OFFERED, 0)
        largeChest.setInventorySlotContents(upper.getSizeInventory(), offeredToLower)
        val lowerStored = largeChest.getStackInSlot(upper.getSizeInventory()).count
        assertEquals(lower.getInventoryStackLimit(), lowerStored, "下箱写入夹取应等于下箱自身上限，而非广告值")
        assertEquals(64, lowerStored)
        assertEquals(64, offeredToLower.count, "入站堆叠被就地夹取到 64（与广告 512 不一致）")

        // 上箱槽位写入夹取到 512 —— 同一大箱两半上限不一致，证明大箱自身不是单一容量来源。
        val offeredToUpper = ItemStack(raisedItem(), OFFERED, 0)
        largeChest.setInventorySlotContents(0, offeredToUpper)
        assertEquals(RAISED_LIMIT, largeChest.getStackInSlot(0).count)
    }

    @Test
    fun advertisedLimit_followsUpperHalf_whenLowerHalfRaised() {
        // 对称情形：上箱原值、下箱抬高 —— 广告仍取上箱（64），下箱写入夹取 512；
        // 广告不虚报，但也不代表真实写入面（下箱），进一步证明转发语义。
        val upper = TileEntityChest()
        val lower = object : TileEntityChest() {
            override fun getInventoryStackLimit(): Int = RAISED_LIMIT
        }
        val largeChest = InventoryLargeChest("stackupup_test_double", upper, lower)

        assertEquals(64, largeChest.getInventoryStackLimit())

        val offered = ItemStack(raisedItem(), OFFERED, 0)
        largeChest.setInventorySlotContents(upper.getSizeInventory(), offered)
        assertEquals(RAISED_LIMIT, largeChest.getStackInSlot(upper.getSizeInventory()).count)
    }

    @Test
    fun largeChest_shouldNotBeInTargetTable() {
        assertFalse(VanillaInventoryTargets.TARGETS.contains(InventoryLargeChest::class.java))
        assertTrue(VanillaInventoryTargets.EXCLUDED.contains(InventoryLargeChest::class.java))
    }
}
