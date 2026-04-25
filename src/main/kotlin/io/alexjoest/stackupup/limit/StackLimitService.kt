package io.alexjoest.stackupup.limit

import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.model.RuleMatchContext
import java.util.concurrent.ConcurrentHashMap

class StackLimitService(
    private val snapshot: RuleSnapshot
) {
    // 规则求值是高频热路径。
    // 当前 DSL 只依赖物品标识、metadata、类型、原版基线和由其稳定导出的矿辞集合，
    // 因此这里按“物品身份 + 原版基线”缓存结果，避免反复构造 RuleMatchContext 并遍历整条规则链。
    private val resolvedCache = ConcurrentHashMap<ResolvedLimitKey, Int>()

    fun resolve(context: StackContext): Int {
        val key = ResolvedLimitKey.from(context)
        return resolvedCache.computeIfAbsent(key) {
            val matchContext = RuleMatchContext(
                itemId = context.itemId,
                modId = context.modId,
                meta = context.metadata,
                baseSize = context.baseLimit,
                type = context.type,
                oreNames = context.oreNames
            )

            var result = context.baseLimit
            for (rule in snapshot.rules) {
                if (rule.matches(matchContext)) {
                    result = rule.action.apply(result)
                }
            }

            result.coerceIn(1, StackUpUpConfig.maxStackSize)
        }
    }

    fun resolve(identity: StackIdentity, baseLimit: Int, oreNames: Set<String>): Int {
        return resolve(
            StackContext(
                itemId = identity.itemId,
                modId = identity.modId,
                metadata = identity.meta,
                type = identity.type,
                baseLimit = baseLimit,
                oreNames = oreNames
            )
        )
    }

    fun hasRules(): Boolean = snapshot.hasRules

    fun needsOreNames(): Boolean = snapshot.needsOreNames

    fun debugResolvedCacheSize(): Int = resolvedCache.size

    private data class ResolvedLimitKey(
        val itemId: String,
        val modId: String,
        val metadata: Int,
        val type: String,
        val baseLimit: Int
    ) {
        companion object {
            fun from(context: StackContext): ResolvedLimitKey {
                // 这里故意不直接把 oreNames 放进缓存键：
                // 在当前 1.12.2 语义下，矿辞集合由 itemId + metadata 稳定决定；
                // 一旦规则快照或矿辞索引被替换，RuleRuntime 会整体刷新 StackLimitService，
                // 从而自然清空这层缓存。
                return ResolvedLimitKey(
                    itemId = context.itemId,
                    modId = context.modId,
                    metadata = context.metadata,
                    type = context.type,
                    baseLimit = context.baseLimit
                )
            }
        }
    }
}


