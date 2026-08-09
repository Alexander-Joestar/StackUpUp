package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.StackLimitHooks
import io.alexjoest.stackupup.StackUpUp
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.limit.StackContextResolver
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.items.ItemStackHandler
import java.io.File

/** 自动化报告与失败标记相对服务端运行目录（默认 `run/`）。 */
private const val AUTOMATION_LOG_DIRECTORY = "logs"
private const val AUTOMATION_REPORT_FILE_NAME = "autotest-report.txt"

/** 边界探针自带的 vanilla 规则：只依赖 minecraft:stick，不依赖 GT 加载。 */
private const val BOUNDARY_PROBE_RULE = "item = minecraft:stick -> 1024"

/**
 * 服务端自动探针。
 */
object DevAutomationServerDriver {
    fun run(server: MinecraftServer) {
        val report = AutomationReport()
        try {
            if (DevAutomationConfig.runServerMatrix) {
                runMatrix(server, report)
            } else {
                runSingle(server, report)
            }
        } catch (throwable: Throwable) {
            // failFast 抛出的异常统一在此落进报告，再原样抛给桥接层写失败标记。
            report.recordFailure("异常终止：${throwable.message ?: throwable.javaClass.simpleName}")
            throw throwable
        } finally {
            report.writeToDisk()
        }
    }

