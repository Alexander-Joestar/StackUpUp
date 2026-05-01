package io.alexjoest.stackupup.dev

import com.mojang.authlib.GameProfile
import io.alexjoest.stackupup.StackLimitHooks
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraftforge.common.util.FakePlayerFactory
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.UUID

internal object RefinedStorageStorageMonitorExtractProbe : DevCompatProbe {
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
            arrayOf(securityManagerClass),
        ) { _, method, _ ->
            when (method.name) {
                "hasPermission" -> true
                else -> method.safeNullValue
            }
        }

        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "getSecurityManager" -> securityManager
                "extractItem" if args != null && args.size >= 2 -> {
                    requested[0] = (args[1] as Number).toLong()
                    ItemStack(Items.STICK, requested[0].toInt())
                }

                else -> method.safeNullValue
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

internal object RefinedStorageGridExtractProbe : DevCompatProbe {
    override val id: String = "refinedstorage_grid_extract"

    override fun isAvailable(): Boolean = hasClass("com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandler")

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
            Int::class.javaPrimitiveType,
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
                if (leftClickRequest >= 0L) summary else appendProbeFailureCause(summary, extractFailure),
            )
        }
    }

    private fun createGridNetworkProxy(itemStack: ItemStack, entry: Any, entryCount: Long, requestSizes: MutableList<Long>): Any {
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
            arrayOf(securityManagerClass),
        ) { _, method, _ ->
            when (method.name) {
                "hasPermission" -> true
                else -> method.safeNullValue
            }
        }

        val storageTracker = createStorageTrackerProxy(storageTrackerClass)

        val networkItemHandler = Proxy.newProxyInstance(
            networkItemHandlerClass.classLoader,
            arrayOf(networkItemHandlerClass),
        ) { _, method, _ ->
            when (method.name) {
                "drainEnergy" -> null
                else -> method.safeNullValue
            }
        }

        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "getSecurityManager" -> securityManager
                "getItemStorageCache" -> storageCache
                "getItemStorageTracker" -> storageTracker
                "getNetworkItemHandler" -> networkItemHandler
                "extractItem" -> {
                    val requested = (args?.get(1) as Number).toLong()
                    requestSizes += requested
                    stackListResultClass.getDeclaredConstructor(Any::class.java, java.lang.Long.TYPE)
                        .newInstance(itemStack.copy(), requested)
                }

                else -> method.safeNullValue
            }
        }

        return Proxy.newProxyInstance(networkClass.classLoader, arrayOf(networkClass), handler)
    }
}

internal object RefinedStoragePortableGridExtractProbe : DevCompatProbe {
    override val id: String = "refinedstorage_portable_grid_extract"

    override fun isAvailable(): Boolean = hasClass("com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandlerPortable")

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
                createPortableGridStateProxy(),
            )
        val idValue = entryClass.getMethod("getId").invoke(entry) as UUID

        val method = handlerClass.getMethod(
            "onExtract",
            net.minecraft.entity.player.EntityPlayerMP::class.java,
            UUID::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
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
                if (leftClickRequest >= 0L) summary else appendProbeFailureCause(summary, extractFailure),
            )
        }
    }

    private fun createPortableGridProxy(itemStack: ItemStack, entry: Any, entryCount: Long, requestSizes: MutableList<Long>): Any {
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
                "extract" -> {
                    val requested = (args?.get(1) as Number).toLong()
                    requestSizes += requested
                    stackListResultClass.getDeclaredConstructor(Any::class.java, java.lang.Long.TYPE)
                        .newInstance(itemStack.copy(), requested)
                }
                "getStored" -> entryCount
                "getEntries" -> listOf(entry)
                "getPriority" -> 0
                "getCapacity" -> entryCount
                "getId" -> "stackupup-portable-probe"
                else -> method.safeNullValue
            }
        }

        return Proxy.newProxyInstance(portableGridClass.classLoader, arrayOf(portableGridClass)) { _, method, _ ->
            when (method.name) {
                "getStorage", "getItemStorage" -> storageDisk
                "getCache", "getItemCache" -> storageCache
                "getItemStorageTracker" -> storageTracker
                "drainEnergy" -> null
                "getEnergy" -> 1000
                else -> method.safeNullValue
            }
        }
    }

    private fun createPortableGridStateProxy(): Any {
        val gridClass = loadClass("com.raoulvdberge.refinedstorage.api.network.grid.IGrid")
        return Proxy.newProxyInstance(gridClass.classLoader, arrayOf(gridClass)) { _, method, _ ->
            when (method.name) {
                "isActive" -> true
                else -> method.safeNullValue
            }
        }
    }
}

private fun createStackListProxy(stackListClass: Class<*>, entry: Any, entryCount: Long): Any {
    val entryId = entry.javaClass.getMethod("getId").invoke(entry) as UUID
    return Proxy.newProxyInstance(stackListClass.classLoader, arrayOf(stackListClass)) { _, method, args ->
        when (method.name) {
            "get" -> if (args?.firstOrNull() == entryId) entry else null
            "getEntry" -> entry
            "getStored" -> entryCount
            "isEmpty" -> false
            "getStacks" -> listOf(entry)
            else -> method.safeNullValue
        }
    }
}

private fun createStorageCacheProxy(storageCacheClass: Class<*>, stackList: Any): Any =
    Proxy.newProxyInstance(storageCacheClass.classLoader, arrayOf(storageCacheClass)) {
            _,
            method,
            _,
        ->
        when (method.name) {
            "getList" -> stackList
            "getCraftablesList" -> stackList
            "getStorages" -> emptyList<Any>()
            else -> method.safeNullValue
        }
    }

private fun createStorageTrackerProxy(storageTrackerClass: Class<*>): Any =
    Proxy.newProxyInstance(storageTrackerClass.classLoader, arrayOf(storageTrackerClass)) {
            _,
            method,
            _,
        ->
        when (method.name) {
            "changed" -> null
            else -> method.safeNullValue
        }
    }
