package io.alexjoest.stackupup.audit

import net.minecraft.init.Bootstrap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.IItemHandlerModifiable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConservationAuditorTest {
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
    fun `conservationStub_fullInsertWithoutRemainder_shouldNotWarn`() {
        val event = unbalancedLookingEvent(handlerClassName = "balanced-stub", after = 100, remainderCount = 0)

        val outcome = ConservationAuditor.audit(event)

        assertNull(outcome.warning)
        assertTrue(event.balanced)
        assertTrue(ConservationAuditor.recordedWarnings().isEmpty())
    }

    @Test
    fun `truncatingStub_insertItemTruncatesTo64AndReturnsEmptyRemainder_shouldWarnWithClassName`() {
        val handler = TruncatingStubHandler()
        val offered = 100
        val remainder = handler.insertItem(0, stack(offered), false)
        val event = ConservationEvent(
            callSite = "ConservationAuditorTest",
            handlerClassName = handler.javaClass.name,
            slot = 0,
            simulate = false,
            offered = offered,
            before = 0,
            after = handler.stored,
            remainderCount = stackCount(remainder),
        )

        val outcome = ConservationAuditor.audit(event)

        val warning = outcome.warning ?: error("截断 stub 必须触发守恒告警")
        assertTrue(warning.contains(handler.javaClass.name))
        assertEquals(64, event.storedDelta)
        assertEquals(0, event.remainderCount)
        assertFalse(event.balanced)
        assertEquals(listOf(warning), ConservationAuditor.recordedWarnings())
    }

    @Test
    fun `auditCall_shouldNeverTouchBusinessInventory`() {
        val handler = RecordingNoopHandler()
        val event = ConservationEvent(
            callSite = "ConservationAuditorTest",
            handlerClassName = handler.javaClass.name,
            slot = 0,
            simulate = false,
            offered = 100,
            before = 0,
            after = 64,
            remainderCount = 0,
        )

        val outcome = ConservationAuditor.audit(event)

        assertNotNull(outcome.warning)
        assertEquals(0, handler.insertCalls)
        assertEquals(0, handler.extractCalls)
        assertEquals(0, handler.setCalls)
    }

    @Test
    fun `simulateEvent_shouldProduceNoWarningEvenIfUnbalanced`() {
        val event = ConservationEvent(
            callSite = "ConservationAuditorTest",
            handlerClassName = "simulate-handler",
            slot = 0,
            simulate = true,
            offered = 100,
            before = 0,
            after = 0,
            remainderCount = 0,
        )

        val outcome = ConservationAuditor.audit(event)

        assertNull(outcome.warning)
        assertTrue(ConservationAuditor.recordedWarnings().isEmpty())
    }

    @Test
    fun `disabledByDefault_shouldNotWarnOnUnbalanced`() {
        System.clearProperty(ConservationAuditor.ENABLE_PROPERTY)
        val event = unbalancedLookingEvent(handlerClassName = "disabled-handler", after = 64, remainderCount = 0)

        val outcome = ConservationAuditor.audit(event)

        assertNull(outcome.warning)
        assertTrue(ConservationAuditor.recordedWarnings().isEmpty())
    }

    @Test
    fun `conservationFormula_storedDeltaPlusRemainderShouldEqualOffered`() {
        val balanced = ConservationEvent(
            callSite = "ConservationAuditorTest",
            handlerClassName = "formula-handler",
            slot = 0,
            simulate = false,
            offered = 100,
            before = 40,
            after = 90,
            remainderCount = 50,
        )

        assertEquals(50, balanced.storedDelta)
        assertTrue(balanced.balanced)

        val unbalanced = balanced.copy(remainderCount = 40)

        assertFalse(unbalanced.balanced)
    }

    private fun unbalancedLookingEvent(handlerClassName: String, after: Int, remainderCount: Int) = ConservationEvent(
        callSite = "ConservationAuditorTest",
        handlerClassName = handlerClassName,
        slot = 0,
        simulate = false,
        offered = 100,
        before = 0,
        after = after,
        remainderCount = remainderCount,
    )

    /** 截断 stub：insertItem 把槽内数量截到 64，但返回空余量（声称全部接受）。 */
    private class TruncatingStubHandler : IItemHandler {
        var stored: Int = 0
            private set

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

        override fun getSlotLimit(slot: Int): Int = 64
    }

    /** 记录全部写入调用的 noop handler：审计调用期间必须零写入。 */
    private class RecordingNoopHandler : IItemHandlerModifiable {
        var insertCalls: Int = 0
            private set
        var extractCalls: Int = 0
            private set
        var setCalls: Int = 0
            private set

        override fun getSlots(): Int = 1

        override fun getStackInSlot(slot: Int): ItemStack = ItemStack.EMPTY

        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            insertCalls++
            return stack
        }

        override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack {
            extractCalls++
            return ItemStack.EMPTY
        }

        override fun getSlotLimit(slot: Int): Int = 64

        override fun setStackInSlot(slot: Int, stack: ItemStack) {
            setCalls++
        }
    }

    companion object {
        private lateinit var auditItem: Item

        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            Bootstrap.register()
            auditItem = Item()
                .setMaxStackSize(256)
                .setRegistryName(ResourceLocation("stackupup_test", "audit_item"))
        }

        private fun stack(count: Int): ItemStack {
            val stack = ItemStack(auditItem, 0)
            stack.count = count
            return stack
        }

        private fun stackCount(stack: ItemStack): Int = if (stack.isEmpty) 0 else stack.count
    }
}
