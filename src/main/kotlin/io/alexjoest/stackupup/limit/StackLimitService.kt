package io.alexjoest.stackupup.limit

import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.field.RuleFieldCacheContext
import io.alexjoest.stackupup.rules.model.RuleMatchContext
import java.util.concurrent.ConcurrentHashMap

class StackLimitService(private val snapshot: RuleSnapshot) {
    private val cacheKeyFields = snapshot.requirements.cacheKeyFieldsInOrder

    // 规则求值是高频热路径。
    // 缓存键固定包含物品身份和原版基线；会随上下文变化的字段由 RuleField 自己声明并贡献缓存键。
    private val resolvedCache = ConcurrentHashMap<ResolvedLimitKey, Int>()

    fun resolve(context: StackContext): Int = resolve(
        itemId = context.itemId,
        modId = context.modId,
        metadata = context.metadata,
        type = context.type,
        baseLimit = context.baseLimit,
        oreNames = context.oreNames,
        tab = context.tab,
        material = context.material,
    )

    fun resolve(
        identity: StackIdentity,
        baseLimit: Int,
        oreNames: Set<String>,
        tab: String = "",
        material: String = "",
    ): Int = resolve(
        itemId = identity.itemId,
        modId = identity.modId,
        metadata = identity.meta,
        type = identity.type,
        baseLimit = baseLimit,
        oreNames = oreNames,
        tab = tab,
        material = material,
    )

    fun resolve(
        itemId: String,
        modId: String,
        metadata: Int,
        type: String,
        baseLimit: Int,
        oreNames: Set<String>,
        tab: String = "",
        material: String = "",
    ): Int {
        val fieldCacheKey = buildFieldCacheKey(itemId, modId, metadata, type, baseLimit, tab, material)
        val key = ResolvedLimitKey(
            itemId,
            modId,
            metadata,
            type,
            baseLimit,
            fieldCacheKey,
        )
        resolvedCache[key]?.let { return it }

        val matchContext = RuleMatchContext(
            itemId = itemId,
            modId = modId,
            meta = metadata,
            baseSize = baseLimit,
            type = type,
            oreNames = oreNames,
            tab = tab,
            material = material,
        )

        var result = baseLimit
        for (rule in snapshot.rules) {
            if (rule.matches(matchContext)) {
                result = rule.action.apply(result)
            }
        }

        val resolved = result.coerceIn(1, StackUpUpConfig.activeMaxStackSize)
        val previous = resolvedCache.putIfAbsent(key, resolved)
        return previous ?: resolved
    }

    fun hasRules(): Boolean = snapshot.hasRules

    fun needsOreNames(): Boolean = snapshot.needsOreNames

    fun needsMaterial(): Boolean = snapshot.needsMaterial

    fun debugResolvedCacheSize(): Int = resolvedCache.size

    // 这里故意不直接把 oreNames 放进缓存键：
    // 在当前 1.12.2 语义下，矿辞集合由 itemId + metadata 稳定决定；
    // 一旦规则快照或矿辞索引被替换，RuleRuntime 会整体刷新 StackLimitService，
    // 从而自然清空这层缓存。
    private fun buildFieldCacheKey(
        itemId: String,
        modId: String,
        metadata: Int,
        type: String,
        baseLimit: Int,
        tab: String,
        material: String,
    ): Any {
        if (cacheKeyFields.isEmpty()) {
            return EMPTY_FIELD_CACHE_KEY
        }
        val context = RuleFieldCacheContext(
            itemId = itemId,
            modId = modId,
            metadata = metadata,
            type = type,
            baseLimit = baseLimit,
            tab = tab,
            material = material,
        )
        if (cacheKeyFields.size == 1) {
            return cacheKeyFields[0].cacheKeyValue(context)
        }
        return ArrayList<String>(cacheKeyFields.size).apply {
            for (field in cacheKeyFields) {
                add(field.cacheKeyValue(context))
            }
        }
    }

    private data class ResolvedLimitKey(
        val itemId: String,
        val modId: String,
        val metadata: Int,
        val type: String,
        val baseLimit: Int,
        val fieldValues: Any,
    )

    private companion object {
        private val EMPTY_FIELD_CACHE_KEY = emptyList<String>()
    }
}
