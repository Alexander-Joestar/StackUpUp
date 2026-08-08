package io.alexjoest.stackupup.core

import io.alexjoest.stackupup.StackLimitHooks
import io.alexjoest.stackupup.StackUpUpConfig
import net.minecraft.entity.EntityLivingBase
import net.minecraft.init.Bootstrap
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.inventory.InventoryBasic
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.profiler.Profiler
import net.minecraft.util.EnumHandSide
import net.minecraft.util.NonNullList
import net.minecraft.util.ResourceLocation
import net.minecraft.world.World
import net.minecraft.world.WorldProviderSurface
import net.minecraft.world.chunk.IChunkProvider
import net.minecraft.world.storage.WorldInfo
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.items.wrapper.CombinedInvWrapper
import net.minecraftforge.items.wrapper.EntityArmorInvWrapper
import net.minecraftforge.items.wrapper.EntityHandsInvWrapper
import net.minecraftforge.items.wrapper.InvWrapper
import net.minecraftforge.items.wrapper.RangedWrapper
import net.minecraftforge.items.wrapper.SidedInvWrapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Forge handler / 转发 wrapper 的真实 remainder 守恒与 T3 收敛护栏。
 *
 * 第一部分是重构前诊断基线：转发型 wrapper 的 getSlotLimit 与真实写入容量一致性、守恒公式
 * `stored + remainderCount == offered`。
 * 第二部分是 T3 收敛护栏（docs/agent/compatibility-decision-record.md §3.5）：
 * ItemStackHandler / EntityEquipmentInvWrapper 保留注入（真实写入守恒）；四个转发 wrapper
 * 不再注入（getSlotLimit 只转发底层真实来源，不得抬到兼容上限）；SlotItemHandler 不再有
 * getSlotStackLimit 独立上限注入（字节码结构检查）。
 *
 * mixin 在纯 JUnit 中不生效：提升效果用真实子类覆盖 getSlotLimit 模拟（结构与 mixin 注入等价），
 * 守恒断言全部走真实 insertItem(simulate=false) 写入与 remainder 返回。
 */
class WrapperCapacityDiagnosticTest {
    private var previousMaxStackSize: Int = 64

    @BeforeEach
    fun setUpCompatLimit() {
        previousMaxStackSize = StackUpUpConfig.activeMaxStackSize
        StackUpUpConfig.general.maxStackSize = COMPAT_LIMIT
        StackUpUpConfig.activeMaxStackSize = COMPAT_LIMIT
    }

    @AfterEach
    fun restoreCompatLimit() {
        StackUpUpConfig.activeMaxStackSize = previousMaxStackSize
    }

    /** InvWrapper.getSlotLimit 是否直接转发背后 IInventory 的真实上限（不得被抬到兼容上限）。 */
    @Test
    fun `invWrapper_slotLimit_reflectsBackingInventoryLimit`() {
        val wrapper = InvWrapper(LimitedInventory(64))
        assertEquals(64, wrapper.getSlotLimit(0), "InvWrapper 应转发背后库存的真实上限")
        assertTrue(
            wrapper.getSlotLimit(0) != StackLimitHooks.getCompatibilityStackSize(),
            "转发 wrapper 不得被抬到兼容上限（compat=${StackLimitHooks.getCompatibilityStackSize()}）",
        )
    }

    /** 关键诊断：向只接受 64 的库存插入 150，wrapper 报告的 remainder 是否诚实。 */
    @Test
    fun `invWrapper_insertOversized_remainderMustAccountForTruncation`() {
        val inventory = LimitedInventory(64)
        val wrapper = InvWrapper(inventory)

        val remainder = wrapper.insertItem(0, stack(150), false)

        val stored = inventory.getStackInSlot(0).count
        val remainderCount = if (remainder.isEmpty) 0 else remainder.count
        // 守恒：真实写入 + 诚实退回 == 投入总量。不成立即为吞物品。
        assertEquals(
            150,
            stored + remainderCount,
            "投入 150，实际写入 $stored，退回 $remainderCount —— 差额即被吞掉的数量",
        )
    }

    /** SidedInvWrapper 走的是另一套静态插入逻辑，单独确认。 */
    @Test
    fun `sidedInvWrapper_insertOversized_remainderMustAccountForTruncation`() {
        val inventory = LimitedSidedInventory(64)
        val wrapper = SidedInvWrapper(inventory, net.minecraft.util.EnumFacing.NORTH)

        val remainder = wrapper.insertItem(0, stack(150), false)

        val stored = inventory.getStackInSlot(0).count
        val remainderCount = if (remainder.isEmpty) 0 else remainder.count
        assertEquals(
            150,
            stored + remainderCount,
            "投入 150，实际写入 $stored，退回 $remainderCount",
        )
    }

