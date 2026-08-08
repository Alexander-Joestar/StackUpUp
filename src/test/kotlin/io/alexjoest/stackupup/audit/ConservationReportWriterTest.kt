package io.alexjoest.stackupup.audit

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.init.Bootstrap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.items.IItemHandler
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
 * 守恒审计报告器行为测试（T12.5）：守恒 stub 与截断 stub 生成可解析 JSONL，
 * schema 字段齐全，不平衡条目可回到调用点与 handler 类名，simulate 事件写入但不计入不平衡汇总。
 */
class ConservationReportWriterTest {
    private lateinit var reportDir: File
    private lateinit var reportFile: File
    private lateinit var summaryFile: File

    @BeforeEach
    fun setUpReportFiles() {
        reportDir = Files.createTempDirectory("stackupup-conservation-report").toFile()
        reportFile = File(reportDir, "stackupup-conservation.jsonl")
        summaryFile = File(reportDir, "stackupup-conservation-summary.json")
        ConservationReportWriter.setReportFileForTesting(reportFile)
        ConservationReportWriter.resetForTesting()
        // 本类部分测试经 ConservationAuditor.audit 走完整链路，会写入全局告警记录；
        // 必须在前后清理，避免向同 JVM 的其他守恒测试类泄漏（同一次运行按测试类共享 JVM）。
        ConservationAuditor.clearRecordedWarnings()
    }

    @AfterEach
    fun tearDownReportFiles() {
        ConservationAuditor.setEnabledForTesting(false)
        ConservationAuditor.clearRecordedWarnings()
        ConservationReportWriter.resetForTesting()
        reportDir.deleteRecursively()
    }

    @Test
    fun `conservingStub_shouldProduceParseableJsonlWithAllSchemaFields`() {
        val handler = ConservingStubHandler()
        val offered = 128
        val remainder = handler.insertItem(0, stack(offered), false)
        val event = ConservationEvent(
            callSite = "conserving-stub",
            handlerClassName = handler.javaClass.name,
            slot = 0,
            simulate = false,
            offered = offered,
            before = 0,
            after = handler.stored,
            remainderCount = stackCount(remainder),
        )

        ConservationReportWriter.write(event)

        assertTrue(event.balanced)
        val line = parseLines().single()
        requireSchema(line)
        assertEquals(ConservationReportWriter.SCHEMA_VERSION, line.get("schemaVersion").getAsInt())
        assertEquals("conserving-stub", line.get("callSite").getAsString())
        assertEquals(handler.javaClass.name, line.get("handlerClassName").getAsString())
        assertEquals(0, line.get("slot").getAsInt())
        assertFalse(line.get("simulate").getAsBoolean())
        assertEquals(128, line.get("offered").getAsInt())
        assertEquals(0, line.get("before").getAsInt())
        assertEquals(128, line.get("after").getAsInt())
        assertEquals(128, line.get("storedDelta").getAsInt())
        assertEquals(0, line.get("remainderCount").getAsInt())
        assertTrue(line.get("balanced").getAsBoolean())
    }

    @Test
    fun `truncatingStub_shouldWriteUnbalancedEventAndSummaryEntryTraceableToCallSiteAndHandler`() {
        val handler = TruncatingStubHandler()
        val offered = 128
        val remainder = handler.insertItem(0, stack(offered), false)
        val event = ConservationEvent(
            callSite = "truncating-stub",
            handlerClassName = handler.javaClass.name,
            slot = 0,
            simulate = false,
            offered = offered,
            before = 0,
            after = handler.stored,
            remainderCount = stackCount(remainder),
        )

        ConservationReportWriter.write(event)

        assertFalse(event.balanced)
        val line = parseLines().single()
        requireSchema(line)
        assertEquals(64, line.get("storedDelta").getAsInt())
        assertEquals(0, line.get("remainderCount").getAsInt())
        assertFalse(line.get("balanced").getAsBoolean())

        val summary = ConservationReportWriter.summary()
        assertEquals(1, summary.unbalancedRealEvents)
        val entry = summary.unbalanced.single()
        assertEquals(handler.javaClass.name, entry.handlerClassName)
        assertEquals("truncating-stub", entry.callSite)
        assertEquals(1, entry.eventCount)
    }

    @Test
    fun `simulateEvent_shouldBeWrittenButExcludedFromUnbalancedSummary`() {
        val event = ConservationEvent(
            callSite = "simulate-stub",
            handlerClassName = "simulate-handler",
            slot = 2,
            simulate = true,
            offered = 128,
            before = 0,
            after = 0,
            remainderCount = 128,
        )

        ConservationReportWriter.write(event)

        val line = parseLines().single()
        requireSchema(line)
        assertTrue(line.get("simulate").getAsBoolean())
        // simulate 事件按公式给出 balanced，但不改变状态、不计入不平衡汇总
        assertTrue(event.balanced)
        val summary = ConservationReportWriter.summary()
        assertEquals(1, summary.totalEvents)
        assertEquals(1, summary.simulateEvents)
        assertEquals(0, summary.unbalancedRealEvents)
        assertTrue(summary.unbalanced.isEmpty())
    }

