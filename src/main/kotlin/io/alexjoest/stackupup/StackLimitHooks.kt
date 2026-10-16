package io.alexjoest.stackupup

import io.alexjoest.stackupup.core.InventoryStackLimitHook
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.limit.StackContextResolver
import io.alexjoest.stackupup.limit.StackLimitService
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.Loader
import java.util.Locale
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/** 用途：Mixin 注入求取堆叠上限的唯一调用面。 */
object StackLimitHooks {
    private const val VANILLA_STACK_LIMIT: Int = Constants.VANILLA_STACK_LIMIT

    /**
     * T4b：内容键缓存，替代 mark/consume 的实例身份 ThreadLocal。
     *
     * 键只含真正参与堆叠计算的内容分量（itemId/meta），不持有 ItemStack 实例引用；
     * 同一实例变更 count/NBT 不会改变键，仍可复用缓存；变更 meta 后键随之变化，旧值不会被复用。
     * 键覆盖依据 T6 已证明的字段覆盖：ITEM/MOD/TYPE/META/SIZE 由身份键分量承载，
     * ORE 由 itemId+metadata 稳定决定，MATERIAL/TAB 由 item 实例（itemId）稳定决定。
     *
     * 失效：RuleRuntime.replaceRuntime 整体换新 [StackLimitService] 实例时，
     * 通过 [cacheServiceEpoch] 检测并清空；条目还携带写入时的服务引用，
     * 读取时校验服务一致（换新瞬间的并发写入也不会把旧快照的值泄漏给新快照）。
     */
    private val resolvedItemLimitCache = ConcurrentHashMap<StackContentKey, CachedResolvedLimit>()
    private val cacheServiceEpoch = AtomicReference<StackLimitService?>(null)

    @JvmField
    val enteringItemMixin: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    private val originalBaselineBypassDepth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

    @JvmField
    val RANDOM: Random = Random()

    @JvmStatic
    fun getCompatibilityStackSize(): Int = StackUpUpConfig.maxStackSize

    @JvmStatic
    fun getInventoryCompatibilityStackSize(): Int = getInventoryCompatibilityStackSize(VANILLA_STACK_LIMIT)

    @JvmStatic
    fun getCoreInventoryStackSize(target: Any, original: Int): Int {
        if (original != VANILLA_STACK_LIMIT || !canExpandCoreInventory(target)) return original
        return StackUpUpConfig.maxStackSize
    }

    @JvmStatic
    fun canExpandCoreInventory(target: Any): Boolean {
        if (!InventoryStackLimitHook.isPrepared() || shouldBypassInventoryStackLimit(target)) return false
        if (target is net.minecraft.entity.EntityLivingBase) {
            return target.javaClass.name.startsWith("net.minecraft.")
        }
        if (!isCoreInventoryGetterTarget(target.javaClass)) return false
        val owner = (target as? net.minecraft.entity.player.InventoryPlayer)?.player
        return owner == null || owner.javaClass.name.startsWith("net.minecraft.")
    }

    internal fun isCoreInventoryGetterTarget(targetClass: Class<*>): Boolean = when (targetClass) {
        net.minecraft.tileentity.TileEntityDispenser::class.java,
        net.minecraft.tileentity.TileEntityChest::class.java,
        net.minecraft.tileentity.TileEntityFurnace::class.java,
        net.minecraft.tileentity.TileEntityBrewingStand::class.java,
        net.minecraft.tileentity.TileEntityHopper::class.java,
        net.minecraft.tileentity.TileEntityShulkerBox::class.java,
        net.minecraft.entity.item.EntityMinecartContainer::class.java,
        net.minecraft.entity.player.InventoryPlayer::class.java,
        net.minecraft.inventory.InventoryBasic::class.java,
        net.minecraft.inventory.InventoryMerchant::class.java,
        net.minecraft.inventory.InventoryCrafting::class.java,
        net.minecraft.inventory.InventoryCraftResult::class.java,
        -> true
        else -> false
    }

    internal val NATIVE64_WRITER_CAPABILITY_TARGETS: Set<Class<*>> = setOf(
        net.minecraft.entity.player.InventoryPlayer::class.java,
        net.minecraft.tileentity.TileEntityChest::class.java,
        net.minecraft.tileentity.TileEntityDispenser::class.java,
        net.minecraft.tileentity.TileEntityShulkerBox::class.java,
        net.minecraft.tileentity.TileEntityBrewingStand::class.java,
    )

