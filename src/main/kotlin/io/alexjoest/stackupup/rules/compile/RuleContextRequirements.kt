package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.RuleField
import io.alexjoest.stackupup.rules.field.FieldMatcher
import io.alexjoest.stackupup.rules.field.RuleFieldContextProvider

/**
 * 规则快照的上下文与缓存键需求。
 *
 * - [referencedFields]：规则引用的字段集合（AST 投影，驱动运行时 provider 采集计划）；
 * - [cacheKeyFields]：字段缓存键材料，由 matcher 树 [FieldMatcher.readFields] 机械推导
 *   后按字段声明的 [CacheKeyStrategy] 过滤（T6），不再手工维护；
 * - [readFields]：matcher 树实际读取的字段集合（缓存键推导的唯一事实源）。
 */
data class RuleContextRequirements(val referencedFields: Set<RuleField>, val cacheKeyFields: List<RuleField>, val readFields: Set<RuleField>) {
    private val runtimeRequirementsCache: RuntimeContextRequirements =
        RuntimeContextRequirements.fromFields(referencedFields)

    fun runtimeRequirements(): RuntimeContextRequirements = runtimeRequirementsCache

    val needsOreNames: Boolean = runtimeRequirementsCache.requires(RuleFieldContextProvider.ORE_NAMES)
    val needsMaterial: Boolean = runtimeRequirementsCache.requires(RuleFieldContextProvider.MATERIAL)

    /**
     * matcher 树是否读取了 ORE 字段。
     *
     * 快路径（零分配表）不承载 ORE 的“身份稳定性 + 索引替换失效”契约，含 ORE 的快照一律走慢路径。
     */
    val readsOre: Boolean = RuleField.ORE in readFields

    companion object {
        fun fromRules(rules: List<CompiledRule>): RuleContextRequirements {
            val readFields = rules.flatMapTo(LinkedHashSet()) { it.matcher.readFields() }

            // T6 fail-fast：读取字段必须被缓存键覆盖。字段枚举必填 CacheKeyStrategy 是编译期护栏，
            // 此处是快照构造期的结构性兜底检查：任何未被覆盖的读取字段直接抛错，不得静默共享错误缓存条目。
            val uncovered = readFields.filterNot(RuleField::isCacheKeyCovered)
            check(uncovered.isEmpty()) {
                "规则读取了未被缓存键覆盖的字段: " + uncovered.joinToString { it.name }
            }

            val referencedFields = rules.flatMapTo(LinkedHashSet(), CompiledRule::referencedFields)
            val cacheKeyFields = readFields.filterTo(LinkedHashSet()) { it.contributesToCacheKey() }
            return RuleContextRequirements(referencedFields, cacheKeyFields.toList(), readFields)
        }
    }
}
