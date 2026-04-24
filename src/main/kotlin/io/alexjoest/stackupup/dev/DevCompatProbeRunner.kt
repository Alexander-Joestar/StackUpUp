package io.alexjoest.stackupup.dev

import com.google.common.base.Defaults.defaultValue
import com.mojang.authlib.GameProfile
import io.alexjoest.stackupup.StackLimitHooks
import io.alexjoest.stackupup.StackUpUp
import io.alexjoest.stackupup.core.FixedCompatTargets
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraftforge.common.util.FakePlayerFactory
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID

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
        SlotItemHandlerLimitProbe
    )

    internal fun probeIds(): List<String> = probes.map(DevCompatProbe::id)

    internal fun fixedTargetCoverage(): Set<String> =
        probes.asSequence()
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
                ProbeAvailability.missing()  -> {
                    StackUpUp.logger?.info("开发自动验收[兼容探针]：{} 跳过，目标模组未加载。", probe.id)
                    continue
                }
                else                         -> {
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

internal data class ProbeAvailability(
    val available: Boolean,
    val failureSummary: String?
) {
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

internal data class DevCompatProbeResult(
    val passed: Boolean,
    val summary: String
) {
    companion object {
        fun passed(summary: String): DevCompatProbeResult = DevCompatProbeResult(true, summary)

        fun failed(summary: String): DevCompatProbeResult = DevCompatProbeResult(false, summary)
    }
}

private object CyclopsCoreSimpleInventoryLimitProbe : DevCompatProbe {
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

private object ColossalChestsInventoryLimitProbe : DevCompatProbe {
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

private object RefinedStorageStorageMonitorExtractProbe : DevCompatProbe {
    override val id: String = "refinedstorage_storage_monitor_extract"
    override val primaryTargetClass: String =
        "com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeStorageMonitor"

    override fun run(server: MinecraftServer): DevCompatProbeResult {
        val world = server.getWorld(0)
        val monitorClass = loadClass(primaryTargetClass)
        val monitor = monitorClass.getDeclaredConstructor(net.minecraft.world.World::class.java, BlockPos::class.java)
            .newInstance(world, BlockPos.ORIGIN)

        val config = monitorClass.getMethod("getConfig").invoke(monitor)
        val itemHandler = config.javaClass.getMethod("getItemHandler").invoke(config)
        itemHandler.javaClass.getMethod("setStackInSlot", Int::class.javaPrimitiveType, ItemStack::class.java)
            .invoke(itemHandler, 0, ItemStack(Items.STICK, 1))

        val requested = LongArray(1)
        setNetworkField(monitor, createRefinedStorageNetworkProxy(requested))

        val player =
            FakePlayerFactory.get(world, GameProfile(UUID.nameUUIDFromBytes(id.toByteArray()), "StackUpUpProbe"))
        monitorClass.getMethod("extract", net.minecraft.entity.player.EntityPlayer::class.java, EnumFacing::class.java)
            .invoke(monitor, player, EnumFacing.NORTH)

        val expected = StackLimitHooks.getCompatibilityStackSize().toLong()
        return if (requested[0] == expected) {
            DevCompatProbeResult.passed("提取请求=$expected")
        } else {
            DevCompatProbeResult.failed("提取请求=${requested[0]} 预期=$expected")
        }
    }

    private fun createRefinedStorageNetworkProxy(requested: LongArray): Any {
        val networkClass = loadClass("com.raoulvdberge.refinedstorage.api.network.INetwork")
        val securityManagerClass = loadClass("com.raoulvdberge.refinedstorage.api.network.security.ISecurityManager")

        val securityManager = Proxy.newProxyInstance(
            securityManagerClass.classLoader,
            arrayOf(securityManagerClass)
        ) { _, method, _ ->
            when (method.name) {
                "hasPermission" -> true
                else            -> method.safeNullValue
            }
        }

        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "getSecurityManager"                            -> securityManager
                "extractItem" if args != null && args.size >= 2 -> {
                    requested[0] = (args[1] as Number).toLong()
                    ItemStack(Items.STICK, requested[0].toInt())
                }

                else                                            -> method.safeNullValue
            }
        }

        return Proxy.newProxyInstance(networkClass.classLoader, arrayOf(networkClass), handler)
    }

    private fun setNetworkField(monitor: Any, network: Any) {
        val field = findField(monitor.javaClass, "network")
        field.isAccessible = true
        field.set(monitor, network)
    }
}

private object RefinedStorageGridExtractProbe : DevCompatProbe {
    override val id: String = "refinedstorage_grid_extract"

    override fun isAvailable(): Boolean =
        hasClass("com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandler")

    override fun run(server: MinecraftServer): DevCompatProbeResult {
        val world = server.getWorld(0)
        val player =
            FakePlayerFactory.get(world, GameProfile(UUID.nameUUIDFromBytes(id.toByteArray()), "StackUpUpGridProbe"))
        val itemStack = DevCompatProbeItems.createGridExtractProbeStack()
        val entryCount = 4096L
        val entryClass = loadClass("com.raoulvdberge.refinedstorage.api.util.StackListEntry")
        val entry = entryClass.getDeclaredConstructor(Any::class.java, java.lang.Long.TYPE)
            .newInstance(itemStack.copy(), entryCount)

        val requestSizes = ArrayList<Long>()
        val handlerClass = loadClass("com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandler")
        val handler =
            handlerClass.getDeclaredConstructor(loadClass("com.raoulvdberge.refinedstorage.api.network.INetwork"))
                .newInstance(createGridNetworkProxy(itemStack, entry, entryCount, requestSizes))
        val idValue = entryClass.getMethod("getId").invoke(entry) as UUID

        val method = handlerClass.getMethod(
            "onExtract",
            net.minecraft.entity.player.EntityPlayerMP::class.java,
            UUID::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        var extractFailure: Throwable? = null
        try {
            method.invoke(handler, player, idValue, -1, 0)
        } catch (exception: InvocationTargetException) {
            // 原始调用可能在请求发出后继续因环境不完整而抛错；
            // 只有在根本没有记录到请求量时，才把异常并入摘要辅助定位。
            extractFailure = exception
        }

        val expected = StackLimitHooks.getCompatibilityStackSize().toLong()
        val leftClickRequest = requestSizes.firstOrNull() ?: -1L
        return if (leftClickRequest == expected) {
            DevCompatProbeResult.passed("左键提取请求=$leftClickRequest")
        } else {
            val summary = "左键提取请求=$leftClickRequest 预期=$expected"
            DevCompatProbeResult.failed(
                if (leftClickRequest >= 0L) summary else appendProbeFailureCause(summary, extractFailure)
            )
        }
    }

    private fun createGridNetworkProxy(
        itemStack: ItemStack,
        entry: Any,
        entryCount: Long,
        requestSizes: MutableList<Long>
    ): Any {
        val networkClass = loadClass("com.raoulvdberge.refinedstorage.api.network.INetwork")
        val securityManagerClass = loadClass("com.raoulvdberge.refinedstorage.api.network.security.ISecurityManager")
        val storageCacheClass = loadClass("com.raoulvdberge.refinedstorage.api.storage.IStorageCache")
        val stackListClass = loadClass("com.raoulvdberge.refinedstorage.api.util.IStackList")
        val storageTrackerClass = loadClass("com.raoulvdberge.refinedstorage.api.storage.tracker.IStorageTracker")
        val networkItemHandlerClass = loadClass("com.raoulvdberge.refinedstorage.api.network.item.INetworkItemHandler")
        val stackListResultClass = loadClass("com.raoulvdberge.refinedstorage.api.util.StackListResult")

        val stackList = createStackListProxy(stackListClass, entry, entryCount)
        val storageCache = createStorageCacheProxy(storageCacheClass, stackList)

        val securityManager = Proxy.newProxyInstance(
            securityManagerClass.classLoader,
            arrayOf(securityManagerClass)
        ) { _, method, _ ->
            when (method.name) {
                "hasPermission" -> true
                else            -> method.safeNullValue
            }
        }

        val storageTracker = createStorageTrackerProxy(storageTrackerClass)

        val networkItemHandler = Proxy.newProxyInstance(
            networkItemHandlerClass.classLoader,
            arrayOf(networkItemHandlerClass)
        ) { _, method, _ ->
            when (method.name) {
                "drainEnergy" -> null
                else          -> method.safeNullValue
            }
        }

        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "getSecurityManager"    -> securityManager
                "getItemStorageCache"   -> storageCache
                "getItemStorageTracker" -> storageTracker
                "getNetworkItemHandler" -> networkItemHandler
                "extractItem"           -> {
                    val requested = (args?.get(1) as Number).toLong()
                    requestSizes += requested
                    stackListResultClass.getDeclaredConstructor(Any::class.java, java.lang.Long.TYPE)
                        .newInstance(itemStack.copy(), requested)
                }

                else                    -> method.safeNullValue
            }
        }

        return Proxy.newProxyInstance(networkClass.classLoader, arrayOf(networkClass), handler)
    }
}

private object RefinedStoragePortableGridExtractProbe : DevCompatProbe {
    override val id: String = "refinedstorage_portable_grid_extract"

    override fun isAvailable(): Boolean =
        hasClass("com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandlerPortable")

    override fun run(server: MinecraftServer): DevCompatProbeResult {
        val world = server.getWorld(0)
        val player =
            FakePlayerFactory.get(world, GameProfile(UUID.nameUUIDFromBytes(id.toByteArray()), "StackUpUpPortableGridProbe"))
        val itemStack = DevCompatProbeItems.createGridExtractProbeStack()
        val entryCount = 4096L
        val entryClass = loadClass("com.raoulvdberge.refinedstorage.api.util.StackListEntry")
        val entry = entryClass.getDeclaredConstructor(Any::class.java, java.lang.Long.TYPE)
            .newInstance(itemStack.copy(), entryCount)

        val requestSizes = ArrayList<Long>()
        val portableGridClass = loadClass("com.raoulvdberge.refinedstorage.tile.grid.portable.IPortableGrid")
        val gridClass = loadClass("com.raoulvdberge.refinedstorage.api.network.grid.IGrid")
        val handlerClass = loadClass("com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandlerPortable")
        val handler = handlerClass
            .getDeclaredConstructor(portableGridClass, gridClass)
            .newInstance(
                createPortableGridProxy(itemStack, entry, entryCount, requestSizes),
                createPortableGridStateProxy()
            )
        val idValue = entryClass.getMethod("getId").invoke(entry) as UUID

        val method = handlerClass.getMethod(
            "onExtract",
            net.minecraft.entity.player.EntityPlayerMP::class.java,
            UUID::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        var extractFailure: Throwable? = null
        try {
            method.invoke(handler, player, idValue, -1, 0)
        } catch (exception: InvocationTargetException) {
            extractFailure = exception
        }

        val expected = StackLimitHooks.getCompatibilityStackSize().toLong()
        val leftClickRequest = requestSizes.firstOrNull() ?: -1L
        return if (leftClickRequest == expected) {
            DevCompatProbeResult.passed("左键提取请求=$leftClickRequest")
        } else {
            val summary = "左键提取请求=$leftClickRequest 预期=$expected"
            DevCompatProbeResult.failed(
                if (leftClickRequest >= 0L) summary else appendProbeFailureCause(summary, extractFailure)
            )
        }
    }

    private fun createPortableGridProxy(
        itemStack: ItemStack,
        entry: Any,
        entryCount: Long,
        requestSizes: MutableList<Long>
    ): Any {
        val portableGridClass = loadClass("com.raoulvdberge.refinedstorage.tile.grid.portable.IPortableGrid")
        val storageCacheClass = loadClass("com.raoulvdberge.refinedstorage.api.storage.IStorageCache")
        val stackListClass = loadClass("com.raoulvdberge.refinedstorage.api.util.IStackList")
        val storageTrackerClass = loadClass("com.raoulvdberge.refinedstorage.api.storage.tracker.IStorageTracker")
        val storageDiskClass = loadClass("com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk")
        val stackListResultClass = loadClass("com.raoulvdberge.refinedstorage.api.util.StackListResult")

        val stackList = createStackListProxy(stackListClass, entry, entryCount)
        val storageCache = createStorageCacheProxy(storageCacheClass, stackList)
        val storageTracker = createStorageTrackerProxy(storageTrackerClass)

        val storageDisk = Proxy.newProxyInstance(storageDiskClass.classLoader, arrayOf(storageDiskClass)) { _, method, args ->
            when (method.name) {
                "extract"     -> {
                    val requested = (args?.get(1) as Number).toLong()
                    requestSizes += requested
                    stackListResultClass.getDeclaredConstructor(Any::class.java, java.lang.Long.TYPE)
                        .newInstance(itemStack.copy(), requested)
                }
                "getStored"   -> entryCount
                "getEntries"  -> listOf(entry)
                "getPriority" -> 0
                "getCapacity" -> entryCount
                "getId"       -> "stackupup-portable-probe"
                else          -> method.safeNullValue
            }
        }

        return Proxy.newProxyInstance(portableGridClass.classLoader, arrayOf(portableGridClass)) { _, method, _ ->
            when (method.name) {
                "getStorage", "getItemStorage"       -> storageDisk
                "getCache", "getItemCache"           -> storageCache
                "getItemStorageTracker"              -> storageTracker
                "drainEnergy"                        -> null
                "getEnergy"                          -> 1000
                else                                 -> method.safeNullValue
            }
        }
    }

    private fun createPortableGridStateProxy(): Any {
        val gridClass = loadClass("com.raoulvdberge.refinedstorage.api.network.grid.IGrid")
        return Proxy.newProxyInstance(gridClass.classLoader, arrayOf(gridClass)) { _, method, _ ->
            when (method.name) {
                "isActive" -> true
                else       -> method.safeNullValue
            }
        }
    }
}

private fun createStackListProxy(stackListClass: Class<*>, entry: Any, entryCount: Long): Any {
    val entryId = entry.javaClass.getMethod("getId").invoke(entry) as UUID
    return Proxy.newProxyInstance(stackListClass.classLoader, arrayOf(stackListClass)) { _, method, args ->
        when (method.name) {
            "get"       -> if (args?.firstOrNull() == entryId) entry else null
            "getEntry"  -> entry
            "getStored" -> entryCount
            "isEmpty"   -> false
            "getStacks" -> listOf(entry)
            else        -> method.safeNullValue
        }
    }
}

private fun createStorageCacheProxy(storageCacheClass: Class<*>, stackList: Any): Any {
    return Proxy.newProxyInstance(storageCacheClass.classLoader, arrayOf(storageCacheClass)) { _, method, _ ->
        when (method.name) {
            "getList"           -> stackList
            "getCraftablesList" -> stackList
            "getStorages"       -> emptyList<Any>()
            else                -> method.safeNullValue
        }
    }
}

private fun createStorageTrackerProxy(storageTrackerClass: Class<*>): Any {
    return Proxy.newProxyInstance(storageTrackerClass.classLoader, arrayOf(storageTrackerClass)) { _, method, _ ->
        when (method.name) {
            "changed" -> null
            else      -> method.safeNullValue
        }
    }
}

internal fun hasClass(name: String): Boolean {
    return try {
        loadClass(name)
        true
    } catch (_: Throwable) {
        false
    }
}

internal fun loadClass(name: String): Class<*> =
    Class.forName(name, false, DevCompatProbeRunner::class.java.classLoader)

internal fun createInventoryProxy(): Any {
    val inventoryClass = loadClass("net.minecraft.inventory.IInventory")
    val handler = InvocationHandler { _, method, args ->
        when (method.name) {
            "func_70302_i_", "getSizeInventory"        -> 1
            "func_70301_a", "getStackInSlot"           -> ItemStack.EMPTY
            "func_70297_j_", "getInventoryStackLimit"  -> 64
            "func_94041_b", "isItemValidForSlot"       -> true
            "func_70298_a", "decrStackSize"            -> ItemStack.EMPTY
            "func_70304_b", "removeStackFromSlot"      -> ItemStack.EMPTY
            "func_70299_a", "setInventorySlotContents" -> null
            "func_70296_d", "markDirty"                -> null
            "func_70300_a", "isUsableByPlayer"         -> true
            "func_174889_b", "openInventory"           -> null
            "func_174886_c", "closeInventory"          -> null
            "func_145818_k_", "hasCustomName"          -> false
            "func_70005_c_", "getName"                 -> "StackUpUpProbeInventory"
            "func_145748_c_", "getDisplayName"         -> null
            "func_191420_l", "isEmpty"                 -> true
            "func_174887_a_", "getField"               -> 0
            "func_174885_b", "setField"                -> null
            "func_174890_g", "getFieldCount"           -> 0
            "func_174888_l", "clear"                   -> null
            else                                       -> method.safeNullValue
        }
    }

    return Proxy.newProxyInstance(inventoryClass.classLoader, arrayOf(inventoryClass), handler)
}

internal fun createSidedInventoryProxy(): Any {
    val sidedInventoryClass = loadClass("net.minecraft.inventory.ISidedInventory")
    val inventoryClass = loadClass("net.minecraft.inventory.IInventory")
    val handler = InvocationHandler { _, method, args ->
        when (method.name) {
            "func_180463_a", "getSlotsForFace"         -> intArrayOf(0)
            "func_180462_a", "canInsertItem"           -> true
            "func_180461_b", "canExtractItem"          -> true
            "func_70302_i_", "getSizeInventory"        -> 1
            "func_70301_a", "getStackInSlot"           -> ItemStack.EMPTY
            "func_70297_j_", "getInventoryStackLimit"  -> 64
            "func_94041_b", "isItemValidForSlot"       -> true
            "func_70298_a", "decrStackSize"            -> ItemStack.EMPTY
            "func_70304_b", "removeStackFromSlot"      -> ItemStack.EMPTY
            "func_70299_a", "setInventorySlotContents" -> null
            "func_70296_d", "markDirty"                -> null
            "func_70300_a", "isUsableByPlayer"         -> true
            "func_174889_b", "openInventory"           -> null
            "func_174886_c", "closeInventory"          -> null
            "func_145818_k_", "hasCustomName"          -> false
            "func_70005_c_", "getName"                 -> "StackUpUpProbeSidedInventory"
            "func_145748_c_", "getDisplayName"         -> null
            "func_191420_l", "isEmpty"                 -> true
            "func_174887_a_", "getField"               -> 0
            "func_174885_b", "setField"                -> null
            "func_174890_g", "getFieldCount"           -> 0
            "func_174888_l", "clear"                   -> null
            else                                       -> method.safeNullValue
        }
    }

    return Proxy.newProxyInstance(
        sidedInventoryClass.classLoader,
        arrayOf(sidedInventoryClass, inventoryClass),
        handler
    )
}

internal fun findField(type: Class<*>, name: String): Field {
    var current: Class<*>? = type
    while (current != null) {
        try {
            return current.getDeclaredField(name)
        } catch (_: NoSuchFieldException) {
            current = current.superclass
        }
    }
    throw NoSuchFieldException(name)
}

internal fun findMethod(type: Class<*>, names: Array<String>, vararg parameterTypes: Class<*>): Method {
    var current: Class<*>? = type
    while (current != null) {
        for (name in names) {
            try {
                return current.getMethod(name, *parameterTypes)
            } catch (_: NoSuchMethodException) {
            }

            try {
                val method = current.getDeclaredMethod(name, *parameterTypes)
                method.isAccessible = true
                return method
            } catch (_: NoSuchMethodException) {
            }
        }
        current = current.superclass
    }

    throw NoSuchMethodException(names.joinToString(prefix = "[", postfix = "]"))
}

internal fun formatProbeThrowable(throwable: Throwable): String {
    val unwrapped = (throwable as? InvocationTargetException)?.targetException ?: throwable
    val message = unwrapped.message?.takeIf(String::isNotBlank)
    return if (message == null) unwrapped.javaClass.simpleName else "${unwrapped.javaClass.simpleName}: $message"
}

internal fun evaluateProbeAvailability(check: () -> Boolean): ProbeAvailability {
    return try {
        if (check()) ProbeAvailability.available() else ProbeAvailability.missing()
    } catch (throwable: Throwable) {
        ProbeAvailability.failed(formatProbeThrowable(throwable))
    }
}

internal fun expectedFixedTargetProbeCoverage(): Set<String> = FixedCompatTargets.probeTargets().toSet()

internal fun appendProbeFailureCause(summary: String, throwable: Throwable?): String {
    if (throwable == null) {
        return summary
    }
    return "$summary 原因=${formatProbeThrowable(throwable)}"
}

internal val Method.safeNullValue: Any?
    get() = when (returnType) {
        ItemStack::class.java -> ItemStack.EMPTY
        else                  -> defaultValue(returnType)
    }