    private fun runSingle(server: MinecraftServer, report: AutomationReport) {
        when (val injection = DevRuleInjector.ensureInjected(DevAutomationConfig.tempRule)) {
            is DevRuleInjectionResult.Applied -> {
                StackUpUp.logger?.info(
                    "开发自动验收[服务端]：已注入临时规则 `{}`，规则数 {} -> {}。",
                    injection.ruleLine,
                    injection.previousRuleCount,
                    injection.newRuleCount,
                )
            }

            is DevRuleInjectionResult.Failed -> {
                val message = "临时规则注入失败：${injection.errors.joinToString("；")}"
                StackUpUp.logger?.error("开发自动验收[服务端]：{}", message)
                handleFailure(server, report, message)
                return
            }

            DevRuleInjectionResult.Skipped -> Unit
        }

        val target = DevTargetRuntimeResolver.resolve()
        if (target == null) {
            val message = "未找到目标物品 item=${DevAutomationConfig.itemId.ifBlank {
                "<未指定>"
            }} meta=${DevAutomationConfig.itemMeta} ore=${DevAutomationConfig.oreName}。"
            StackUpUp.logger?.error("开发自动验收[服务端]：{}", message)
            handleFailure(server, report, message)
            return
        }

        val probeResult = probeTarget(target)

        StackUpUp.logger?.info(
            "开发自动验收[服务端]：目标 {}@{}，矿辞={}，原版基线={}，规则解析={}，实际上限={}，插槽上限={}，请求数量={}，存入数量={}，剩余数量={}。",
            target.itemId,
            target.meta,
            probeResult.context.oreNames.joinToString(prefix = "[", postfix = "]"),
            probeResult.baseLimit,
            probeResult.resolvedLimit,
            probeResult.actualLimit,
            probeResult.slotLimit,
            DevAutomationConfig.itemCount,
            probeResult.stored.count,
            probeResult.remainder.count,
        )
        report.line("单场景 ${probeSummary(target, probeResult)}")
        if (!probeResult.evaluation.passed) {
            val message = "验证失败：${probeResult.evaluation.reasons.joinToString("；")}"
            StackUpUp.logger?.error("开发自动验收[服务端]：{}", message)
            handleFailure(server, report, message)
            return
        }

        val boundaryResult = probeBoundaryLimit()
        StackUpUp.logger?.info("开发自动验收[服务端]：边界探针 {}。", boundaryResult.summary)
        report.line("边界探针 ${boundaryResult.summary}")
        if (!boundaryResult.evaluation.passed) {
            val message = "边界探针失败：${boundaryResult.evaluation.reasons.joinToString("；")}"
            StackUpUp.logger?.error("开发自动验收[服务端]：{}", message)
            handleFailure(server, report, message)
            return
        }

        StackUpUp.logger?.info("开发自动验收[服务端]：验证通过，目标物品已按规则生效。")
        shutdownIfRequested(server)
    }

    private fun runMatrix(server: MinecraftServer, report: AutomationReport) {
        // 矩阵模式不再依赖外部注入的单场景临时规则，规则由 builtInMatrix 各场景 spec.rule 携带并批量注入。
        val ruleLines = DevAutomationConfig.builtInMatrix.mapNotNull(DevProbeTargetSpec::rule)
        when (val injection = DevRuleInjector.ensureInjected(ruleLines)) {
            is DevRuleInjectionResult.Applied -> {
                StackUpUp.logger?.info(
                    "开发自动验收[服务端]：已注入矩阵规则 {} 条，规则数 {} -> {}。",
                    ruleLines.size,
                    injection.previousRuleCount,
                    injection.newRuleCount,
                )
            }

            is DevRuleInjectionResult.Failed -> {
                val message = "矩阵规则注入失败：${injection.errors.joinToString("；")}"
                StackUpUp.logger?.error("开发自动验收[服务端]：{}", message)
                handleFailure(server, report, message)
                return
            }

            DevRuleInjectionResult.Skipped -> Unit
        }

        val failures = ArrayList<String>()
        var unresolvedBuiltInTargets = 0

        for (spec in DevAutomationConfig.builtInMatrix) {
            val result = evaluateTarget(spec)
            if (result.passed) {
                StackUpUp.logger?.info("开发自动验收[服务端]：矩阵样例 {} 通过。{}", spec.name, result.summary)
                report.line("矩阵 ${spec.name}: 通过。${result.summary}")
                continue
            }

            if (result.summary == UNRESOLVED_TARGET_SUMMARY) {
                unresolvedBuiltInTargets++
                report.line("矩阵 ${spec.name}: 跳过，未解析到目标物品。")
                continue
            }

            failures += "${spec.name}: ${result.summary}"
            report.recordFailure("矩阵 ${spec.name}: 失败。${result.summary}")
            StackUpUp.logger?.error("开发自动验收[服务端]：矩阵样例 {} 失败。{}", spec.name, result.summary)
        }

        val builtInMatrixFailure = unresolvedBuiltInMatrixFailure(
            unresolvedCount = unresolvedBuiltInTargets,
            totalCount = DevAutomationConfig.builtInMatrix.size,
            gregTechLoaded = Loader.isModLoaded("gregtech"),
        )
        if (unresolvedBuiltInTargets == DevAutomationConfig.builtInMatrix.size && builtInMatrixFailure == null) {
            StackUpUp.logger?.warn("开发自动验收[服务端]：内建 GT/metadata 矩阵未解析到目标物品，已跳过该专项回归。")
            report.line("矩阵 内建专项: 跳过，GT 未加载，全部目标未解析。")
        } else if (builtInMatrixFailure != null) {
            failures += builtInMatrixFailure
            report.recordFailure("矩阵 内建专项: 失败。$builtInMatrixFailure")
        }

        val probeFailures = DevCompatProbeRunner.run(server)
        if (probeFailures.isEmpty()) {
            report.line("兼容探针: 无失败。")
        } else {
            report.recordFailure("兼容探针: 失败 ${probeFailures.size} 个。")
            probeFailures.forEach { report.line("兼容探针: $it") }
        }
        failures += probeFailures

        if (failures.isNotEmpty() && DevAutomationConfig.failFast) {
            throw IllegalStateException("服务端自动矩阵回归失败：${failures.joinToString(" | ")}")
        }

        shutdownIfRequested(server)
    }

    private fun evaluateTarget(spec: DevProbeTargetSpec): DevProbeRunResult {
        val target = DevTargetRuntimeResolver.resolve(spec)
            ?: return DevProbeRunResult.failed(UNRESOLVED_TARGET_SUMMARY)
        val probeResult = probeTarget(target)

        return DevProbeRunResult(
            passed = probeResult.evaluation.passed,
            summary = probeSummary(target, probeResult),
        )
    }

    /**
     * 边界探针：注入自包含 vanilla 规则后，验证 `insertItem(N)` 全部存入（remainder=0）、
     * 追加 `insertItem(1)` 被拒（remainder=1），N 取规则解析值而非固定 128。
     */
    private fun probeBoundaryLimit(): BoundaryProbeResult {
        when (val injection = DevRuleInjector.ensureInjected(BOUNDARY_PROBE_RULE)) {
            is DevRuleInjectionResult.Failed ->
                return BoundaryProbeResult.failed("边界探针规则注入失败：${injection.errors.joinToString("；")}")

            DevRuleInjectionResult.Skipped,
            is DevRuleInjectionResult.Applied,
            -> Unit
        }

        val stick = ItemStack(Items.STICK)
        val baseLimit = StackLimitHooks.resolveOriginalBaseline(stick)
        val context = StackContextResolver.fromStack(
            stack = stick,
            baseLimit = baseLimit,
            requirements = RuleRuntime.limitService().contextRequirements(),
        )
            ?: return BoundaryProbeResult.failed("minecraft:stick 无法解析为统一堆叠上下文。")
        val resolvedLimit = RuleRuntime.limitService().resolve(context)

        val handler = ItemStackHandler(1)
        val insertNStack = stick.copy().also { it.count = resolvedLimit }
        val remainderAfterN = handler.insertItem(0, insertNStack, false)
        val storedAfterN = handler.getStackInSlot(0)
        val insertOneStack = ItemStack(Items.STICK, 1)
        val remainderAfterOne = handler.insertItem(0, insertOneStack, false)
        val storedAfterOne = handler.getStackInSlot(0).count
        val actualLimit = stick.maxStackSize
        return BoundaryProbeResult(
            rule = BOUNDARY_PROBE_RULE,
            resolvedLimit = resolvedLimit,
            actualLimit = actualLimit,
            stored = storedAfterN,
            remainder = remainderAfterOne,
            evaluation = evaluateBoundaryProbe(
                resolvedLimit = resolvedLimit,
                actualLimit = actualLimit,
                storedAfterN = storedAfterN.count,
                remainderAfterN = remainderAfterN.count,
                storedAfterOne = storedAfterOne,
                remainderAfterOne = remainderAfterOne.count,
            ),
        )
    }

    private fun probeTarget(target: ResolvedDevTarget): ProbedTarget {
        val probeStack = target.stack.copy()
        val baseLimit = StackLimitHooks.resolveOriginalBaseline(probeStack)
        val context = StackContextResolver.fromStack(
            stack = probeStack,
            baseLimit = baseLimit,
            requirements = RuleRuntime.limitService().contextRequirements(),
        )
            ?: error("开发自动验收[服务端]：目标物品无法解析为统一堆叠上下文。")
        val resolvedLimit = RuleRuntime.limitService().resolve(context)
        val insertionStack = probeStack.copy().also { it.count = DevAutomationConfig.itemCount }
        val handler = ItemStackHandler(1)
        val remainder = handler.insertItem(0, insertionStack, false)
        val stored = handler.getStackInSlot(0)
        val actualLimit = probeStack.maxStackSize
        val slotLimit = handler.getSlotLimit(0)
        val evaluation = evaluateProbeResult(
            requestedCount = DevAutomationConfig.itemCount,
            resolvedLimit = resolvedLimit,
            actualLimit = actualLimit,
            slotLimit = slotLimit,
            storedCount = stored.count,
            remainderCount = remainder.count,
        )
        return ProbedTarget(
            context = context,
            baseLimit = baseLimit,
            resolvedLimit = resolvedLimit,
            actualLimit = actualLimit,
            slotLimit = slotLimit,
            stored = stored,
            remainder = remainder,
            evaluation = evaluation,
        )
    }

    private fun handleFailure(server: MinecraftServer, report: AutomationReport, message: String) {
        if (DevAutomationConfig.failFast) {
            throw IllegalStateException(message)
        }
        report.recordFailure(message)
        shutdownIfRequested(server)
    }

    private fun shutdownIfRequested(server: MinecraftServer) {
        if (!DevAutomationConfig.autoShutdown) {
            return
        }

        StackUpUp.logger?.info("开发自动验收[服务端]：探针结束，准备自动停服。")
        server.initiateShutdown()
    }

    private fun probeSummary(target: ResolvedDevTarget, probeResult: ProbedTarget): String = buildString {
        append("目标=${target.itemId}@${target.meta}")
        append(" 矿辞=${probeResult.context.oreNames.joinToString(prefix = "[", postfix = "]")}")
        append(" 原版基线=${probeResult.baseLimit}")
        append(" 解析=${probeResult.resolvedLimit}")
        append(" 实际=${probeResult.actualLimit}")
        append(" 插槽=${probeResult.slotLimit}")
        append(" 存入=${probeResult.stored.count}")
        append(" 剩余=${probeResult.remainder.count}")
        if (probeResult.evaluation.reasons.isNotEmpty()) {
            append(" 原因=${probeResult.evaluation.reasons.joinToString("；")}")
        }
    }

    private const val UNRESOLVED_TARGET_SUMMARY: String = "未解析到目标物品。"
}