    @Test
    fun `balancedEvent_shouldBeWrittenButExcludedFromUnbalancedSummary`() {
        val event = ConservationEvent(
            callSite = "balanced-stub",
            handlerClassName = "balanced-handler",
            slot = 0,
            simulate = false,
            offered = 128,
            before = 0,
            after = 128,
            remainderCount = 0,
        )

        ConservationReportWriter.write(event)

        val line = parseLines().single()
        requireSchema(line)
        assertFalse(line.get("simulate").getAsBoolean())
        assertTrue(line.get("balanced").getAsBoolean())
        val summary = ConservationReportWriter.summary()
        assertEquals(1, summary.totalEvents)
        assertEquals(0, summary.simulateEvents)
        assertEquals(0, summary.unbalancedRealEvents)
        assertTrue(summary.unbalanced.isEmpty())
    }

    @Test
    fun `summaryFile_shouldListUnbalancedClassesAndCallSitesAndMatchInMemorySummary`() {
        ConservationReportWriter.write(
            ConservationEvent("call-A", "example.HandlerOne", 0, simulate = false, offered = 100, before = 0, after = 64, remainderCount = 0),
        )
        ConservationReportWriter.write(
            ConservationEvent("call-A", "example.HandlerOne", 1, simulate = false, offered = 100, before = 0, after = 64, remainderCount = 0),
        )
        ConservationReportWriter.write(
            ConservationEvent("call-B", "example.HandlerTwo", 0, simulate = false, offered = 100, before = 0, after = 64, remainderCount = 0),
        )
        ConservationReportWriter.write(
            ConservationEvent("call-C", "example.HandlerOne", 0, simulate = true, offered = 100, before = 0, after = 0, remainderCount = 100),
        )

        val inMemory = ConservationReportWriter.summary()
        assertEquals(4, inMemory.totalEvents)
        assertEquals(1, inMemory.simulateEvents)
        assertEquals(3, inMemory.unbalancedRealEvents)
        assertEquals(2, inMemory.unbalanced.size)
        assertEquals("example.HandlerOne", inMemory.unbalanced[0].handlerClassName)
        assertEquals("call-A", inMemory.unbalanced[0].callSite)
        assertEquals(2, inMemory.unbalanced[0].eventCount)
        assertEquals("example.HandlerTwo", inMemory.unbalanced[1].handlerClassName)
        assertEquals("call-B", inMemory.unbalanced[1].callSite)
        assertEquals(1, inMemory.unbalanced[1].eventCount)

        val parsed = JsonParser().parse(summaryFile.readText()).getAsJsonObject()
        assertEquals(ConservationReportWriter.SCHEMA_VERSION, parsed.get("schemaVersion").getAsInt())
        assertEquals("conservation-unbalanced-summary", parsed.get("summaryKind").getAsString())
        assertEquals(4, parsed.get("totalEvents").getAsInt())
        assertEquals(1, parsed.get("simulateEvents").getAsInt())
        assertEquals(3, parsed.get("unbalancedRealEvents").getAsInt())
        val parsedEntries = parsed.getAsJsonArray("unbalanced")
        assertEquals(2, parsedEntries.size())
        assertEquals("example.HandlerOne", parsedEntries[0].getAsJsonObject().get("handlerClassName").getAsString())
        assertEquals("call-A", parsedEntries[0].getAsJsonObject().get("callSite").getAsString())
        assertEquals(2, parsedEntries[0].getAsJsonObject().get("eventCount").getAsInt())
    }

    @Test
    fun `unbalancedJsonlEntry_shouldTraceBackToCallSiteAndHandlerClassName`() {
        val handler = TruncatingStubHandler()
        val remainder = handler.insertItem(0, stack(128), false)
        ConservationReportWriter.write(
            ConservationEvent(
                callSite = "trace-me",
                handlerClassName = handler.javaClass.name,
                slot = 3,
                simulate = false,
                offered = 128,
                before = 0,
                after = handler.stored,
                remainderCount = stackCount(remainder),
            ),
        )

        val unbalancedLines = parseLines().filter { !it.get("simulate").getAsBoolean() && !it.get("balanced").getAsBoolean() }
        val line = unbalancedLines.single()
        assertEquals("trace-me", line.get("callSite").getAsString())
        assertEquals(handler.javaClass.name, line.get("handlerClassName").getAsString())
        assertEquals(3, line.get("slot").getAsInt())
    }

