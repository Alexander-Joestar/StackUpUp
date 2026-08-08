package io.alexjoest.stackupup.audit

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 守恒审计器：只读观察与告警，没有任何写入业务库存的路径。
 *
 * - 默认关闭：系统属性 `stackupup.audit.conservation=true` 才开启；
 * - 开启后对每次真实投喂（simulate=false）按守恒公式判定，不平衡时产出结构化 WARN；
 * - 只读审计：不修正不平衡结果、不回填、不重试、不补偿，也不强制截断余量。
 */
object ConservationAuditor {
    internal const val ENABLE_PROPERTY: String = "stackupup.audit.conservation"
    private const val MAX_RECORDED_WARNINGS: Int = 1024

    private val logger: Logger = LogManager.getLogger("stackupup.audit")
    private val recordedWarningsList = CopyOnWriteArrayList<String>()

    /** 是否开启守恒审计（默认关闭）。 */
    @JvmStatic
    fun enabled(): Boolean = System.getProperty(ENABLE_PROPERTY) == "true"

    /**
     * 对一次投喂做守恒判定。未开启、simulate=true 或守恒时产出 null（不告警、不记录）；
     * 不平衡且开启时产出结构化告警文本，记录 WARN 并加入机器可读记录。
     */
    @JvmStatic
    fun audit(event: ConservationEvent): ConservationAuditOutcome {
        if (!enabled() || event.simulate || event.balanced) {
            return ConservationAuditOutcome(warning = null)
        }
        val warning = buildString(160) {
            append("守恒审计[不平衡] handler=")
            append(event.handlerClassName)
            append(" slot=")
            append(event.slot)
            append(" 调用点=")
            append(event.callSite)
            append(" offered=")
            append(event.offered)
            append(" 落库=")
            append(event.storedDelta)
            append(" 余量=")
            append(event.remainderCount)
            append(" before=")
            append(event.before)
            append(" after=")
            append(event.after)
        }
        record(warning)
        logger.warn(warning)
        return ConservationAuditOutcome(warning = warning)
    }

    private fun record(warning: String) {
        recordedWarningsList += warning
        while (recordedWarningsList.size > MAX_RECORDED_WARNINGS) {
            recordedWarningsList.removeAt(0)
        }
    }

    /** 最近产出的告警文本（有界机器可读记录，仅供诊断与测试，不参与业务状态）。 */
    internal fun recordedWarnings(): List<String> = recordedWarningsList.toList()

    /** 清空告警记录，仅用于测试隔离。 */
    internal fun clearRecordedWarnings() {
        recordedWarningsList.clear()
    }
}

/**
 * 守恒审计结果：warning 为 null 表示本次未告警。
 */
data class ConservationAuditOutcome(val warning: String?)