internal fun unresolvedBuiltInMatrixFailure(unresolvedCount: Int, totalCount: Int, gregTechLoaded: Boolean): String? {
    if (unresolvedCount <= 0 || totalCount <= 0) {
        return null
    }
    if (unresolvedCount < totalCount) {
        return "built_in_matrix: unresolved=$unresolvedCount"
    }
    return if (gregTechLoaded) {
        "built_in_matrix: all targets unresolved while gregtech is loaded"
    } else {
        null
    }
}

internal fun evaluateProbeResult(
    requestedCount: Int,
    resolvedLimit: Int,
    actualLimit: Int,
    slotLimit: Int,
    storedCount: Int,
    remainderCount: Int,
): DevProbeEvaluation {
    val expectedStoredCount = minOf(requestedCount, actualLimit, slotLimit)
    val expectedRemainderCount = requestedCount - expectedStoredCount
    val reasons = buildList {
        if (resolvedLimit <= 64) {
            add("规则解析后的堆叠上限仍未突破 64。")
        }
        if (actualLimit != resolvedLimit) {
            add("目标物品的实际上限 $actualLimit 与规则解析结果 $resolvedLimit 不一致。")
        }
        if (actualLimit <= 64) {
            add("目标物品的实际上限仍未突破 64。")
        }
        if (storedCount != expectedStoredCount) {
            add("请求插入 $requestedCount 个物品时，期望存入 $expectedStoredCount 个，实际仅存入 $storedCount 个。")
        }
        if (remainderCount != expectedRemainderCount) {
            add("插入后期望剩余 $expectedRemainderCount 个物品，实际剩余 $remainderCount 个。")
        }
    }

    return DevProbeEvaluation(
        passed = reasons.isEmpty(),
        reasons = reasons,
    )
}

