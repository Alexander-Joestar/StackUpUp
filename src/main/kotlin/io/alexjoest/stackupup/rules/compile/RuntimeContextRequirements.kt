package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.RuleContextRequirement

/**
 * 运行时构造 `StackContext` 时需要采集的昂贵字段集合。
 */
data class RuntimeContextRequirements(
    private val requirements: Set<RuleContextRequirement>,
) {
    /**
     * 判断指定昂贵字段是否需要在当前规则快照下采集。
     */
    fun requires(requirement: RuleContextRequirement): Boolean = requirement in requirements

    companion object {
        /**
         * 不采集任何额外昂贵字段。
         */
        val EMPTY: RuntimeContextRequirements = RuntimeContextRequirements(emptySet())

        /**
         * 直接从 ItemStack 构造上下文时的兼容默认值：保留矿辞查询。
         */
        val DEFAULT_STACK_CONTEXT: RuntimeContextRequirements =
            RuntimeContextRequirements(setOf(RuleContextRequirement.ORE_NAMES))

        /**
         * 从编译期字段需求创建运行时需求集合。
         */
        fun of(vararg requirements: RuleContextRequirement): RuntimeContextRequirements =
            RuntimeContextRequirements(requirements.toCollection(LinkedHashSet()))
    }
}
