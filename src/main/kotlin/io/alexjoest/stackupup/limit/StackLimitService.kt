package io.alexjoest.stackupup.limit

import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.compile.RuntimeContextRequirements
import java.util.concurrent.ConcurrentHashMap

/** 用途：按规则快照求值物品堆叠上限的热路径服务，带缓存。 */
class StackLimitService internal constructor(private val snapshot: RuleSnapshot, private val forceSlowPath: Boolean) {
    constructor(snapshot: RuleSnapshot) : this(snapshot, forceSlowPath = false)

    // T6：cacheKeyFields 由 matcher 树机械推导（RuleContextRequirements.fromRules 按 CacheKeyStrategy 过滤）。
    private val cacheKeyFields = snapshot.requirements.cacheKeyFields

    // 快慢路径在快照替换时定型（构造即定型，RuleRuntime.replaceRuntime 创建本实例时执行），热路径不重新选择。
    // 快路径：字段键材料 ≤1 且不读 ORE；其余（多字段 / 含 ORE）走 ConcurrentHashMap 慢路径。
    private val useFastPath = !forceSlowPath && cacheKeyFields.size <= 1 && !snapshot.requirements.readsOre
    private val fastCache: FastResolvedCache? = if (useFastPath) FastResolvedCache() else null
    private val resolvedCache = ConcurrentHashMap<ResolvedLimitKey, Int>()

    fun resolve(context: StackContext): Int {
        if (!snapshot.hasRules) {
            return context.baseLimit.coerceIn(1, StackUpUpConfig.maxStackSize)
        }

        val fast = fastCache
        if (fast != null) {
            return resolveFast(fast, context)
        }
        return resolveSlow(context)
    }

    /**
     * 快路径：命中时不构造任何中间键对象（[FastResolvedCache] 分量内联、开放寻址）。
     */
    private fun resolveFast(fast: FastResolvedCache, context: StackContext): Int {
        val fieldValue = if (cacheKeyFields.isEmpty()) "" else cacheKeyFields[0].cacheKeyValue(context)
        val cached = fast.get(
            context.itemId,
            context.modId,
            context.metadata,
            context.type,
            context.baseLimit,
            fieldValue,
        )
        if (cached != FastResolvedCache.MISS) {
            return cached
        }
        val resolved = evaluate(context)
        fast.put(
            context.itemId,
            context.modId,
            context.metadata,
            context.type,
            context.baseLimit,
            fieldValue,
            resolved,
        )
        return resolved
    }

    /**
     * 慢路径：多字段键材料或含 ORE 的快照。
     *
     * ORE 不进键由 [io.alexjoest.stackupup.rules.CacheKeyStrategy.STABLE_VIA_IDENTITY] 显式声明：
     * 矿辞集合由 itemId+metadata 稳定决定；矿辞索引被替换时 RuleRuntime 整体换新本服务实例，
     * 缓存随之清空（机制在 RuleRuntime.replaceRuntime，不是注释约定）。
     */
    private fun resolveSlow(context: StackContext): Int {
        val key = ResolvedLimitKey(
            context.itemId,
            context.modId,
            context.metadata,
            context.type,
            context.baseLimit,
            buildFieldCacheKey(context),
        )
        resolvedCache[key]?.let { return it }

        val resolved = evaluate(context)
        val previous = resolvedCache.putIfAbsent(key, resolved)
        return previous ?: resolved
    }

    private fun evaluate(context: StackContext): Int {
        var result = context.baseLimit
        for (rule in snapshot.rules) {
            if (rule.matches(context)) {
                result = rule.action.apply(result)
            }
        }
        return result.coerceIn(1, StackUpUpConfig.maxStackSize)
    }

    @Deprecated("Use resolve(StackContext)")
    fun resolve(identity: StackIdentity, baseLimit: Int, oreNames: Set<String>, tab: String = "", material: String = ""): Int = resolve(
        StackContext(
            itemId = identity.itemId,
            modId = identity.modId,
            metadata = identity.meta,
            type = identity.type,
            baseLimit = baseLimit,
            oreNames = oreNames,
            tab = tab,
            material = material,
        ),
    )

    fun hasRules(): Boolean = snapshot.hasRules

    fun needsOreNames(): Boolean = snapshot.needsOreNames

    fun needsMaterial(): Boolean = snapshot.needsMaterial

    fun contextRequirements(): RuntimeContextRequirements = snapshot.requirements.runtimeRequirements()

    fun debugResolvedCacheSize(): Int = (fastCache?.size ?: 0) + resolvedCache.size

    internal fun usesFastPath(): Boolean = fastCache != null

    private fun buildFieldCacheKey(context: StackContext): Any {
        if (cacheKeyFields.isEmpty()) {
            return EMPTY_FIELD_CACHE_KEY
        }
        return when (cacheKeyFields.size) {
            1 -> cacheKeyFields[0].cacheKeyValue(context)
            2 -> PairFieldCacheKey(
                cacheKeyFields[0].cacheKeyValue(context),
                cacheKeyFields[1].cacheKeyValue(context),
            )
            else -> MultiFieldCacheKey(
                Array(cacheKeyFields.size) { index ->
                    cacheKeyFields[index].cacheKeyValue(context)
                },
            )
        }
    }

    private data class ResolvedLimitKey(val itemId: String, val modId: String, val metadata: Int, val type: String, val baseLimit: Int, val fieldValues: Any)

    private object EmptyFieldCacheKey

    private data class PairFieldCacheKey(val first: String, val second: String)

    private class MultiFieldCacheKey(private val values: Array<String>) {
        override fun equals(other: Any?): Boolean = this === other || other is MultiFieldCacheKey && values.contentEquals(other.values)