    /** 端到端：AE2 限流器把 InvWrapper 当 trusted 直通后，守恒是否仍成立。 */
    @Test
    fun `ae2Limiter_trustedInvWrapper_overUnexpandedInventory_conservesItems`() {
        val inventory = LimitedInventory(64)
        val wrapper = InvWrapper(inventory)

        val remainder = Ae2ItemHandlerInsertLimiter.insertCapped(wrapper, 0, stack(150), false)

        val stored = inventory.getStackInSlot(0).count
        val remainderCount = if (remainder.isEmpty) 0 else remainder.count
        assertEquals(
            150,
            stored + remainderCount,
            "经 trusted 直通后：写入 $stored，退回 $remainderCount",
        )
    }

    /** 自洽站点基线：未提升的 ItemStackHandler 在 64 上限处闭合 remainder。 */
    @Test
    fun `itemStackHandler_insertOversized_vanillaLimit_conservesItems`() {
        val handler = ItemStackHandler(1)
        assertEquals(64, handler.getSlotLimit(0))

        val remainder = handler.insertItem(0, stack(150), false)

        val stored = handler.getStackInSlot(0).count
        val remainderCount = if (remainder.isEmpty) 0 else remainder.count
        assertEquals(150, stored + remainderCount, "投入 150，写入 $stored，退回 $remainderCount")
        assertEquals(64, stored, "未提升时真实写入上限应为 64")
    }

    /**
     * 自洽站点保留动作护栏：ItemStackHandler 被抬到兼容上限后，真实 insertItem 写入
     * （ItemStackHandler.java:88 读 getStackLimit、:107-116 落库并返回 remainder）必须守恒。
     */
    @Test
    fun `itemStackHandler_insertOversized_raisedSlotLimit_conservesItems`() {
        val handler = RaisedLimitItemStackHandler(StackLimitHooks.getCompatibilityStackSize())
        assertEquals(StackLimitHooks.getCompatibilityStackSize(), handler.getSlotLimit(0))

        val remainder = handler.insertItem(0, stack(300), false)

        val stored = handler.getStackInSlot(0).count
        val remainderCount = if (remainder.isEmpty) 0 else remainder.count
        assertEquals(300, stored + remainderCount, "投入 300，写入 $stored，退回 $remainderCount")
        assertEquals(StackLimitHooks.getCompatibilityStackSize(), stored, "广告上限与真实写入量应一致")
    }

    /** 转发链不注入：CombinedInvWrapper 的 getSlotLimit 转发子 handler，写入经子 handler 闭合 remainder。 */
    @Test
    fun `combinedInvWrapper_insertOversized_remainderMustAccountForTruncation`() {
        val wrapper = CombinedInvWrapper(ItemStackHandler(1), ItemStackHandler(1))
        assertEquals(64, wrapper.getSlotLimit(0), "CombinedInvWrapper 应转发子 handler 的真实上限")

        val remainder = wrapper.insertItem(0, stack(150), false)

        val stored = wrapper.getStackInSlot(0).count
        val remainderCount = if (remainder.isEmpty) 0 else remainder.count
        assertEquals(150, stored + remainderCount, "投入 150，实际写入 $stored，退回 $remainderCount")
    }

    /** 转发链不注入：RangedWrapper 的 getSlotLimit 转发 compose，写入经 compose 闭合 remainder。 */
    @Test
    fun `rangedWrapper_insertOversized_remainderMustAccountForTruncation`() {
        val wrapper = RangedWrapper(ItemStackHandler(3), 0, 2)
        assertEquals(64, wrapper.getSlotLimit(0), "RangedWrapper 应转发 compose 的真实上限")

        val remainder = wrapper.insertItem(0, stack(150), false)

        val stored = wrapper.getStackInSlot(0).count
        val remainderCount = if (remainder.isEmpty) 0 else remainder.count
        assertEquals(150, stored + remainderCount, "投入 150，实际写入 $stored，退回 $remainderCount")
    }

