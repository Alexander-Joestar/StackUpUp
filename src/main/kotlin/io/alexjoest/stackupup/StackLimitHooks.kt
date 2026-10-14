package io.alexjoest.stackupup

import io.alexjoest.stackupup.core.InventoryStackLimitHook
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.limit.StackContext
import io.alexjoest.stackupup.limit.StackContextResolver
import io.alexjoest.stackupup.limit.StackLimitService
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
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
     * 键只含内容可观察分量（itemId/meta/count/NBT），不持有 ItemStack 实例引用；
     * 同一实例变更 meta/NBT 后键随之变化，旧值不会被复用（旧身份缓存会在变异后继续命中旧值）。
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
    fun getInventoryCompatibilityStackSize(original: Int): Int {
        if (original != VANILLA_STACK_LIMIT ||
            !InventoryStackLimitHook.isExpansionEnabled() ||
            !InventoryStackLimitHook.isPrepared()
        ) {
            return original
        }
        return if (inventoryStackLimitBlacklistCache.get().matchesAny()) VANILLA_STACK_LIMIT else StackUpUpConfig.maxStackSize
    }

    private val inventoryStackLimitBlacklistCache = AtomicReference(InventoryStackLimitBlacklistCache.empty())

    @JvmStatic
    fun refreshInventoryStackLimitBlacklist() {
        val configuredModIds = StackUpUpConfig.compat.inventoryStackLimitBlacklist.modIds
            .asSequence()
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toSet()
        if (configuredModIds.isNotEmpty()) {
            InventoryStackLimitHook.disableExpansionForRuntimeBlacklist()
        }
        val ownedPackages = if (configuredModIds.isEmpty()) {
            emptyList()
        } else {
            Loader.instance().modList.asSequence()
                .filter { configuredModIds.contains(it.modId.lowercase(Locale.ROOT)) }
                .flatMap { it.ownedPackages.asSequence() }
                .toList()
        }
        inventoryStackLimitBlacklistCache.set(InventoryStackLimitBlacklistCache.from(ownedPackages))
        InventoryStackLimitHook.prepare(StackUpUpConfig.maxStackSize, ownedPackages.toTypedArray())
    }

    @JvmStatic
    internal fun installInventoryStackLimitBlacklistForTesting(packageNames: Array<String>) {
        inventoryStackLimitBlacklistCache.set(InventoryStackLimitBlacklistCache.from(packageNames.asList()))
        InventoryStackLimitHook.prepare(StackUpUpConfig.maxStackSize, packageNames)
    }

    @JvmStatic
    fun shouldBypassInventoryStackLimit(target: Any): Boolean = inventoryStackLimitBlacklistCache.get().matches(target.javaClass)

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
        val totalLimit = when {
            slotLimit < currentItemLimit -> slotLimit
            currentItemLimit < slotLimit && currentItemLimit != VANILLA_STACK_LIMIT -> currentItemLimit
            else -> slotLimit
        }
        return minOf(simulatedLimit, totalLimit)
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

private class InventoryStackLimitBlacklistCache private constructor(private val root: PackageNode) {
    private val classResults = ConcurrentHashMap<Class<*>, Boolean>()

    fun matches(targetClass: Class<*>): Boolean = classResults.computeIfAbsent(targetClass) { matchesPackage(it.name) }

    fun matchesAny(): Boolean = root.children.isNotEmpty()

    private fun matchesPackage(className: String): Boolean {
        var node = root
        for (character in className) {
            if (character == '.') {
                if (node.terminal) {
                    return true
                }
                node = node.children[character] ?: return false
                continue
            }
            node = node.children[Character.toLowerCase(character)] ?: return false
        }
        return node.terminal
    }

    companion object {
        fun empty(): InventoryStackLimitBlacklistCache = from(emptyList())

        fun from(packageNames: Iterable<String>): InventoryStackLimitBlacklistCache {
            val root = PackageNode()
            for (rawPackageName in packageNames) {
                val packageName = rawPackageName.trim().lowercase(Locale.ROOT)
                if (packageName.isEmpty()) {
                    continue
                }
                var node = root
                for (character in packageName) {
                    node = node.children.getOrPut(character) { PackageNode() }
                }
                node.terminal = true
            }
            return InventoryStackLimitBlacklistCache(root)
        }
    }

    private class PackageNode {
        val children: HashMap<Char, PackageNode> = HashMap()
        var terminal: Boolean = false
    }
}

/**
 * ItemStack 的内容可观察键（T4b）。
 *
 * 只取内容分量（itemId/meta/count/NBT），不引用 ItemStack 实例；
 * NBT 分量依赖 NBTTagCompound 的深比较 equals/hashCode（1.12.2 原版已实现）。
 * 原地变异 NBT 会让 hashCode 变化，条目变为不可达，读取自然 miss，保证变异后重新解析。
 * itemId 取注册名；未注册物品（仅测试场景）退回类名，避免不同物品类共享同一键。
 */
data class StackContentKey(val itemId: String?, val metadata: Int, val count: Int, val tagCompound: NBTTagCompound?) {
    companion object {
        @JvmStatic
        fun fromStack(stack: ItemStack): StackContentKey {
            val item = stack.item
            return StackContentKey(
                itemId = item.registryName?.toString() ?: item.javaClass.name,
                metadata = stack.metadata,
                count = stack.count,
                tagCompound = stack.tagCompound,
            )
        }
    }
}
