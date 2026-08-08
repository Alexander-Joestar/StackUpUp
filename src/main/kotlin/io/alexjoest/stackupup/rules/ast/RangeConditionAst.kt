package io.alexjoest.stackupup.rules.ast

import io.alexjoest.stackupup.rules.RuleField

/**
 * 区间比较 AST：`lower (</<=) field (</<=) upper` 的原始比较表示。
 *
 * lower/upper 保持字符串字面量，编译期按字段 matcher 编译为两条原始比较，
 * 不做运行时 IntRange 实例化；与旧 AndConditionAst 脱糖的求值语义一致。
 * 边界朝向由解析器按操作符方向归一：lower 恒为 field 的下界，upper 恒为上界。
 */
data class RangeConditionAst(val field: RuleField, val lower: String, val lowerInclusive: Boolean, val upper: String, val upperInclusive: Boolean) :
    ConditionAst {
    override fun debugFields(): List<RuleField> = listOf(field)
    override fun debugLiteralCount(): Int = 2
}
