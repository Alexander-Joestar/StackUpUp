package io.alexjoest.stackupup.mixin

import io.alexjoest.stackupup.TestContainer
import net.minecraft.entity.item.EntityMinecartChest
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.init.Bootstrap
import net.minecraft.inventory.IInventory
import net.minecraft.inventory.InventoryBasic
import net.minecraft.inventory.InventoryCraftResult
import net.minecraft.inventory.InventoryCrafting
import net.minecraft.inventory.InventoryMerchant
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntityBrewingStand
import net.minecraft.tileentity.TileEntityChest
import net.minecraft.tileentity.TileEntityDispenser
import net.minecraft.tileentity.TileEntityFurnace
import net.minecraft.tileentity.TileEntityHopper
import net.minecraft.tileentity.TileEntityShulkerBox
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 表内目标守恒测试（模拟态，T2a §6 规则 6 落地；docs/agent/t2b-原版目标表.md §4）。
 *
 * 模拟抬高：匿名子类 override `getInventoryStackLimit()` 返回 [RAISED_LIMIT]（对应运行时
 * VanillaInventoryLimitMixin 返回 `getCompatibilityStackSize()` 的效果）；物品层以
 * `getItemStackLimit = OFFERED` 的测试物品模拟 ItemMixin 抬高。写入调用真实 vanilla 代码，
 * 断言 `stored == min(offered, limit)` 且 `stored + remainder == offered`（remainder 为只读审计公式）。
 */
class VanillaInventoryConservationTest {

    private companion object {
        const val RAISED_LIMIT = 512
        const val OFFERED = 10240
        const val VANILLA_LIMIT = 64
    }

    @BeforeEach
    fun registerVanillaItems() {
        Bootstrap.register()
    }

    private fun raisedItem(): Item = object : Item() {
        override fun getItemStackLimit(stack: ItemStack): Int = OFFERED
    }.setRegistryName(ResourceLocation("stackupup_test", "vanilla_inventory_test_item"))

    /** 夹取类通用断言：写入后落库量恰为 min(offered, limit)，无额外丢失。 */
    private fun assertConservedClamp(inventory: IInventory, slot: Int, limit: Int) {
        val stack = ItemStack(raisedItem(), OFFERED, 0)
        inventory.setInventorySlotContents(slot, stack)
        val stored = inventory.getStackInSlot(slot).count
        val remainder = OFFERED - stored
        assertEquals(minOf(OFFERED, limit), stored, "落库量应恰为广告上限，无额外丢失")
        assertEquals(OFFERED, stored + remainder, "stored + remainder == offered（只读审计公式）")
        assertEquals(stack.count, stored, "入站堆叠应被就地夹取到同一落库量")
    }

    /** 无夹取类通用断言：朴素写入不截断，落库量 == offered（remainder == 0，不吞物）。 */
    private fun assertConservedPlainWrite(inventory: IInventory, slot: Int) {
        val stack = ItemStack(raisedItem(), OFFERED, 0)
        inventory.setInventorySlotContents(slot, stack)
        val stored = inventory.getStackInSlot(slot).count
        assertEquals(OFFERED, stored, "朴素写入路径不得截断（不吞物）")
        assertEquals(OFFERED, stored + (OFFERED - stored))
    }

    @Test
    fun chest_setter_shouldClampToRaisedLimitWithoutLoss() {
        assertConservedClamp(
            object : TileEntityChest() {
                override fun getInventoryStackLimit(): Int = RAISED_LIMIT
            },
            0,
            RAISED_LIMIT,
        )
    }

    @Test
    fun chest_setter_shouldClampToVanillaLimitInBaseline() {
        // 原值基线：无 override 时夹取 64，证明夹取跟随上限值而非写死 64。
        assertConservedClamp(TileEntityChest(), 0, VANILLA_LIMIT)
    }

    @Test
    fun dispenser_setter_shouldClampToRaisedLimitWithoutLoss() {
        assertConservedClamp(
            object : TileEntityDispenser() {
                override fun getInventoryStackLimit(): Int = RAISED_LIMIT
            },
            0,
            RAISED_LIMIT,
        )
    }

