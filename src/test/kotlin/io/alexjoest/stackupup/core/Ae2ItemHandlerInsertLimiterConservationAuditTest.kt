package io.alexjoest.stackupup.core

import io.alexjoest.stackupup.audit.ConservationAuditor
import io.alexjoest.stackupup.audit.ConservationReportWriter
import net.minecraft.init.Bootstrap
import net.minecraft.inventory.InventoryBasic
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.items.wrapper.InvWrapper
import net.minecraftforge.items.wrapper.SidedInvWrapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * 守恒审计在 Ae2ItemHandlerInsertLimiter 真实插入循环上的集成测试。
 */
class Ae2ItemHandlerInsertLimiterConservationAuditTest {
    private lateinit var reportDir: File

    @BeforeEach
    fun enableAudit() {
        // 开关改为类加载单次读取（T12.4），运行期不再读系统属性；测试经内部钩子显式切换。
        ConservationAuditor.setEnabledForTesting(true)
        // 报告文件重定向到临时目录（T12.5），避免测试写入默认 run/logs 路径。
        reportDir = Files.createTempDirectory("stackupup-conservation-ae2").toFile()
        ConservationReportWriter.setReportFileForTesting(File(reportDir, "stackupup-conservation.jsonl"))
        ConservationReportWriter.resetForTesting()
    }

    @AfterEach
    fun resetAudit() {
        ConservationAuditor.setEnabledForTesting(false)
        ConservationAuditor.clearRecordedWarnings()
        ConservationReportWriter.resetForTesting()
        reportDir.deleteRecursively()
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

    /** T10 移出白名单项：InvWrapper 经 64 限流分片投喂，delegate 自洽时守恒且不告警。 */
    @Test
    fun `insertCapped_invWrapper_untrustedChunked_shouldConserveWithoutWarning`() {
        val inventory = LimitedInventory(64)
        val wrapper = InvWrapper(inventory)
        val result = Ae2ItemHandlerInsertLimiter.insertCapped(wrapper, 0, stack(150), false)

        val stored = inventory.getStackInSlot(0).count
        val remainderCount = if (result.isEmpty) 0 else result.count
        assertEquals(150, stored + remainderCount, "投入 150，写入 $stored，退回 $remainderCount")
        assertTrue(ConservationAuditor.recordedWarnings().isEmpty())
    }

    /** T10 移出白名单项：SidedInvWrapper 同理分片投喂，守恒且不告警。 */
    @Test
    fun `insertCapped_sidedInvWrapper_untrustedChunked_shouldConserveWithoutWarning`() {
        val inventory = LimitedSidedInventory(64)
        val wrapper = SidedInvWrapper(inventory, EnumFacing.NORTH)
        val result = Ae2ItemHandlerInsertLimiter.insertCapped(wrapper, 0, stack(150), false)

        val stored = inventory.getStackInSlot(0).count
        val remainderCount = if (result.isEmpty) 0 else result.count
        assertEquals(150, stored + remainderCount, "投入 150，写入 $stored，退回 $remainderCount")
        assertTrue(ConservationAuditor.recordedWarnings().isEmpty())
    }

    /** T10 保留白名单项：ItemStackHandler 直通真实插入，守恒且不告警（64 上限处闭合 remainder）。 */
    @Test
    fun `insertCapped_itemStackHandler_trustedPassThrough_shouldConserveWithoutWarning`() {
        val handler = ItemStackHandler(1)
        val result = Ae2ItemHandlerInsertLimiter.insertCapped(handler, 0, stack(150), false)

        val stored = handler.getStackInSlot(0).count
        val remainderCount = if (result.isEmpty) 0 else result.count
        assertEquals(150, stored + remainderCount, "投入 150，写入 $stored，退回 $remainderCount")
        assertEquals(64, stored, "未提升的 ItemStackHandler 真实写入上限应为 64")
        assertTrue(ConservationAuditor.recordedWarnings().isEmpty())
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

    /** 自洽的 vanilla 型库存：setter 按 getInventoryStackLimit 夹取（InventoryBasic.java:143-149 同语义）。 */
    private class LimitedInventory(private val stackLimit: Int) : InventoryBasic("stackupup-audit-limiter", false, 1) {
        override fun getInventoryStackLimit(): Int = stackLimit
    }

    /** SidedInvWrapper 的 delegate 需要 ISidedInventory（SidedInvWrapper.java:34-41）。 */
    private class LimitedSidedInventory(private val stackLimit: Int) :
        InventoryBasic("stackupup-audit-limiter-sided", false, 1),
        net.minecraft.inventory.ISidedInventory {
        override fun getInventoryStackLimit(): Int = stackLimit

        override fun getSlotsForFace(side: EnumFacing): IntArray = intArrayOf(0)

        override fun canInsertItem(index: Int, stack: ItemStack, direction: EnumFacing): Boolean = true

        override fun canExtractItem(index: Int, stack: ItemStack, direction: EnumFacing): Boolean = true
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
