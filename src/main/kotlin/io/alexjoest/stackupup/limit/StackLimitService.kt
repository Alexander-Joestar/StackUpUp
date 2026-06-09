package io.alexjoest.stackupup.limit

import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.compile.RuntimeContextRequirements
import io.alexjoest.stackupup.rules.field.RuleFieldCacheContext
import io.alexjoest.stackupup.rules.model.RuleMatchContext
import java.util.concurrent.ConcurrentHashMap

class StackLimitService(private val snapshot: RuleSnapshot) {
    private val cacheKeyFields = snapshot.requirements.cacheKeyFields

    // 规则求值是高频热路径。
    // 缓存键固定包含物品身份和原版基线；会随上下文变化的字段由 RuleField 自己声明并贡献缓存键。
    private val resolvedCache = ConcurrentHashMap<ResolvedLimitKey, Int>()

    fun resolve(context: StackContext): Int {
        if (!snapshot.hasRules) {
            return context.baseLimit.coerceIn(1, StackUpUpConfig.activeMaxStackSize)
        }

        val fieldCacheKey = buildFieldCacheKey(context)
        val key = ResolvedLimitKey(
            context.itemId,
            context.modId,
            context.metadata,
            context.type,
            context.baseLimit,
            fieldCacheKey,
        )
        resolvedCache[key]?.let { return it }

        val matchContext = RuleMatchContext(
            itemId = context.itemId,
            modId = context.modId,
            meta = context.metadata,
            baseSize = context.baseLimit,
            type = context.type,
            oreNames = context.oreNames,
            tab = context.tab,
            material = context.material,
        )

        var result = context.baseLimit
        for (rule in snapshot.rules) {
            if (rule.matches(matchContext)) {
                result = rule.action.apply(result)
            }
        }

        val resolved = result.coerceIn(1, StackUpUpConfig.activeMaxStackSize)
        val previous = resolvedCache.putIfAbsent(key, resolved)
        return previous ?: resolved
    }

    @Deprecated("Use resolve(StackContext)")
    fun resolve(
        identity: StackIdentity,
        baseLimit: Int,
        oreNames: Set<String>,
        tab: String = "",
        material: String = "",
    ): Int = resolve(
        StackContext(
            itemId = identity.itemId,
            modId = identity.modId,
            metadata = identity.meta,
            type = identity.type,
            baseLimit = baseLimit,
            oreNames = oreNames,
            tab = tab,
            material = material,
        )
    )

    fun hasRules(): Boolean = snapshot.hasRules

    fun needsOreNames(): Boolean = snapshot.needsOreNames

    fun needsMaterial(): Boolean = snapshot.needsMaterial

    fun contextRequirements(): RuntimeContextRequirements = snapshot.requirements.runtimeRequirements()

    fun debugResolvedCacheSize(): Int = resolvedCache.size

    // 这里故意不直接把 oreNames 放进缓存键：
    // 在当前 1.12.2 语义下，矿辞集合由 itemId + metadata 稳定决定；
    // 一旦规则快照或矿辞索引被替换，RuleRuntime 会整体刷新 StackLimitService，
    // 从而自然清空这层缓存。
    private fun buildFieldCacheKey(context: StackContext): Any {
        if (cacheKeyFields.isEmpty()) {
            return EMPTY_FIELD_CACHE_KEY
        }
        val fieldContext = RuleFieldCacheContext(
            itemId = context.itemId,
            modId = context.modId,
            metadata = context.metadata,
            type = context.type,
            baseLimit = context.baseLimit,
            tab = context.tab,
            material = context.material,
        )
        return when (cacheKeyFields.size) {
            1 -> cacheKeyFields[0].cacheKeyValue(fieldContext)
            2 -> PairFieldCacheKey(
                cacheKeyFields[0].cacheKeyValue(fieldContext),
                cacheKeyFields[1].cacheKeyValue(fieldContext),
            )
            else -> MultiFieldCacheKey(Array(cacheKeyFields.size) { index ->
                cacheKeyFields[index].cacheKeyValue(fieldContext)
            })
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

    private object EmptyFieldCacheKey

    private data class PairFieldCacheKey(val first: String, val second: String)

    private class MultiFieldCacheKey(private val values: Array<String>) {
        override fun equals(other: Any?): Boolean =
            this === other || other is MultiFieldCacheKey && values.contentEquals(other.values)

        override fun hashCode(): Int = values.contentHashCode()
    }

    private companion object {
        private val EMPTY_FIELD_CACHE_KEY = EmptyFieldCacheKey
    }
}
