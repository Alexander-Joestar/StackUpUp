package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.limit.StackContextResolver
import net.minecraft.item.ItemStack

internal fun resolveDevProbeContext(stack: ItemStack, baseLimit: Int): StackContext? =
    StackContextResolver.fromStack(
        stack = stack,
        baseLimit = baseLimit,
        requirements = RuleRuntime.limitService().contextRequirements(),
    )