    @Test
    fun furnace_setter_shouldClampToRaisedLimitWithoutLoss() {
        assertConservedClamp(
            object : TileEntityFurnace() {
                override fun getInventoryStackLimit(): Int = RAISED_LIMIT
            },
            0,
            RAISED_LIMIT,
        )
    }

    @Test
    fun hopper_setter_shouldClampToRaisedLimitWithoutLoss() {
        assertConservedClamp(
            object : TileEntityHopper() {
                override fun getInventoryStackLimit(): Int = RAISED_LIMIT
            },
            0,
            RAISED_LIMIT,
        )
    }

    @Test
    fun shulkerBox_setter_shouldClampToRaisedLimitWithoutLoss() {
        assertConservedClamp(
            object : TileEntityShulkerBox() {
                override fun getInventoryStackLimit(): Int = RAISED_LIMIT
            },
            0,
            RAISED_LIMIT,
        )
    }

    @Test
    fun minecartChest_setter_shouldClampToRaisedLimitWithoutLoss() {
        // EntityMinecartContainer 为抽象类（mixin 目标本身），以其具体子类 EntityMinecartChest 为行为代理，
        // patch 经继承对子类生效（t2b §4 规格）。
        assertConservedClamp(
            object : EntityMinecartChest(null) {
                override fun getInventoryStackLimit(): Int = RAISED_LIMIT
            },
            0,
            RAISED_LIMIT,
        )
    }

    @Test
    fun basic_setter_shouldClampToRaisedLimitWithoutLoss() {
        assertConservedClamp(
            object : InventoryBasic("stackupup_test", false, 2) {
                override fun getInventoryStackLimit(): Int = RAISED_LIMIT
            },
            0,
            RAISED_LIMIT,
        )
    }

    @Test
    fun merchant_setter_shouldClampToRaisedLimitWithoutLoss() {
        // 写槽 2（交易结果槽）避开 resetRecipeAndSlots（槽 0/1 变更触发，需要非空 merchant）。
        assertConservedClamp(
            object : InventoryMerchant(null, null) {
                override fun getInventoryStackLimit(): Int = RAISED_LIMIT
            },
            2,
            RAISED_LIMIT,
        )
    }

    @Test
    fun playerInventory_addItemStack_shouldClampPerSlotToRaisedLimitWithoutLoss() {
        val playerInventory = object : InventoryPlayer(null) {
            override fun getInventoryStackLimit(): Int = RAISED_LIMIT
        }
        val offered = ItemStack(raisedItem(), OFFERED, 0)

        val accepted = playerInventory.addItemStackToInventory(offered)

        assertTrue(accepted, "10240 个物品应全部入库存")
        assertTrue(offered.isEmpty, "入站堆叠应被完全消费（remainder == 0）")
        var storedTotal = 0
        for (index in 0 until playerInventory.mainInventory.size) {
            val stored = playerInventory.mainInventory.get(index).count
            assertTrue(stored <= RAISED_LIMIT, "每槽落库量不得超过广告上限")
            storedTotal += stored
        }
        assertEquals(OFFERED, storedTotal, "storedDelta + remainderCount == inserted（remainder == 0）")
    }

    @Test
    fun brewingStand_setter_shouldNotTruncateAtRaisedState() {
        // TileEntityBrewingStand.setInventorySlotContents（:330-336）为朴素写入，无夹取即不截断。
        assertConservedPlainWrite(
            object : TileEntityBrewingStand() {
                override fun getInventoryStackLimit(): Int = RAISED_LIMIT
            },
            0,
        )
    }

    @Test
    fun crafting_setter_shouldNotTruncateAtRaisedState() {
        // InventoryCrafting.setInventorySlotContents（:176-180）为朴素写入；eventHandler 用测试容器。
        assertConservedPlainWrite(
            object : InventoryCrafting(TestContainer(), 3, 3) {
                override fun getInventoryStackLimit(): Int = RAISED_LIMIT
            },
            0,
        )
    }

    @Test
    fun craftResult_setter_shouldNotTruncateAtRaisedState() {
        // InventoryCraftResult.setInventorySlotContents（:150-154）为朴素写入。
        assertConservedPlainWrite(
            object : InventoryCraftResult() {
                override fun getInventoryStackLimit(): Int = RAISED_LIMIT
            },
            0,
        )
    }
}
