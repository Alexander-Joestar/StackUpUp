package io.alexjoest.stackupup.rules.field

import io.alexjoest.stackupup.limit.GregTechMaterialResolver
import io.alexjoest.stackupup.limit.RuleRuntime
import net.minecraft.item.ItemStack

/**
 * 规则字段运行时上下文的按需采集器。
 *
 * Provider 是字段声明的一部分；Resolver 只执行计划，不按字段名分发。
 */
enum class RuleFieldContextProvider(val collect: (ItemStack, StackContextFields) -> Unit) {
    ORE_NAMES({ stack, fields ->
        fields.oreNames = RuleRuntime.oreDictIndex().getOreNames(stack)
    }),

    MATERIAL({ stack, fields ->
        fields.material = GregTechMaterialResolver.resolveMaterial(stack)
    }),

    TAB({ stack, fields ->
        fields.tab = stack.item.creativeTab?.tabLabel.orEmpty()
    }),
}

/**
 * StackContext 的可选字段收集结果。
 *
 * 固定 typed slot 避免在规则热路径上引入 Map<String, Any?>。
 */
class StackContextFields {
    var oreNames: Set<String> = emptySet()
    var material: String = ""
    var tab: String = ""
}