    @Test
    fun `schemaValidation_everyLineShouldContainAllRequiredFieldsWithCorrectTypes`() {
        ConservationReportWriter.write(
            ConservationEvent("schema-a", "schema-handler", 0, simulate = false, offered = 100, before = 0, after = 100, remainderCount = 0),
        )
        ConservationReportWriter.write(
            ConservationEvent("schema-b", "schema-handler", 1, simulate = true, offered = 100, before = 0, after = 0, remainderCount = 100),
        )

        val lines = parseLines()
        assertEquals(2, lines.size)
        lines.forEach { line ->
            requireSchema(line)
            assertTrue(line.get("schemaVersion").isJsonPrimitive && line.get("schemaVersion").getAsJsonPrimitive().isNumber)
            assertTrue(line.get("callSite").isJsonPrimitive && line.get("callSite").getAsJsonPrimitive().isString)
            assertTrue(line.get("handlerClassName").isJsonPrimitive && line.get("handlerClassName").getAsJsonPrimitive().isString)
            listOf("slot", "offered", "before", "after", "storedDelta", "remainderCount").forEach { field ->
                assertTrue(line.get(field).isJsonPrimitive && line.get(field).getAsJsonPrimitive().isNumber, "$field 必须是数值")
            }
            listOf("simulate", "balanced").forEach { field ->
                assertTrue(line.get(field).isJsonPrimitive && line.get(field).getAsJsonPrimitive().isBoolean, "$field 必须是布尔值")
            }
        }
    }

    @Test
    fun `auditor_whenEnabled_shouldForwardEveryEventToReport`() {
        ConservationAuditor.setEnabledForTesting(true)
        try {
            ConservationAuditor.audit(
                ConservationEvent("auditor-wiring", "balanced-handler", 0, simulate = false, offered = 100, before = 0, after = 100, remainderCount = 0),
            )
            ConservationAuditor.audit(
                ConservationEvent("auditor-wiring", "unbalanced-handler", 0, simulate = false, offered = 100, before = 0, after = 64, remainderCount = 0),
            )
            ConservationAuditor.audit(
                ConservationEvent("auditor-wiring", "simulate-handler", 0, simulate = true, offered = 100, before = 0, after = 0, remainderCount = 100),
            )

            val lines = parseLines()
            assertEquals(3, lines.size, "开启时平衡/不平衡/模拟事件都必须写入报告，不得丢弃")
            assertEquals(1, ConservationReportWriter.summary().unbalancedRealEvents)
        } finally {
            ConservationAuditor.setEnabledForTesting(false)
        }
    }

    @Test
    fun `auditor_whenDisabled_shouldNotCreateAnyReportFile`() {
        ConservationAuditor.setEnabledForTesting(false)
        try {
            ConservationAuditor.audit(
                ConservationEvent("disabled", "unbalanced-handler", 0, simulate = false, offered = 100, before = 0, after = 64, remainderCount = 0),
            )

            assertFalse(reportFile.exists(), "关闭态不得创建报告文件（零 I/O）")
            assertFalse(summaryFile.exists(), "关闭态不得创建汇总文件")
        } finally {
            ConservationAuditor.setEnabledForTesting(false)
        }
    }

    private fun parseLines(): List<JsonObject> = reportFile.readLines().map { JsonParser().parse(it).getAsJsonObject() }

    /** schema 校验：每行必须恰含 11 个必需字段，不多不少（字段增删必须 bump schemaVersion）。 */
    private fun requireSchema(line: JsonObject) {
        val required = setOf(
            "schemaVersion",
            "callSite",
            "handlerClassName",
            "slot",
            "simulate",
            "offered",
            "before",
            "after",
            "storedDelta",
            "remainderCount",
            "balanced",
        )
        assertEquals(required, line.entrySet().map { it.key }.toSet(), "JSONL 行必须恰含全部 schema 字段")
    }

    /** 守恒 stub：容量 256，按真实容量接受并返回正确余量（守恒）。 */
    private class ConservingStubHandler : IItemHandler {
        var stored: Int = 0
            private set

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

    /** 截断 stub：容量 64，超过部分直接丢弃并返回空余量（不平衡，用于守恒告警与报告）。 */
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

    companion object {
        private lateinit var reportItem: Item

        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            Bootstrap.register()
            reportItem = Item()
                .setMaxStackSize(256)
                .setRegistryName(ResourceLocation("stackupup_test", "report_item"))
        }

        private fun stack(count: Int): ItemStack {
            val stack = ItemStack(reportItem, 0)
            stack.count = count
            return stack
        }

        private fun stackCount(stack: ItemStack): Int = if (stack.isEmpty) 0 else stack.count
    }
}