        override fun hashCode(): Int = values.contentHashCode()
    }

    private companion object {
        private val EMPTY_FIELD_CACHE_KEY = EmptyFieldCacheKey
    }
}

/**
 * 快路径零分配缓存表（T6）。
 *
 * 开放寻址、分量内联：槽位直接存键分量（itemId/modId/type 的 String 引用 + 原始 int），
 * 命中查找从 [StackContext] 分量直接计算 hash 与比较，不构造任何中间键对象（如慢路径的键包装）。
 * 表不删除条目：失效由快照替换整体换新服务实例驱动（RuleRuntime.replaceRuntime）。
 *
 * 线程安全：get/put 同步（无争用时为轻量锁路径）；put 对同键幂等。
 */
internal class FastResolvedCache(initialCapacity: Int = 256) {
    private var capacity: Int
    private var occupied: BooleanArray
    private var itemIds: Array<String?>
    private var modIds: Array<String?>
    private var types: Array<String?>
    private var metadata: IntArray
    private var baseLimits: IntArray
    private var fieldValues: Array<String?>
    private var results: IntArray
    private var entryCount = 0
    private var resizeThreshold: Int

    init {
        var cap = initialCapacity.coerceAtLeast(MIN_CAPACITY)
        if (cap and (cap - 1) != 0) {
            cap = Integer.highestOneBit(cap) shl 1
        }
        capacity = cap
        occupied = BooleanArray(cap)
        itemIds = arrayOfNulls(cap)
        modIds = arrayOfNulls(cap)
        types = arrayOfNulls(cap)
        metadata = IntArray(cap)
        baseLimits = IntArray(cap)
        fieldValues = arrayOfNulls(cap)
        results = IntArray(cap)
        resizeThreshold = cap * MAX_LOAD_PERCENT / 100
    }

    val size: Int
        @Synchronized get() = entryCount

    @Synchronized
    fun get(itemId: String, modId: String, meta: Int, type: String, baseLimit: Int, fieldValue: String): Int {
        val mask = capacity - 1
        var index = hash(itemId, modId, meta, type, baseLimit, fieldValue) and mask
        while (true) {
            if (!occupied[index]) {
                return MISS
            }
            if (itemIds[index] == itemId &&
                modIds[index] == modId &&
                types[index] == type &&
                metadata[index] == meta &&
                baseLimits[index] == baseLimit &&
                fieldValues[index] == fieldValue
            ) {
                return results[index]
            }
            index = (index + 1) and mask
        }
    }

    @Synchronized
    fun put(itemId: String, modId: String, meta: Int, type: String, baseLimit: Int, fieldValue: String, result: Int) {
        if (entryCount >= resizeThreshold) {
            resize()
        }
        val mask = capacity - 1
        var index = hash(itemId, modId, meta, type, baseLimit, fieldValue) and mask
        while (occupied[index]) {
            if (itemIds[index] == itemId &&
                modIds[index] == modId &&
                types[index] == type &&
                metadata[index] == meta &&
                baseLimits[index] == baseLimit &&
                fieldValues[index] == fieldValue
            ) {
                results[index] = result
                return
            }
            index = (index + 1) and mask
        }
        occupied[index] = true
        itemIds[index] = itemId
        modIds[index] = modId
        types[index] = type
        metadata[index] = meta
        baseLimits[index] = baseLimit
        fieldValues[index] = fieldValue
        results[index] = result
        entryCount++
    }

    private fun resize() {
        val newCapacity = capacity shl 1
        val newOccupied = BooleanArray(newCapacity)
        val newItemIds = arrayOfNulls<String>(newCapacity)
        val newModIds = arrayOfNulls<String>(newCapacity)
        val newTypes = arrayOfNulls<String>(newCapacity)
        val newMetadata = IntArray(newCapacity)
        val newBaseLimits = IntArray(newCapacity)
        val newFieldValues = arrayOfNulls<String>(newCapacity)
        val newResults = IntArray(newCapacity)
        val newMask = newCapacity - 1
        for (i in 0 until capacity) {
            if (occupied[i]) {
                var index = hash(
                    checkNotNull(itemIds[i]),
                    checkNotNull(modIds[i]),
                    metadata[i],
                    checkNotNull(types[i]),
                    baseLimits[i],
                    checkNotNull(fieldValues[i]),
                ) and newMask
                while (newOccupied[index]) {
                    index = (index + 1) and newMask
                }
                newOccupied[index] = true
                newItemIds[index] = itemIds[i]
                newModIds[index] = modIds[i]
                newTypes[index] = types[i]
                newMetadata[index] = metadata[i]
                newBaseLimits[index] = baseLimits[i]
                newFieldValues[index] = fieldValues[i]
                newResults[index] = results[i]
            }
        }
        capacity = newCapacity
        occupied = newOccupied
        itemIds = newItemIds
        modIds = newModIds
        types = newTypes
        metadata = newMetadata
        baseLimits = newBaseLimits
        fieldValues = newFieldValues
        results = newResults
        resizeThreshold = newCapacity * MAX_LOAD_PERCENT / 100
    }

    private fun hash(itemId: String, modId: String, meta: Int, type: String, baseLimit: Int, fieldValue: String): Int {
        var h = itemId.hashCode()
        h = h * 31 + modId.hashCode()
        h = h * 31 + meta
        h = h * 31 + type.hashCode()
        h = h * 31 + baseLimit
        h = h * 31 + fieldValue.hashCode()
        return h
    }

    companion object {
        const val MISS = -1
        private const val MIN_CAPACITY = 16
        private const val MAX_LOAD_PERCENT = 70
    }
}