    internal fun isNative64WriterCapabilityTarget(targetClass: Class<*>): Boolean = targetClass in NATIVE64_WRITER_CAPABILITY_TARGETS

    @JvmStatic
    fun getEquipmentInventoryStackSize(
        wrapper: net.minecraftforge.items.wrapper.EntityEquipmentInvWrapper,
        entity: net.minecraft.entity.EntityLivingBase,
        original: Int,
    ): Int {
        if (original != VANILLA_STACK_LIMIT ||
            !InventoryStackLimitHook.isPrepared() ||
            shouldBypassInventoryStackLimit(wrapper) ||
            shouldBypassInventoryStackLimit(entity)
        ) {
            return original
        }
        if (!InventoryStackLimitHook.isExpansionEnabled()) {
            val wrapperType = wrapper.javaClass
            if (wrapperType != net.minecraftforge.items.wrapper.EntityHandsInvWrapper::class.java &&
                wrapperType != net.minecraftforge.items.wrapper.EntityArmorInvWrapper::class.java
            ) {
                return original
            }
            if (!canExpandCoreInventory(entity)) return original
        }
        return StackUpUpConfig.maxStackSize
    }

    @JvmStatic
    fun getInventoryCompatibilityStackSize(target: Any, owner: Any?, original: Int): Int {
        if (owner != null && shouldBypassInventoryStackLimit(owner)) return original
        return getInventoryCompatibilityStackSize(target, original)
    }

    @JvmStatic
    fun getInventoryCompatibilityStackSize(target: Any, original: Int): Int {
        if (shouldBypassInventoryStackLimit(target)) return original
        return InventoryStackLimitHook.resolveInventoryStackLimit(target.javaClass, original)
    }

    @JvmStatic
    fun getInventoryCompatibilityStackSize(original: Int): Int {
        if (original != VANILLA_STACK_LIMIT ||
            !InventoryStackLimitHook.isExpansionEnabled() ||
            !InventoryStackLimitHook.isPrepared()
        ) {
            return original
        }
        return StackUpUpConfig.maxStackSize
    }

    @JvmStatic
    fun refreshInventoryStackLimitBlacklist() {
        if (InventoryStackLimitHook.isPrepared()) {
            InventoryStackLimitHook.setCompatibilityStackSize(StackUpUpConfig.maxStackSize)
            return
        }
        val modIds = io.alexjoest.stackupup.core.InventoryExpansionStartupConfig.load().modIds
        val ownedPackagesByMod = if (modIds.isEmpty()) {
            emptyMap()
        } else {
            Loader.instance().modList.associate { it.modId to it.ownedPackages }
        }
        refreshInventoryStackLimitBlacklist(modIds, ownedPackagesByMod)
    }

    internal fun refreshInventoryStackLimitBlacklist(modIds: List<String>, ownedPackagesByMod: Map<String, Collection<String>>) {
        val configuredModIds = modIds.map { it.trim().lowercase(Locale.ROOT) }.toSet()
        require(configuredModIds.all { it.matches(Regex("[a-z0-9_.-]+")) }) { "Invalid inventory capacity blacklist mod ID: $modIds" }
        val loadedMods = ownedPackagesByMod.mapKeys { it.key.lowercase(Locale.ROOT) }
        val ownedPackages = configuredModIds.flatMap { modId ->
            val packages = loadedMods[modId]
            if (packages == null) {
                org.apache.logging.log4j.LogManager.getLogger("stackupup.capacity").warn(
                    "Inventory capacity blacklist mod '{}' is not loaded; entry has no effect",
                    modId,
                )
                emptyList()
            } else {
                check(packages.any { it.isNotBlank() }) { "Inventory capacity blacklist mod '$modId' has no attributable owned packages" }
                packages.filter { it.isNotBlank() }
            }
        }
        InventoryStackLimitHook.prepare(StackUpUpConfig.maxStackSize, ownedPackages.toTypedArray())
    }

    @JvmStatic
    internal fun installInventoryStackLimitBlacklistForTesting(packageNames: Array<String>) {
        InventoryStackLimitHook.prepare(StackUpUpConfig.maxStackSize, packageNames)
    }

    @JvmStatic
    fun shouldBypassInventoryStackLimit(target: Any): Boolean {
        if (InventoryStackLimitHook.isBlacklisted(target.javaClass)) return true
        val owner = (target as? net.minecraft.entity.player.InventoryPlayer)?.player
        if (owner != null && InventoryStackLimitHook.isBlacklisted(owner.javaClass)) return true
        val delegate = (target as? net.minecraftforge.items.wrapper.InvWrapper)?.inv
        return delegate != null && shouldBypassInventoryStackLimit(delegate)
    }