/**
 * 边界探针求值：`insertItem(N)` 必须全部存入（remainder=0、stored=N），
 * 追加 `insertItem(1)` 必须被拒（remainder=1、存量不变），N 为规则解析值。
 */
internal fun evaluateBoundaryProbe(
    resolvedLimit: Int,
    actualLimit: Int,
    storedAfterN: Int,
    remainderAfterN: Int,
    storedAfterOne: Int,
    remainderAfterOne: Int,
): DevProbeEvaluation {
    val reasons = buildList {
        if (resolvedLimit <= 64) {
            add("边界探针规则未生效，解析上限仍为 $resolvedLimit。")
        }
        if (actualLimit != resolvedLimit) {
            add("边界探针物品实际上限 $actualLimit 与规则解析结果 $resolvedLimit 不一致。")
        }
        if (storedAfterN != resolvedLimit) {
            add("insertItem($resolvedLimit) 期望全部存入 $resolvedLimit 个，实际存入 $storedAfterN 个。")
        }
        if (remainderAfterN != 0) {
            add("insertItem($resolvedLimit) 期望 remainder=0，实际剩余 $remainderAfterN 个。")
        }
        if (storedAfterOne != resolvedLimit) {
            add("追加 insertItem(1) 后期望存量保持 $resolvedLimit 个，实际 $storedAfterOne 个。")
        }
        if (remainderAfterOne != 1) {
            add("追加 insertItem(1) 期望被拒并返回 remainder=1，实际剩余 $remainderAfterOne 个。")
        }
    }

    return DevProbeEvaluation(
        passed = reasons.isEmpty(),
        reasons = reasons,
    )
}

