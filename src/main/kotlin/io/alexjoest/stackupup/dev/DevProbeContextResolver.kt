package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.limit.StackContextResolver
import net.minecraft.item.ItemStack

/**
 * 按当前规则快照的运行时需求解析开发探针上下文。
 */
internal fun resolveDevProbeContext(stack: ItemStack, baseLimit: Int): StackContext? =
    StackContextResolver.fromStack(
        stack = stack,
        baseLimit = baseLimit,
        requirements = RuleRuntime.limitService().contextRequirements(),
    )
