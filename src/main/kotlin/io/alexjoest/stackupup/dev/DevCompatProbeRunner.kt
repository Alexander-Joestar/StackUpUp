package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.StackUpUp
import net.minecraft.server.MinecraftServer

object DevCompatProbeRunner {
    private val probes: List<DevCompatProbe> = listOf(
        RefinedStorageGridExtractProbe,
        RefinedStoragePortableGridExtractProbe,
        RefinedStorageStorageMonitorExtractProbe,
        CyclopsCoreSimpleInventoryLimitProbe,
        ColossalChestsInventoryLimitProbe,
        CombinedInvWrapperLimitProbe,
        InvWrapperLimitProbe,
        RangedWrapperLimitProbe,
        SidedInvWrapperLimitProbe,
        SlotItemHandlerLimitProbe,
    )

    internal fun probeIds(): List<String> = probes.map(DevCompatProbe::id)

    internal fun fixedTargetCoverage(): Set<String> = probes.asSequence()
        .filter(DevCompatProbe::isFixedTargetProbe)
        .flatMap { it.coveredClasses.asSequence() }
        .toSet()

    fun run(server: MinecraftServer): List<String> {
        val selectedIds =
            selectRequestedProbeIds(DevAutomationConfig.compatProbeIds, probes.map(DevCompatProbe::id))
        val selectedProbes = probes.filter { it.id in selectedIds }
        if (selectedProbes.isEmpty()) {
            return emptyList()
        }

        val failures = ArrayList<String>()
        for (probe in selectedProbes) {
            when (val availability = evaluateProbeAvailability(probe::isAvailable)) {
                ProbeAvailability.available() -> Unit
                ProbeAvailability.missing() -> {
                    StackUpUp.logger?.info("开发自动验收[兼容探针]：{} 跳过，目标模组未加载。", probe.id)
                    continue
                }
                else -> {
                    val summary = "可用性检查异常：${availability.failureSummary}"
                    failures += "${probe.id}: $summary"
                    StackUpUp.logger?.error("开发自动验收[兼容探针]：{} 失败。{}", probe.id, summary)
                    continue
                }
            }

            val result = runCatching { probe.run(server) }
                .getOrElse { throwable ->
                    DevCompatProbeResult.failed("执行异常：${formatProbeThrowable(throwable)}")
                }

            if (result.passed) {
                StackUpUp.logger?.info("开发自动验收[兼容探针]：{} 通过。{}", probe.id, result.summary)
            } else {
                failures += "${probe.id}: ${result.summary}"
                StackUpUp.logger?.error("开发自动验收[兼容探针]：{} 失败。{}", probe.id, result.summary)
            }
        }

        return failures
    }
}

internal data class ProbeAvailability(val available: Boolean, val failureSummary: String?) {
    companion object {
        fun available(): ProbeAvailability = ProbeAvailability(available = true, failureSummary = null)

        fun missing(): ProbeAvailability = ProbeAvailability(available = false, failureSummary = null)

        fun failed(summary: String): ProbeAvailability = ProbeAvailability(available = false, failureSummary = summary)
    }
}

internal interface DevCompatProbe {
    val id: String
    val isFixedTargetProbe: Boolean
        get() = false
    val primaryTargetClass: String?
        get() = null
    val coveredClasses: Array<String>
        get() = primaryTargetClass?.let { arrayOf(it) } ?: emptyArray()

    fun isAvailable(): Boolean = primaryTargetClass?.let(::hasClass) ?: true

    fun run(server: MinecraftServer): DevCompatProbeResult
}

internal data class DevCompatProbeResult(val passed: Boolean, val summary: String) {
    companion object {
        fun passed(summary: String): DevCompatProbeResult = DevCompatProbeResult(true, summary)

        fun failed(summary: String): DevCompatProbeResult = DevCompatProbeResult(false, summary)
    }
}