    @JvmStatic
    @Deprecated("Internal stack limit resolution has moved to ItemStack/StackContext; this overload is kept only for legacy callers.")
    fun applyDynamicStackLimit(itemId: String, modId: String, meta: Int, type: String, baseLimit: Int, oreNames: Set<String>): Int =
        RuleRuntime.limitService().resolve(
            StackContext(
                itemId = itemId,
                modId = modId,
                metadata = meta,
                type = type,
                baseLimit = baseLimit,
                oreNames = oreNames,
            ),
        )

    @JvmStatic
    fun applyDynamicStackLimit(stack: ItemStack, baseLimit: Int): Int {
        if (shouldBypassDynamicItemRules()) return baseLimit
        if (enteringItemMixin.get()) return baseLimit
        enteringItemMixin.set(true)
        try {
            val limitService = RuleRuntime.limitService()
            if (!limitService.hasRules()) return resolveOriginalBaseline(stack, baseLimit)
            val originalBaseline = resolveOriginalBaseline(stack, baseLimit)
            val context = StackContextResolver.fromStack(
                stack = stack,
                baseLimit = originalBaseline,
                requirements = limitService.contextRequirements(),
            ) ?: return originalBaseline
            return limitService.resolve(context)
        } finally {
            enteringItemMixin.remove()
        }
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
    fun cacheResolvedItemLimit(stack: ItemStack, resolvedLimit: Int): Int {
        val service = RuleRuntime.limitService()
        contentCacheFor(service)[StackContentKey.fromStack(stack)] = CachedResolvedLimit(service, resolvedLimit)
        return resolvedLimit
    }

    @JvmStatic
    fun lookupResolvedItemLimit(stack: ItemStack): Int? {
        if (shouldBypassDynamicItemRules()) {
            // 基线解析（resolveOriginalBaseline）期间不得把规则化缓存值泄漏进原始基线。
            return null
        }
        val service = RuleRuntime.limitService()
        val cached = contentCacheFor(service)[StackContentKey.fromStack(stack)] ?: return null
        return if (cached.service === service) cached.value else null
    }

    @JvmStatic
    fun debugResolvedContentCacheSize(): Int = resolvedItemLimitCache.size

    private fun contentCacheFor(service: StackLimitService): ConcurrentHashMap<StackContentKey, CachedResolvedLimit> {
        while (true) {
            val seen = cacheServiceEpoch.get()
            if (seen === service) {
                return resolvedItemLimitCache
            }
            if (cacheServiceEpoch.compareAndSet(seen, service)) {
                // 快照替换驱动失效：与 T6 的解析缓存同构，换新即整表清空，不做逐条过期。
                resolvedItemLimitCache.clear()
                return resolvedItemLimitCache
            }
        }
    }

    private data class CachedResolvedLimit(val service: StackLimitService, val value: Int)

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
    fun resolveInventoryWriteLimit(stack: ItemStack, inventoryLimit: Int): Int {
        if (stack.isEmpty || inventoryLimit <= 0) {
            return inventoryLimit
        }
        return resolveInventoryWriteLimit(stack, inventoryLimit, stack.maxStackSize)
    }

    @JvmStatic
    fun resolveInventoryWriteLimit(stack: ItemStack, inventoryLimit: Int, itemLimit: Int): Int {
        if (stack.isEmpty || inventoryLimit <= 0) {
            return inventoryLimit
        }
        return if (inventoryLimit == VANILLA_STACK_LIMIT) itemLimit else minOf(inventoryLimit, itemLimit)
    }

    @JvmStatic
    fun resolveInventoryWriteLimit(stack: ItemStack, target: Any, inventoryLimit: Int): Int {
        if (stack.isEmpty || inventoryLimit <= 0) {
            return inventoryLimit
        }
        return resolveInventoryWriteLimit(stack, target, inventoryLimit, stack.maxStackSize)
    }

    @JvmStatic
    fun resolveInventoryWriteLimit(stack: ItemStack, target: Any, inventoryLimit: Int, itemLimit: Int): Int {
        if (stack.isEmpty || inventoryLimit <= 0) {
            return inventoryLimit
        }

        if (shouldBypassInventoryStackLimit(target)) {
            return minOf(inventoryLimit, itemLimit)
        }

        if (inventoryLimit == VANILLA_STACK_LIMIT &&
            InventoryStackLimitHook.isPrepared() &&
            isNative64WriterCapabilityTarget(target.javaClass)
        ) {
            return minOf(StackUpUpConfig.maxStackSize, itemLimit)
        }

        return minOf(inventoryLimit, itemLimit)
    }

    @JvmStatic
    fun resolvePlacementSlotLimit(stack: ItemStack, slotLimit: Int, inventoryLimit: Int): Int =
        resolvePlacementSlotLimit(stack, null, slotLimit, inventoryLimit)

    @JvmStatic
    fun resolvePlacementSlotLimit(stack: ItemStack, target: Any?, slotLimit: Int, inventoryLimit: Int): Int {
        val itemLimit = if (stack.isEmpty) 0 else stack.maxStackSize
        val dynamic = resolveDynamicSlotLimit(stack, slotLimit, itemLimit)
        val itemAware = if (stack.isEmpty) dynamic else minOf(dynamic, itemLimit)
        val effectiveInventoryLimit = if (target == null) {
            resolveInventoryWriteLimit(stack, inventoryLimit, itemLimit)
        } else {
            resolveInventoryWriteLimit(stack, target, inventoryLimit, itemLimit)
        }
        return if (effectiveInventoryLimit > 0) minOf(itemAware, effectiveInventoryLimit) else itemAware
    }

    @JvmStatic
    fun isSpecialContainerInventory(target: Any): Boolean =
        target is net.minecraft.inventory.InventoryCrafting || target is net.minecraft.inventory.InventoryCraftResult

    @JvmStatic
    fun resolveDynamicSlotLimit(stack: ItemStack, slotLimit: Int): Int {
        if (stack.isEmpty) {
            return slotLimit
        }
        return resolveDynamicSlotLimit(stack, slotLimit, stack.maxStackSize)
    }

    @JvmStatic
    fun resolveDynamicSlotLimit(stack: ItemStack, slotLimit: Int, itemLimit: Int): Int {
        if (stack.isEmpty) {
            return slotLimit
        }

        if (slotLimit >= itemLimit || slotLimit != VANILLA_STACK_LIMIT) {
            return slotLimit
        }

        return itemLimit
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
        val totalLimit = when {
            slotLimit < currentItemLimit -> slotLimit
            currentItemLimit < slotLimit && currentItemLimit != VANILLA_STACK_LIMIT -> currentItemLimit
            else -> slotLimit
        }
        return minOf(simulatedLimit, totalLimit)
    }

    @JvmStatic
    fun resolveInventoryClampLimit(stack: ItemStack, inventoryLimit: Int): Int = resolveInventoryClampLimit(stack, null, inventoryLimit)

    @JvmStatic
    fun resolveInventoryClampLimit(stack: ItemStack, target: Any?, inventoryLimit: Int): Int {
        if (target != null && shouldBypassInventoryStackLimit(target)) {
            return if (stack.isEmpty) inventoryLimit else minOf(inventoryLimit, stack.maxStackSize)
        }
        if (InventoryStackLimitHook.isExpansionEnabled() || inventoryLimit != VANILLA_STACK_LIMIT) {
            return if (stack.isEmpty) inventoryLimit else minOf(inventoryLimit, stack.maxStackSize)
        }
        return resolveInventoryWriteLimit(stack, inventoryLimit)
    }

    @JvmStatic
    fun expandDefaultExtractLimit(requestedSize: Long, maxItemSize: Long): Long =
        if (requestedSize == VANILLA_STACK_LIMIT.toLong() && maxItemSize > VANILLA_STACK_LIMIT.toLong()) {
            maxItemSize
        } else {
            minOf(requestedSize, maxItemSize)
        }
}

/**
 * ItemStack 的内容可观察键（T4b）。
 *
 * 只取真正参与堆叠计算的内容分量（itemId/meta），不引用 ItemStack 实例；
 * count/NBT 变更不会改变键，因此不会使同一物品元数据的缓存失效。
 * itemId 取注册名；未注册物品（仅测试场景）退回类名，避免不同物品类共享同一键。
 */
data class StackContentKey(val itemId: String?, val metadata: Int) {
    companion object {
        @JvmStatic
        fun fromStack(stack: ItemStack): StackContentKey {
            val item = stack.item
            return StackContentKey(
                itemId = item.registryName?.toString() ?: item.javaClass.name,
                metadata = stack.metadata,
            )
        }
    }
}
