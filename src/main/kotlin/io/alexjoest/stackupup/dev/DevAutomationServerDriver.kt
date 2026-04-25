package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.StackUpUp
import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.limit.StackContextResolver
import io.alexjoest.stackupup.limit.RuleRuntime
import net.minecraft.server.MinecraftServer
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.items.ItemStackHandler

/**
 * 服务端自动探针。
 */
object DevAutomationServerDriver {
    fun run(server: MinecraftServer) {
        if (DevAutomationConfig.runServerMatrix) {
            runMatrix(server)
            return
        }

        when (val injection = DevRuleInjector.ensureInjected(DevAutomationConfig.tempRule)) {
            is DevRuleInjectionResult.Applied -> {
                StackUpUp.logger?.info(
                    "开发自动验收[服务端]：已注入临时规则 `{}`，规则数 {} -> {}。",
                    injection.ruleLine,
                    injection.previousRuleCount,
                    injection.newRuleCount
                )
            }

            is DevRuleInjectionResult.Failed  -> {
                StackUpUp.logger?.error(
                    "开发自动验收[服务端]：临时规则注入失败：{}",
                    injection.errors.joinToString("；")
                )
                shutdownIfRequested(server)
                return
            }

            DevRuleInjectionResult.Skipped    -> Unit
        }

        val target = DevTargetRuntimeResolver.resolve()
        if (target == null) {
            StackUpUp.logger?.error(
                "开发自动验收[服务端]：未找到目标物品 item={} meta={} ore={}。",
                DevAutomationConfig.itemId.ifBlank { "<未指定>" },
                DevAutomationConfig.itemMeta,
                DevAutomationConfig.oreName
            )
            shutdownIfRequested(server)
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
            probeResult.remainder.count
        )
        if (!probeResult.evaluation.passed) {
            val message = probeResult.evaluation.reasons.joinToString("；")
            StackUpUp.logger?.error("开发自动验收[服务端]：验证失败：{}", message)
            if (DevAutomationConfig.failFast) {
                throw IllegalStateException("堆叠突破自动验收失败：$message")
            }
            shutdownIfRequested(server)
            return
        }

        StackUpUp.logger?.info("开发自动验收[服务端]：验证通过，目标物品已按规则生效。")
        shutdownIfRequested(server)
    }

    private fun runMatrix(server: MinecraftServer) {
        val failures = ArrayList<String>()
        var unresolvedBuiltInTargets = 0

        for (spec in DevAutomationConfig.builtInMatrix) {
            val result = evaluateTarget(spec)
            if (result.passed) {
                StackUpUp.logger?.info("开发自动验收[服务端]：矩阵样例 {} 通过。{}", spec.name, result.summary)
                continue
            }

            if (result.summary == UNRESOLVED_TARGET_SUMMARY) {
                unresolvedBuiltInTargets++
                continue
            }

            failures += "${spec.name}: ${result.summary}"
            StackUpUp.logger?.error("开发自动验收[服务端]：矩阵样例 {} 失败。{}", spec.name, result.summary)
        }

        val builtInMatrixFailure = unresolvedBuiltInMatrixFailure(
            unresolvedCount = unresolvedBuiltInTargets,
            totalCount = DevAutomationConfig.builtInMatrix.size,
            gregTechLoaded = Loader.isModLoaded("gregtech")
        )
        if (unresolvedBuiltInTargets == DevAutomationConfig.builtInMatrix.size && builtInMatrixFailure == null) {
            StackUpUp.logger?.warn("开发自动验收[服务端]：内建 GT/metadata 矩阵未解析到目标物品，已跳过该专项回归。")
        } else if (builtInMatrixFailure != null) {
            failures += builtInMatrixFailure
        }

        failures += DevCompatProbeRunner.run(server)

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
            summary = buildString {
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
        )
    }

    private fun probeTarget(target: ResolvedDevTarget): ProbedTarget {
        val probeStack = target.stack.copy()
        val baseLimit = probeStack.item.getItemStackLimit(probeStack)
        val context = StackContextResolver.fromStack(probeStack, baseLimit)
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
            remainderCount = remainder.count
        )
        return ProbedTarget(
            context = context,
            baseLimit = baseLimit,
            resolvedLimit = resolvedLimit,
            actualLimit = actualLimit,
            slotLimit = slotLimit,
            stored = stored,
            remainder = remainder,
            evaluation = evaluation
        )
    }

    private fun shutdownIfRequested(server: MinecraftServer) {
        if (!DevAutomationConfig.autoShutdown) {
            return
        }

        StackUpUp.logger?.info("开发自动验收[服务端]：探针结束，准备自动停服。")
        server.initiateShutdown()
    }

    private const val UNRESOLVED_TARGET_SUMMARY: String = "未解析到目标物品。"
}

internal fun unresolvedBuiltInMatrixFailure(
    unresolvedCount: Int,
    totalCount: Int,
    gregTechLoaded: Boolean
): String? {
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
    remainderCount: Int
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
        reasons = reasons
    )
}

private data class DevProbeRunResult(
    val passed: Boolean,
    val summary: String
) {
    companion object {
        fun failed(summary: String): DevProbeRunResult = DevProbeRunResult(false, summary)
    }
}

internal data class DevProbeEvaluation(
    val passed: Boolean,
    val reasons: List<String>
)

private data class ProbedTarget(
    val context: StackContext,
    val baseLimit: Int,
    val resolvedLimit: Int,
    val actualLimit: Int,
    val slotLimit: Int,
    val stored: net.minecraft.item.ItemStack,
    val remainder: net.minecraft.item.ItemStack,
    val evaluation: DevProbeEvaluation
)

