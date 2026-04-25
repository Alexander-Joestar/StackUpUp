package io.alexjoest.stackupup

import io.alexjoest.stackupup.Constants.VANILLA_STACK_LIMIT
import net.minecraft.item.ItemStack
import io.alexjoest.stackupup.limit.StackContextResolver
import io.alexjoest.stackupup.limit.StackIdentity
import io.alexjoest.stackupup.limit.RuleRuntime
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.Random

object StackLimitHooks {
    private const val VANILLA_STACK_LIMIT: Int = Constants.VANILLA_STACK_LIMIT
    private val inventoryWriteContext: ThreadLocal<ArrayDeque<ItemStack>> = ThreadLocal.withInitial(::ArrayDeque)
    private val itemLimitResolutionMarkers: ThreadLocal<IdentityHashMap<ItemStack, Int>> =
        ThreadLocal.withInitial(::IdentityHashMap)
    private val originalBaselineBypassDepth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

    @JvmField
    val RANDOM: Random = Random()

    @JvmStatic
    fun getCompatibilityStackSize(): Int = StackUpUpConfig.maxStackSize

    @JvmStatic
    @Deprecated(
        message = "请改用 getCompatibilityStackSize，旧名称容易与精确物品上限混淆。",
        replaceWith = ReplaceWith("getCompatibilityStackSize()")
    )
    fun getMaxStackSize(): Int = getCompatibilityStackSize()

    @JvmStatic
    fun applyDynamicStackLimit(
        itemId: String,
        modId: String,
        meta: Int,
        type: String,
        baseLimit: Int,
        oreNames: Set<String>
    ): Int {
        return RuleRuntime.limitService().resolve(StackIdentity(itemId, modId, meta, type), baseLimit, oreNames)
    }

    @JvmStatic
    fun applyDynamicStackLimit(stack: ItemStack, baseLimit: Int): Int {
        val limitService = RuleRuntime.limitService()
        if (!limitService.hasRules()) {
            return resolveOriginalBaseline(stack, baseLimit)
        }

        val originalBaseline = resolveOriginalBaseline(stack, baseLimit)
        val context = StackContextResolver.fromStack(
            stack = stack,
            baseLimit = originalBaseline,
            includeOreNames = limitService.needsOreNames()
        ) ?: return originalBaseline
        return limitService.resolve(context)
    }

    @JvmStatic
    fun shouldBypassDynamicItemRules(): Boolean = originalBaselineBypassDepth.get() > 0

    @JvmStatic
    fun resolveOriginalBaseline(stack: ItemStack, fallbackLimit: Int = VANILLA_STACK_LIMIT): Int {
        if (stack.isEmpty) {
            return fallbackLimit
        }

        return withOriginalBaselineBypass {
            stack.item.getItemStackLimit(stack)
        }
    }

    private inline fun <T> withOriginalBaselineBypass(block: () -> T): T {
        val depth = originalBaselineBypassDepth.get()
        originalBaselineBypassDepth.set(depth + 1)
        try {
            return block()
        } finally {
            if (depth == 0) {
                originalBaselineBypassDepth.remove()
            } else {
                originalBaselineBypassDepth.set(depth)
            }
        }
    }

    @JvmStatic
    fun markResolvedItemLimit(stack: ItemStack, resolvedLimit: Int): Int {
        itemLimitResolutionMarkers.get()[stack] = resolvedLimit
        return resolvedLimit
    }

    @JvmStatic
    fun shouldSkipNestedItemStackLimit(stack: ItemStack, currentLimit: Int): Boolean {
        val markers = itemLimitResolutionMarkers.get()
        val markedLimit = markers.remove(stack) ?: return false
        if (markers.isEmpty()) {
            itemLimitResolutionMarkers.remove()
        }
        return markedLimit == currentLimit
    }

    @JvmStatic
    fun resolveCreativeStackLimit(stack: ItemStack): Int {
        if (stack.isEmpty) {
            return VANILLA_STACK_LIMIT
        }
        // 创造模式发包必须看“该物品此刻的真实动态上限”，
        // 否则客户端能拿到的大堆叠会在服务端被当成非法包，形成幽灵物品。
        return stack.maxStackSize
    }

    @JvmStatic
    fun isValidCreativeStackPacket(stack: ItemStack): Boolean {
        if (stack.isEmpty) {
            return true
        }
        return stack.metadata >= 0 && stack.count <= resolveCreativeStackLimit(stack)
    }

    @JvmStatic
    fun resolveDynamicSlotLimit(stack: ItemStack, slotLimit: Int): Int {
        if (stack.isEmpty) {
            return slotLimit
        }

        val resolvedItemLimit = stack.maxStackSize
        if (slotLimit >= resolvedItemLimit || !shouldTreatAsDefaultSlotLimit(slotLimit)) {
            return slotLimit
        }

        return resolvedItemLimit
    }

    @JvmStatic
    fun resolveItemHandlerSlotLimit(stack: ItemStack, simulatedLimit: Int, slotLimit: Int): Int {
        if (stack.isEmpty) {
            return simulatedLimit
        }
        if (slotLimit <= 0) {
            return simulatedLimit
        }

        val currentItemLimit = stack.maxStackSize
        return when {
            slotLimit < currentItemLimit                                                     -> slotLimit
            currentItemLimit < slotLimit && !shouldTreatAsDefaultItemLimit(currentItemLimit) -> currentItemLimit
            else                                                                             -> slotLimit
        }
    }

    @JvmStatic
    fun shouldTreatAsDefaultSlotLimit(slotLimit: Int): Boolean =
        slotLimit == VANILLA_STACK_LIMIT


    @JvmStatic
    fun shouldTreatAsDefaultItemLimit(itemLimit: Int): Boolean =
        itemLimit == VANILLA_STACK_LIMIT

    @JvmStatic
    fun beginInventoryWrite(stack: ItemStack) {
        if (stack.isEmpty) {
            return
        }
        inventoryWriteContext.get().addLast(stack)
    }

    @JvmStatic
    fun endInventoryWrite() {
        val context = inventoryWriteContext.get()
        if (context.isNotEmpty()) {
            context.removeLast()
        }
        if (context.isEmpty()) {
            inventoryWriteContext.remove()
        }
    }

    @JvmStatic
    fun resolveInventoryWriteLimit(inventoryLimit: Int): Int {
        val currentStack = inventoryWriteContext.get().peekLast() ?: return inventoryLimit
        return resolveInventoryClampLimit(currentStack, inventoryLimit)
    }

    @JvmStatic
    fun resolveInventoryClampLimit(stack: ItemStack, inventoryLimit: Int): Int {
        if (stack.isEmpty) {
            return inventoryLimit
        }

        val itemLimit = stack.maxStackSize
        return if (shouldTreatAsDefaultSlotLimit(inventoryLimit)) {
            itemLimit
        } else {
            minOf(inventoryLimit, itemLimit)
        }
    }

    @JvmStatic
    fun expandDefaultExtractLimit(requestedSize: Long, maxItemSize: Long): Long =
        if (requestedSize == VANILLA_STACK_LIMIT.toLong() && maxItemSize > VANILLA_STACK_LIMIT.toLong()) {
            maxItemSize
        } else {
            minOf(requestedSize, maxItemSize)
        }
}