    /**
     * 底层 IInventory 上限被抬（模拟 VanillaInventoryLimitMixin 效果）时，转发 wrapper 自然跟随真实来源，
     * 不需要对 wrapper 自身注入；写入经 InventoryBasic 的 clamp 截断并退回 remainder。
     */
    @Test
    fun `invWrapper_overRaisedInventory_forwardsRealCapacity`() {
        val inventory = LimitedInventory(StackLimitHooks.getCompatibilityStackSize())
        val wrapper = InvWrapper(inventory)
        assertEquals(StackLimitHooks.getCompatibilityStackSize(), wrapper.getSlotLimit(0))

        val remainder = wrapper.insertItem(0, stack(300), false)

        val stored = inventory.getStackInSlot(0).count
        val remainderCount = if (remainder.isEmpty) 0 else remainder.count
        assertEquals(300, stored + remainderCount, "投入 300，写入 $stored，退回 $remainderCount")
        assertEquals(StackLimitHooks.getCompatibilityStackSize(), stored)
    }

    /**
     * EntityEquipment 保留动作护栏（手部）：手部槽被抬到兼容上限（模拟 mixin 64→compat）后，
     * Forge insertItem 的上限计算与 remainder 闭合（EntityEquipmentInvWrapper.java:86-122），
     * vanilla 实体 setter 为无截断列表直写（EntityLiving.java:1012-1022 同语义），写入量守恒。
     */
    @Test
    fun `entityHandsWrapper_insertOversized_raisedHandLimit_conservesItems`() {
        val wrapper = RaisedHandsInvWrapper(StubLivingEntity(TestWorld()))
        assertEquals(StackLimitHooks.getCompatibilityStackSize(), wrapper.getSlotLimit(0))

        val remainder = wrapper.insertItem(0, stack(300), false)

        val stored = wrapper.getStackInSlot(0).count
        val remainderCount = if (remainder.isEmpty) 0 else remainder.count
        assertEquals(300, stored + remainderCount, "投入 300，写入 $stored，退回 $remainderCount")
        assertEquals(StackLimitHooks.getCompatibilityStackSize(), stored, "广告上限与真实写入量应一致")
    }

    /** EntityEquipment 保留动作护栏（装甲槽）：slot limit 恒为 1（不可堆叠语义），不得被提升，remainder 闭合。 */
    @Test
    fun `entityArmorWrapper_insertOversized_armorSlotKeepsLimitOne`() {
        val wrapper = EntityArmorInvWrapper(StubLivingEntity(TestWorld()))
        assertEquals(1, wrapper.getSlotLimit(0), "装甲槽 slot limit 应为 1 且不被提升")

        val remainder = wrapper.insertItem(0, stack(5), false)

        val stored = wrapper.getStackInSlot(0).count
        val remainderCount = if (remainder.isEmpty) 0 else remainder.count
        assertEquals(5, stored + remainderCount, "投入 5，写入 $stored，退回 $remainderCount")
        assertEquals(1, stored, "装甲槽真实写入量应为 1")
    }

    /** 结构检查（非行为验证）：ForgeItemHandlerLimitMixin 只保留两个自洽目标，四个转发 wrapper 已移除。 */
    @Test
    fun `forgeItemHandlerLimitMixin_shouldNotTargetForwardingWrappers`() {
        val bytes = mixinClassBytes("io.alexjoest.stackupup.mixin.early.ForgeItemHandlerLimitMixin")

        for (kept in listOf("ItemStackHandler", "EntityEquipmentInvWrapper")) {
            assertTrue(bytes.containsAscii(kept), "自洽目标 $kept 应保留在 mixin 目标中")
        }
        for (removed in listOf(
            "wrapper/InvWrapper",
            "wrapper/SidedInvWrapper",
            "wrapper/CombinedInvWrapper",
            "wrapper/RangedWrapper",
        )) {
            assertFalse(bytes.containsAscii(removed), "转发 wrapper $removed 不得再出现在 mixin 目标中")
        }
    }

    /** 结构检查（非行为验证）：SlotItemHandler 的 getSlotStackLimit 独立上限注入已移除，simulate 基广告保留。 */
    @Test
    fun `slotItemHandlerMixin_shouldNotInjectIndependentSlotLimit`() {
        val bytes = mixinClassBytes("io.alexjoest.stackupup.mixin.early.SlotItemHandlerMixin")

        assertFalse(
            bytes.containsAscii("stackupup\$replaceCompatibilityLimit"),
            "getSlotStackLimit 的独立动态上限注入应已移除（转发型广告不得独立抬高）",
        )
        assertTrue(
            bytes.containsAscii("stackupup\$resolveItemAwareLimit"),
            "getItemStackLimit 的 simulate 基收敛应保留（自洽站点）",
        )
    }