/**
 * 服务端自动化落盘报告。
 *
 * 报告文件相对服务端运行目录（默认 `run/`）写入；最终状态由是否记录过失败决定。
 */
internal class AutomationReport {
    private val lines = ArrayList<String>()
    var failed: Boolean = false
        private set

    fun line(text: String) {
        lines += text
    }

    fun recordFailure(text: String) {
        failed = true
        lines += text
    }

    fun writeToDisk() {
        runCatching {
            val file = File(AUTOMATION_LOG_DIRECTORY, AUTOMATION_REPORT_FILE_NAME)
            file.parentFile?.mkdirs()
            file.writeText(renderAutomationReport(lines, failed), Charsets.UTF_8)
        }.onFailure { writeFailure ->
            StackUpUp.logger?.error("开发自动验收[服务端]：写入自动化报告失败。", writeFailure)
        }
    }
}

internal fun renderAutomationReport(lines: List<String>, failed: Boolean): String = buildString {
    lines.forEach { append(it).append('\n') }
    append("最终状态: ").append(if (failed) "FAIL" else "PASS").append('\n')
}

private data class DevProbeRunResult(val passed: Boolean, val summary: String) {
    companion object {
        fun failed(summary: String): DevProbeRunResult = DevProbeRunResult(false, summary)
    }
}

internal data class DevProbeEvaluation(val passed: Boolean, val reasons: List<String>)

private data class ProbedTarget(
    val context: StackContext,
    val baseLimit: Int,
    val resolvedLimit: Int,
    val actualLimit: Int,
    val slotLimit: Int,
    val stored: ItemStack,
    val remainder: ItemStack,
    val evaluation: DevProbeEvaluation,
)

private data class BoundaryProbeResult(
    val rule: String,
    val resolvedLimit: Int,
    val actualLimit: Int,
    val stored: ItemStack,
    val remainder: ItemStack,
    val evaluation: DevProbeEvaluation,
) {
    val summary: String
        get() = buildString {
            append("规则=$rule")
            append(" 解析=$resolvedLimit")
            append(" 实际=$actualLimit")
            append(" 存入=${stored.count}")
            append(" 追加剩余=${remainder.count}")
            append(if (evaluation.passed) " 通过" else " 失败")
        }

    companion object {
        fun failed(reason: String): BoundaryProbeResult = BoundaryProbeResult(
            rule = BOUNDARY_PROBE_RULE,
            resolvedLimit = 0,
            actualLimit = 0,
            stored = ItemStack.EMPTY,
            remainder = ItemStack.EMPTY,
            evaluation = DevProbeEvaluation(passed = false, reasons = listOf(reason)),
        )
    }
}
