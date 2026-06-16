package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.RuleContextRequirement
import io.alexjoest.stackupup.rules.RuleField
import io.alexjoest.stackupup.rules.field.RuleFieldContextProvider

/**
 * 运行时构造 `StackContext` 时需要采集的昂贵字段集合。
 */
data class RuntimeContextRequirements(
    val providers: List<RuleFieldContextProvider>,
) {
    /**
     * 判断指定昂贵字段是否需要在当前规则快照下采集。
     */
    fun requires(provider: RuleFieldContextProvider): Boolean = provider in providers

    /**
     * 兼容旧的 requirement 查询；新增字段应优先声明 RuleField.contextProviders。
     */
    fun requires(requirement: RuleContextRequirement): Boolean = requires(requirement.provider)

    companion object {
        /**
         * 不采集任何额外昂贵字段。
         */
        val EMPTY: RuntimeContextRequirements = RuntimeContextRequirements(emptyList())

        /**
         * 直接从 ItemStack 构造上下文时的兼容默认值：保留矿辞查询。
         */
        val DEFAULT_STACK_CONTEXT: RuntimeContextRequirements =
            RuntimeContextRequirements(listOf(RuleFieldContextProvider.ORE_NAMES))

        /**
         * 从规则字段声明创建运行时采集计划。
         */
        fun fromFields(fields: Iterable<RuleField>): RuntimeContextRequirements {
            val providers = fields
                .flatMapTo(LinkedHashSet(), RuleField::contextProviders)
            return fromProviders(providers)
        }

        fun fromProviders(providers: Iterable<RuleFieldContextProvider>): RuntimeContextRequirements =
            RuntimeContextRequirements(providers.toCollection(LinkedHashSet()).toList())

        /**
         * 从编译期字段需求创建运行时需求集合。
         */
        fun of(vararg requirements: RuleContextRequirement): RuntimeContextRequirements =
            fromProviders(requirements.map(RuleContextRequirement::provider))
    }
}
