package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.StackLimitHooks
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.util.EnumFacing

internal object InvWrapperLimitProbe : DevCompatProbe {
    override val id: String = "inv_wrapper_limit"
    override val primaryTargetClass: String = "net.minecraftforge.items.wrapper.InvWrapper"

    override fun run(server: MinecraftServer): DevCompatProbeResult {
        val inventory = createInventoryProxy()
        // T3 判定（decision-record §3.5）：InvWrapper 是转发 wrapper，getSlotLimit 转发 delegate 的
        // getInventoryStackLimit；探针验证的正是"转发到 delegate 上限"，
        // 预期取代理库存实际广告的 getInventoryStackLimit（固定 64），而非全局 compat 上限。
        val expected = delegateInventoryStackLimit(inventory)
        return verifySingleSlotLimit(primaryTargetClass, expected) { wrapperClass ->
            wrapperClass.getDeclaredConstructor(loadClass("net.minecraft.inventory.IInventory")).newInstance(inventory)
        }
    }
}

internal object CombinedInvWrapperLimitProbe : DevCompatProbe {
    override val id: String = "combined_inv_wrapper_limit"
    override val primaryTargetClass: String = "net.minecraftforge.items.wrapper.CombinedInvWrapper"

    override fun run(server: MinecraftServer): DevCompatProbeResult {
        val handlerClass = loadClass("net.minecraftforge.items.ItemStackHandler")
        val modifiableClass = loadClass("net.minecraftforge.items.IItemHandlerModifiable")
        val handlerArray = java.lang.reflect.Array.newInstance(modifiableClass, 2)
        java.lang.reflect.Array.set(handlerArray, 0, handlerClass.getDeclaredConstructor(Int::class.javaPrimitiveType).newInstance(1))
        java.lang.reflect.Array.set(handlerArray, 1, handlerClass.getDeclaredConstructor(Int::class.javaPrimitiveType).newInstance(1))

        return verifyPairSlotLimit(primaryTargetClass) { wrapperClass ->
            wrapperClass.getDeclaredConstructor(handlerArray.javaClass).newInstance(handlerArray)
        }
    }
}

internal object SidedInvWrapperLimitProbe : DevCompatProbe {
    override val id: String = "sided_inv_wrapper_limit"
    override val primaryTargetClass: String = "net.minecraftforge.items.wrapper.SidedInvWrapper"

    override fun run(server: MinecraftServer): DevCompatProbeResult {
        val inventory = createSidedInventoryProxy()
        // 同 InvWrapperLimitProbe：SidedInvWrapper 是转发 wrapper，getSlotLimit 转发 delegate 的
        // getInventoryStackLimit，预期取代理库存实际广告的上限（固定 64）。
        val expected = delegateInventoryStackLimit(inventory)
        return verifySingleSlotLimit(primaryTargetClass, expected) { wrapperClass ->
            wrapperClass.getDeclaredConstructor(loadClass("net.minecraft.inventory.ISidedInventory"), EnumFacing::class.java)
                .newInstance(inventory, EnumFacing.NORTH)
        }
    }
}

internal object RangedWrapperLimitProbe : DevCompatProbe {
    override val id: String = "ranged_wrapper_limit"
    override val primaryTargetClass: String = "net.minecraftforge.items.wrapper.RangedWrapper"

    override fun run(server: MinecraftServer): DevCompatProbeResult {
        val handlerClass = loadClass("net.minecraftforge.items.ItemStackHandler")
        val handler = handlerClass.getDeclaredConstructor(Int::class.javaPrimitiveType).newInstance(3)
        return verifyPairSlotLimit(primaryTargetClass) { wrapperClass ->
            wrapperClass.getDeclaredConstructor(
                loadClass("net.minecraftforge.items.IItemHandlerModifiable"),
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
                .newInstance(handler, 1, 3)
        }
    }
}

internal object SlotItemHandlerLimitProbe : DevCompatProbe {
    override val id: String = "slot_item_handler_limit"
    override val primaryTargetClass: String = "net.minecraftforge.items.SlotItemHandler"

    override fun run(server: MinecraftServer): DevCompatProbeResult {
        val handlerClass = loadClass("net.minecraftforge.items.ItemStackHandler")
        val handler = handlerClass.getDeclaredConstructor(Int::class.javaPrimitiveType).newInstance(1)
        val slotClass = loadClass(primaryTargetClass)
        val slot = slotClass
            .getDeclaredConstructor(
                loadClass("net.minecraftforge.items.IItemHandler"),
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            .newInstance(handler, 0, 0, 0)

        val expected = StackLimitHooks.getCompatibilityStackSize()
        val slotLimit = findMethod(slotClass, arrayOf("getSlotStackLimit", "func_75219_a")).invoke(slot) as Int
        val itemLimit = findMethod(slotClass, arrayOf("getItemStackLimit", "func_178170_b"), ItemStack::class.java)
            .invoke(slot, ItemStack(Items.STICK, 1)) as Int

        return if (slotLimit == expected && itemLimit == expected) {
            DevCompatProbeResult.passed("槽位上限=$slotLimit 物品上限=$itemLimit")
        } else {
            DevCompatProbeResult.failed("槽位上限=$slotLimit 物品上限=$itemLimit 预期=$expected")
        }
    }
}

private fun verifySingleSlotLimit(className: String, expected: Int, createTarget: (Class<*>) -> Any): DevCompatProbeResult {
    val wrapperClass = loadClass(className)
    val slotLimit = wrapperClass.getMethod("getSlotLimit", Int::class.javaPrimitiveType).invoke(createTarget(wrapperClass), 0) as Int
    return if (slotLimit == expected) {
        DevCompatProbeResult.passed("槽位上限=$slotLimit")
    } else {
        DevCompatProbeResult.failed("槽位上限=$slotLimit 预期=$expected")
    }
}

/** 读取转发 wrapper 的 delegate 库存实际广告的 getInventoryStackLimit（运行期为 SRG 名 func_70297_j_）。 */
private fun delegateInventoryStackLimit(inventory: Any): Int =
    findMethod(loadClass("net.minecraft.inventory.IInventory"), arrayOf("getInventoryStackLimit", "func_70297_j_"))
        .invoke(inventory) as Int

private fun verifyPairSlotLimit(className: String, createTarget: (Class<*>) -> Any): DevCompatProbeResult {
    val wrapperClass = loadClass(className)
    val wrapper = createTarget(wrapperClass)
    val expected = StackLimitHooks.getCompatibilityStackSize()
    val firstLimit = wrapperClass.getMethod("getSlotLimit", Int::class.javaPrimitiveType).invoke(wrapper, 0) as Int
    val secondLimit = wrapperClass.getMethod("getSlotLimit", Int::class.javaPrimitiveType).invoke(wrapper, 1) as Int
    return if (firstLimit == expected && secondLimit == expected) {
        DevCompatProbeResult.passed("槽位上限=[$firstLimit,$secondLimit]")
    } else {
        DevCompatProbeResult.failed("槽位上限=[$firstLimit,$secondLimit] 预期=$expected")
    }
}
