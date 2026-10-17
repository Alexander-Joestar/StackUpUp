package io.alexjoest.stackupup

import net.minecraft.inventory.InventoryLargeChest
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.FMLCommonHandler
import org.apache.logging.log4j.LogManager
import java.util.concurrent.ConcurrentHashMap

object ContainerInsertHooks {
    private val logger = LogManager.getLogger("stackupup.merge")

    private const val MERGE_LIMIT_TRACE_REPEATS: Int = 3
    private const val MERGE_LIMIT_TRACE_FORMAT: String =
        "mergeLimitTrace side={} logicalSide={} thread={} inventory={} largeChest={} declaredSlotLimit={} " +
            "rawInventoryLimit={} itemMaxStackSize={} stackCount={} resolved={} item={}"
    private const val MERGE_LIMIT_TRACE_SUPPRESSED_FORMAT: String =
        "mergeLimitTrace suppressed further repeats key={} logged={}"

    private val mergeLimitTraceCounts: MutableMap<String, Int> = ConcurrentHashMap()

    @JvmStatic
    fun resolveMergeSlotLimit(slot: Slot, stack: ItemStack, declaredSlotLimit: Int): Int {
        if (!StackUpUpConfig.debug.mergeLimitTrace) return resolveLimit(slot, stack, declaredSlotLimit)
        val resolved = resolveLimit(slot, stack, declaredSlotLimit)
        logMergeLimitTrace(slot, stack, declaredSlotLimit, resolved)
        return resolved
    }

    private fun resolveLimit(slot: Slot, stack: ItemStack, declaredSlotLimit: Int): Int {
        if (StackLimitHooks.isSpecialContainerInventory(slot.inventory)) {
            return declaredSlotLimit
        }
        val rawInventoryLimit = slot.inventory.inventoryStackLimit
        if (rawInventoryLimit <= 0) return 0
        if (stack.isEmpty) {
            val inventoryLimit = StackLimitHooks.resolveInventoryWriteLimit(
                stack,
                slot.inventory,
                rawInventoryLimit,
            )
            if (inventoryLimit <= 0) return 0
            return minOf(declaredSlotLimit, inventoryLimit)
        }

        val itemLimit = stack.maxStackSize
        val inventoryLimit = StackLimitHooks.resolveInventoryWriteLimit(
            stack,
            slot.inventory,
            rawInventoryLimit,
            itemLimit,
        )
        if (inventoryLimit <= 0) return 0

        return if (declaredSlotLimit == Constants.VANILLA_STACK_LIMIT) {
            inventoryLimit
        } else {
            minOf(declaredSlotLimit, inventoryLimit)
        }
    }

    private fun logMergeLimitTrace(slot: Slot, stack: ItemStack, declaredSlotLimit: Int, resolved: Int) {
        val inventory = slot.inventory
        val itemId = if (stack.isEmpty) "<empty>" else (stack.item.registryName?.toString() ?: "<unregistered>")
        val repeatKey = "${inventory.javaClass.name}#$declaredSlotLimit#${stack.maxStackSize}#${stack.count}#$itemId"
        val seen = mergeLimitTraceCounts.merge(repeatKey, 1) { previous, _ -> previous + 1 } ?: 1
        if (seen > MERGE_LIMIT_TRACE_REPEATS) {
            if (seen == MERGE_LIMIT_TRACE_REPEATS + 1) {
                logger.info(MERGE_LIMIT_TRACE_SUPPRESSED_FORMAT, repeatKey, seen - 1)
            }
            return
        }
        logger.info(
            MERGE_LIMIT_TRACE_FORMAT,
            sideName { FMLCommonHandler.instance().effectiveSide.name },
            sideName { FMLCommonHandler.instance().side.name },
            Thread.currentThread().name,
            inventory.javaClass.name,
            inventory is InventoryLargeChest,
            declaredSlotLimit,
            inventory.inventoryStackLimit,
            stack.maxStackSize,
            stack.count,
            resolved,
            itemId,
        )
    }

    // 诊断专用：FMLCommonHandler 在极早期初始化或缺少 FML 环境时不可用，此时记为 UNKNOWN 而不是中断解析。
    private inline fun sideName(resolve: () -> String): String = try {
        resolve()
    } catch (unavailable: Throwable) {
        "UNKNOWN"
    }
}
