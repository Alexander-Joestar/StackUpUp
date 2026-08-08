package io.alexjoest.stackupup.audit

import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.FileWriter
import java.io.StringWriter

/**
 * 守恒审计报告器：把审计事件写为版本化 JSONL，并维护不平衡汇总（T12.5）。
 *
 * - 报告路径在类加载时单次读取系统属性 `stackupup.audit.conservation.report`
 *   （默认 `run/logs/stackupup-conservation.jsonl`），运行期不再读取配置；测试经
 *   [setReportFileForTesting] 显式重定向（与 [ConservationAuditor.setEnabledForTesting] 同构）；
 * - 只写原始事件：开启时每条事件追加一行 JSONL 并立即落盘，不丢弃平衡事件、不丢弃 simulate 事件；
 * - 汇总（不平衡类与调用点）在每次事件后增量重写汇总文件，始终反映已写入的全部事件；
 * - 报告器只观察与报告：不做任何写入业务库存的动作，也绝不把告警变成补丁；
 *   写失败直接抛出（Fail Fast），不静默吞错、不回退。
 */
object ConservationReportWriter {
    internal const val REPORT_PATH_PROPERTY: String = "stackupup.audit.conservation.report"
    internal val DEFAULT_REPORT_PATH: String = "run/logs/stackupup-conservation.jsonl"

    /** 报告 schema 版本：字段增删改都必须同步 bump，且是 JSONL/汇总中 schemaVersion 的唯一事实源。 */
    const val SCHEMA_VERSION: Int = 1

    private var reportFile: File = resolveReportFile()
    private var summaryFile: File = resolveSummaryFile(reportFile)

    private var totalEvents: Int = 0
    private var simulateEvents: Int = 0
    private val unbalancedCounts = LinkedHashMap<UnbalancedKey, Int>()

    /**
     * 写一条事件：追加一行 JSONL 并落盘，随后增量更新汇总文件。
     * 线程安全（审计可能从服务端线程与 AE2 限流路径调用）；聚合只在同步块内读写。
     */
    @Synchronized
    @JvmStatic
    fun write(event: ConservationEvent) {
        appendLine(encodeEvent(event))
        record(event)
        rewriteSummary()
    }

    /** 当前汇总快照（不可变；不含任何业务状态，仅供报告与测试）。 */
    @Synchronized
    @JvmStatic
    fun summary(): ConservationReportSummary = buildSummary()

    /** 测试专用：重定向报告与汇总文件，避免测试写入默认 `run/logs` 路径。 */
    internal fun setReportFileForTesting(reportPath: File) {
        reportFile = reportPath
        summaryFile = resolveSummaryFile(reportPath)
    }

    /** 测试专用：清空聚合状态（不删除已写文件）。 */
    internal fun resetForTesting() {
        totalEvents = 0
        simulateEvents = 0
        unbalancedCounts.clear()
    }

    private fun appendLine(line: String) {
        reportFile.parentFile?.mkdirs()
        // 追加写：报告文件按行追加，不覆盖历史事件；写后立即关闭，落盘即完成。
        FileWriter(reportFile, true).use { it.append(line).append('\n') }
    }

    private fun record(event: ConservationEvent) {
        totalEvents++
        if (event.simulate) {
            simulateEvents++
            return
        }
        if (!event.balanced) {
            val key = UnbalancedKey(handlerClassName = event.handlerClassName, callSite = event.callSite)
            unbalancedCounts[key] = (unbalancedCounts[key] ?: 0) + 1
        }
    }

    private fun rewriteSummary() {
        summaryFile.parentFile?.mkdirs()
        FileWriter(summaryFile).use { it.write(encodeSummary(buildSummary())) }
    }

    private fun buildSummary(): ConservationReportSummary {
        val entries = unbalancedCounts.entries
            .map { (key, count) ->
                UnbalancedReportEntry(
                    handlerClassName = key.handlerClassName,
                    callSite = key.callSite,
                    eventCount = count,
                )
            }
            .sortedWith(compareBy({ it.handlerClassName }, { it.callSite }))
        return ConservationReportSummary(
            schemaVersion = SCHEMA_VERSION,
            totalEvents = totalEvents,
            simulateEvents = simulateEvents,
            unbalancedRealEvents = entries.sumOf { it.eventCount },
            unbalanced = entries,
        )
    }

    private fun encodeEvent(event: ConservationEvent): String {
        val buffer = StringWriter()
        JsonWriter(buffer).use { json ->
            json.beginObject()
            json.name("schemaVersion").value(SCHEMA_VERSION)
            json.name("callSite").value(event.callSite)
            json.name("handlerClassName").value(event.handlerClassName)
            json.name("slot").value(event.slot)
            json.name("simulate").value(event.simulate)
            json.name("offered").value(event.offered)
            json.name("before").value(event.before)
            json.name("after").value(event.after)
            json.name("storedDelta").value(event.storedDelta)
            json.name("remainderCount").value(event.remainderCount)
            json.name("balanced").value(event.balanced)
            json.endObject()
        }
        return buffer.toString()
    }

    private fun encodeSummary(summary: ConservationReportSummary): String {
        val buffer = StringWriter()
        JsonWriter(buffer).use { json ->
            json.beginObject()
            json.name("schemaVersion").value(summary.schemaVersion)
            json.name("summaryKind").value(SUMMARY_KIND)
            json.name("totalEvents").value(summary.totalEvents)
            json.name("simulateEvents").value(summary.simulateEvents)
            json.name("unbalancedRealEvents").value(summary.unbalancedRealEvents)
            json.name("unbalanced").beginArray()
            for (entry in summary.unbalanced) {
                json.beginObject()
                json.name("handlerClassName").value(entry.handlerClassName)
                json.name("callSite").value(entry.callSite)
                json.name("eventCount").value(entry.eventCount)
                json.endObject()
            }
            json.endArray()
            json.endObject()
        }
        return buffer.toString()
    }

    private fun resolveReportFile(): File = File(System.getProperty(REPORT_PATH_PROPERTY) ?: DEFAULT_REPORT_PATH)

    private fun resolveSummaryFile(report: File): File {
        val absolute = report.absoluteFile
        return File(absolute.parentFile, absolute.nameWithoutExtension + "-summary.json")
    }

    private data class UnbalancedKey(val handlerClassName: String, val callSite: String)

    private const val SUMMARY_KIND: String = "conservation-unbalanced-summary"
}

/**
 * 守恒审计报告汇总：不平衡类与调用点列表（机器可读，schemaVersion 与事件报告一致）。
 */
data class ConservationReportSummary(
    val schemaVersion: Int,
    val totalEvents: Int,
    val simulateEvents: Int,
    val unbalancedRealEvents: Int,
    val unbalanced: List<UnbalancedReportEntry>,
)

/**
 * 汇总条目：一个（handler 类, 调用点）组合的不平衡真实事件数；每条可回到 JSONL 原始事件。
 */
data class UnbalancedReportEntry(val handlerClassName: String, val callSite: String, val eventCount: Int)
