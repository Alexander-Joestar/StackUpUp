package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.StackLimitHooks
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.util.EnumFacing
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

internal object CyclopsCoreSimpleInventoryLimitProbe : DevCompatProbe {
    override val id: String = "cyclopscore_simple_inventory_limit"
    override val isFixedTargetProbe: Boolean = true
    override val primaryTargetClass: String = "org.cyclops.cyclopscore.inventory.SimpleInventory"

    override fun run(server: MinecraftServer): DevCompatProbeResult {
        val inventoryClass = loadClass(primaryTargetClass)
        val inventory = inventoryClass
            .getDeclaredConstructor(Int::class.javaPrimitiveType, String::class.java, Int::class.javaPrimitiveType)
            .newInstance(1, "StackUpUpProbe", 64)
        val limit = inventoryClass.getMethod("getInventoryStackLimit").invoke(inventory) as Int
        val expected = StackLimitHooks.getCompatibilityStackSize()

        val stack = ItemStack(Items.STICK, 128)
        inventoryClass.getMethod("setInventorySlotContents", Int::class.javaPrimitiveType, ItemStack::class.java)
            .invoke(inventory, 0, stack)
        val stored = inventoryClass.getMethod("getStackInSlot", Int::class.javaPrimitiveType)
            .invoke(inventory, 0) as ItemStack

        return if (limit == expected && stored.count == 128) {
            DevCompatProbeResult.passed("上限=$limit 存入=${stored.count}")
        } else {
            DevCompatProbeResult.failed("上限=$limit 预期=$expected 存入=${stored.count}")
        }
    }
}

internal object ColossalChestsInventoryLimitProbe : DevCompatProbe {
    override val id: String = "colossalchests_inventory_limit"
    override val primaryTargetClass: String = "org.cyclops.colossalchests.tileentity.TileColossalChest"

    override fun run(server: MinecraftServer): DevCompatProbeResult {
        val tileClass = loadClass(primaryTargetClass)
        val tile = tileClass.getDeclaredConstructor().newInstance()
        tileClass.getMethod("setSize", net.minecraft.util.math.Vec3i::class.java)
            .invoke(tile, net.minecraft.util.math.Vec3i(2, 2, 2))

        val inventory = tileClass.getMethod("getInventory").invoke(tile)
        val inventoryClass = loadClass("net.minecraft.inventory.IInventory")
        val limit = inventoryClass.getMethod("getInventoryStackLimit").invoke(inventory) as Int
        val expected = StackLimitHooks.getCompatibilityStackSize()

        val stack = ItemStack(Items.STICK, 128)
        inventoryClass.getMethod("setInventorySlotContents", Int::class.javaPrimitiveType, ItemStack::class.java)
            .invoke(inventory, 0, stack)
        val stored =
            inventoryClass.getMethod("getStackInSlot", Int::class.javaPrimitiveType).invoke(inventory, 0) as ItemStack

        return if (limit == expected && stored.count == 128) {
            DevCompatProbeResult.passed("上限=$limit 存入=${stored.count}")
        } else {
            DevCompatProbeResult.failed("上限=$limit 预期=$expected 存入=${stored.count}")
        }
    }
}

internal fun createInventoryProxy(): Any {
    val inventoryClass = loadClass("net.minecraft.inventory.IInventory")
    val handler = InvocationHandler { _, method, args ->
        when (method.name) {
            "func_70302_i_", "getSizeInventory" -> 1
            "func_70301_a", "getStackInSlot" -> ItemStack.EMPTY
            "func_70297_j_", "getInventoryStackLimit" -> 64
            "func_94041_b", "isItemValidForSlot" -> true
            "func_70298_a", "decrStackSize" -> ItemStack.EMPTY
            "func_70304_b", "removeStackFromSlot" -> ItemStack.EMPTY
            "func_70299_a", "setInventorySlotContents" -> null
            "func_70296_d", "markDirty" -> null
            "func_70300_a", "isUsableByPlayer" -> true
            "func_174889_b", "openInventory" -> null
            "func_174886_c", "closeInventory" -> null
            "func_145818_k_", "hasCustomName" -> false
            "func_70005_c_", "getName" -> "StackUpUpProbeInventory"
            "func_145748_c_", "getDisplayName" -> null
            "func_191420_l", "isEmpty" -> true
            "func_174887_a_", "getField" -> 0
            "func_174885_b", "setField" -> null
            "func_174890_g", "getFieldCount" -> 0
            "func_174888_l", "clear" -> null
            else -> method.safeNullValue
        }
    }

    return Proxy.newProxyInstance(inventoryClass.classLoader, arrayOf(inventoryClass), handler)
}

internal fun createSidedInventoryProxy(): Any {
    val sidedInventoryClass = loadClass("net.minecraft.inventory.ISidedInventory")
    val inventoryClass = loadClass("net.minecraft.inventory.IInventory")
    val handler = InvocationHandler { _, method, args ->
        when (method.name) {
            "func_180463_a", "getSlotsForFace" -> intArrayOf(0)
            "func_180462_a", "canInsertItem" -> true
            "func_180461_b", "canExtractItem" -> true
            "func_70302_i_", "getSizeInventory" -> 1
            "func_70301_a", "getStackInSlot" -> ItemStack.EMPTY
            "func_70297_j_", "getInventoryStackLimit" -> 64
            "func_94041_b", "isItemValidForSlot" -> true
            "func_70298_a", "decrStackSize" -> ItemStack.EMPTY
            "func_70304_b", "removeStackFromSlot" -> ItemStack.EMPTY
            "func_70299_a", "setInventorySlotContents" -> null
            "func_70296_d", "markDirty" -> null
            "func_70300_a", "isUsableByPlayer" -> true
            "func_174889_b", "openInventory" -> null
            "func_174886_c", "closeInventory" -> null
            "func_145818_k_", "hasCustomName" -> false
            "func_70005_c_", "getName" -> "StackUpUpProbeSidedInventory"
            "func_145748_c_", "getDisplayName" -> null
            "func_191420_l", "isEmpty" -> true
            "func_174887_a_", "getField" -> 0
            "func_174885_b", "setField" -> null
            "func_174890_g", "getFieldCount" -> 0
            "func_174888_l", "clear" -> null
            else -> method.safeNullValue
        }
    }

    return Proxy.newProxyInstance(
        sidedInventoryClass.classLoader,
        arrayOf(sidedInventoryClass, inventoryClass),
        handler
    )
}