    private fun mixinClassBytes(className: String): ByteArray =
        requireNotNull(Class.forName(className).getResourceAsStream("${className.substringAfterLast('.')}.class")) {
            "无法读取 $className.class"
        }.use { it.readBytes() }

    /** 结构检查辅助：断言字节码原始文本中包含指定 ASCII 序列（与 DynamicCompatEarlyPathBytecodeTest 同型私有扩展）。 */
    private fun ByteArray.containsAscii(value: String): Boolean {
        if (isEmpty()) {
            return false
        }

        val target = value.encodeToByteArray()
        val lastIndex = size - target.size
        if (lastIndex < 0) {
            return false
        }

        for (index in 0..lastIndex) {
            if (matchesAt(index, target)) {
                return true
            }
        }

        return false
    }

    private fun ByteArray.matchesAt(startIndex: Int, target: ByteArray): Boolean {
        for (offset in target.indices) {
            if (this[startIndex + offset] != target[offset]) {
                return false
            }
        }
        return true
    }

    private class LimitedInventory(private val stackLimit: Int) : InventoryBasic("stackupup-diagnostic", false, 1) {
        override fun getInventoryStackLimit(): Int = stackLimit
    }

    private class LimitedSidedInventory(private val stackLimit: Int) :
        InventoryBasic("stackupup-diagnostic-sided", false, 1),
        net.minecraft.inventory.ISidedInventory {
        override fun getInventoryStackLimit(): Int = stackLimit

        override fun getSlotsForFace(side: net.minecraft.util.EnumFacing): IntArray = intArrayOf(0)

        override fun canInsertItem(index: Int, stack: ItemStack, direction: net.minecraft.util.EnumFacing): Boolean = true

        override fun canExtractItem(index: Int, stack: ItemStack, direction: net.minecraft.util.EnumFacing): Boolean = true
    }

    /** 以真实子类覆盖 getSlotLimit 模拟 ForgeItemHandlerLimitMixin 的提升效果（结构与 mixin 注入等价）。 */
    private class RaisedLimitItemStackHandler(private val slotLimit: Int) : ItemStackHandler(1) {
        override fun getSlotLimit(slot: Int): Int = slotLimit
    }

    /** 模拟 mixin 对 EntityEquipmentInvWrapper 手部槽 64→compat 的提升。 */
    private class RaisedHandsInvWrapper(entity: EntityLivingBase) : EntityHandsInvWrapper(entity) {
        override fun getSlotLimit(slot: Int): Int = StackLimitHooks.getCompatibilityStackSize()
    }

    /** 最小 World stub：只实现两个抽象方法；测试路径不会访问 chunk。 */
    private class TestWorld : World(null, WorldInfo(NBTTagCompound()), WorldProviderSurface(), Profiler(), false) {
        override fun createChunkProvider(): IChunkProvider? = null

        override fun isChunkLoaded(x: Int, z: Int, allowEmpty: Boolean): Boolean = false
    }

    /** 最小实体 stub：以列表直写实现 EntityLivingBase 的抽象方法（与 vanilla 实体 setter 同语义）。 */
    private class StubLivingEntity(world: World) : EntityLivingBase(world) {
        private val hands = NonNullList.withSize(2, ItemStack.EMPTY)
        private val armor = NonNullList.withSize(4, ItemStack.EMPTY)

        override fun getArmorInventoryList(): Iterable<ItemStack> = armor

        override fun getItemStackFromSlot(slotIn: EntityEquipmentSlot): ItemStack =
            if (slotIn.slotType == EntityEquipmentSlot.Type.ARMOR) armor[slotIn.index] else hands[slotIn.index]

        override fun setItemStackToSlot(slotIn: EntityEquipmentSlot, stack: ItemStack) {
            if (slotIn.slotType == EntityEquipmentSlot.Type.ARMOR) {
                armor[slotIn.index] = stack
            } else {
                hands[slotIn.index] = stack
            }
        }

        override fun getPrimaryHand(): EnumHandSide = EnumHandSide.RIGHT

        override fun readEntityFromNBT(compound: NBTTagCompound) = Unit

        override fun writeEntityToNBT(compound: NBTTagCompound) = Unit
    }

    companion object {
        private const val COMPAT_LIMIT = 256
        private lateinit var testItem: Item

        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            Bootstrap.register()
            testItem = Item()
                .setMaxStackSize(1024)
                .setRegistryName(ResourceLocation("stackupup_test", "diagnostic_item"))
        }

        private fun stack(count: Int): ItemStack = ItemStack(testItem, count)
    }
}
