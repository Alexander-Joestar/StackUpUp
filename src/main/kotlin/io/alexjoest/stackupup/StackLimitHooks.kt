package io.alexjoest.stackupup

import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.limit.StackIdentity
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
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
    fun getCompatibilityStackSize(): Int = StackUpUpConfig.activeMaxStackSize

    @JvmStatic
    fun applyDynamicStackLimit(itemId: String, modId: String, meta: Int, type: String, baseLimit: Int, oreNames: Set<String>): Int =
        RuleRuntime.limitService().resolve(StackIdentity(itemId, modId, meta, type), baseLimit, oreNames)

    @JvmStatic
    fun applyDynamicStackLimit(stack: ItemStack, baseLimit: Int): Int {
        val limitService = RuleRuntime.limitService()
        if (!limitService.hasRules()) {
            return resolveOriginalBaseline(stack, baseLimit)
        }

        val originalBaseline = resolveOriginalBaseline(stack, baseLimit)
        val registryName = stack.item.registryName ?: return originalBaseline
        val oreNames = if (limitService.needsOreNames()) {
            RuleRuntime.oreDictIndex().getOreNames(stack)
        } else {
            emptySet()
        }
        return limitService.resolve(
            itemId = registryName.toString(),
            modId = registryName.namespace,
            metadata = stack.metadata,
            type = if (stack.item is ItemBlock) "block" else "item",
            baseLimit = originalBaseline,
            oreNames = oreNames,
        )
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
        if (slotLimit >= resolvedItemLimit || slotLimit != VANILLA_STACK_LIMIT) {
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
            slotLimit < currentItemLimit -> slotLimit
            currentItemLimit < slotLimit && currentItemLimit != VANILLA_STACK_LIMIT -> currentItemLimit
            else -> slotLimit
        }
    }

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
        return if (inventoryLimit == VANILLA_STACK_LIMIT) {
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
