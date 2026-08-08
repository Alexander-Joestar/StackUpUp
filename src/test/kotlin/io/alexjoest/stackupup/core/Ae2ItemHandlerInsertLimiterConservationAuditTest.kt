package io.alexjoest.stackupup.core

import io.alexjoest.stackupup.audit.ConservationAuditor
import net.minecraft.init.Bootstrap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.items.IItemHandler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 守恒审计在 Ae2ItemHandlerInsertLimiter 真实插入循环上的集成测试。
 */
class Ae2ItemHandlerInsertLimiterConservationAuditTest {
    @BeforeEach
    fun enableAudit() {
        System.setProperty(ConservationAuditor.ENABLE_PROPERTY, "true")
    }

    @AfterEach
    fun resetAudit() {
        System.clearProperty(ConservationAuditor.ENABLE_PROPERTY)
        ConservationAuditor.clearRecordedWarnings()
    }

    @Test
    fun `insertCapped_truncatingHandler_shouldWarnWithHandlerClassName`() {
        val handler = TruncatingHandler()
        val result = Ae2ItemHandlerInsertLimiter.insertCapped(handler, 0, stack(128), false)

        val warnings = ConservationAuditor.recordedWarnings()
        assertTrue(warnings.isNotEmpty())
        assertTrue(warnings.any { it.contains(handler.javaClass.name) })
        assertTrue(result.isEmpty)
    }

    @Test
    fun `insertCapped_conservingHandler_shouldNotWarn`() {
        val handler = AcceptingHandler()
        val result = Ae2ItemHandlerInsertLimiter.insertCapped(handler, 0, stack(128), false)

        assertTrue(ConservationAuditor.recordedWarnings().isEmpty())
        assertTrue(result.isEmpty)
    }

    @Test
    fun `insertCapped_simulateInsert_shouldProduceNoEvent`() {
        val handler = TruncatingHandler()
        val result = Ae2ItemHandlerInsertLimiter.insertCapped(handler, 0, stack(128), true)

        assertTrue(ConservationAuditor.recordedWarnings().isEmpty())
        assertFalse(result.isEmpty)
    }

    /** 截断 handler：槽内最多存 64，超出部分被吞掉且返回空余量。 */
    private class TruncatingHandler : IItemHandler {
        private var stored = 0

        override fun getSlots(): Int = 1

        override fun getStackInSlot(slot: Int): ItemStack = if (stored > 0) stack(stored) else ItemStack.EMPTY

        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            val accepted = minOf(stack.count, 64 - stored).coerceAtLeast(0)
            if (!simulate) {
                stored += accepted
            }
            return ItemStack.EMPTY
        }

        override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack = ItemStack.EMPTY

        override fun getSlotLimit(slot: Int): Int = 256
    }

    /** 守恒 handler：按槽内容量真实接受并返回真实余量。 */
    private class AcceptingHandler : IItemHandler {
        private var stored = 0

        override fun getSlots(): Int = 1

        override fun getStackInSlot(slot: Int): ItemStack = if (stored > 0) stack(stored) else ItemStack.EMPTY

        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            val accepted = minOf(stack.count, 256 - stored).coerceAtLeast(0)
            if (!simulate) {
                stored += accepted
            }
            return if (accepted >= stack.count) ItemStack.EMPTY else stack(stack.count - accepted)
        }

        override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack = ItemStack.EMPTY

        override fun getSlotLimit(slot: Int): Int = 256
    }

    companion object {
        private lateinit var testItem: Item

        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            Bootstrap.register()
            testItem = Item()
                .setMaxStackSize(256)
                .setRegistryName(ResourceLocation("stackupup_test", "ae2_limiter_audit_item"))
        }

        private fun stack(count: Int): ItemStack = ItemStack(testItem, count)
    }
}
