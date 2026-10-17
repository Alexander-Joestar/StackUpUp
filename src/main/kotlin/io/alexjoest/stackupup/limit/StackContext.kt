package io.alexjoest.stackupup.limit

import io.alexjoest.stackupup.rules.compile.RuntimeContextRequirements
import io.alexjoest.stackupup.rules.field.RuleFieldContextProvider
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack

/**
 * 统一的堆叠规则求值上下文。
 *
 * 所有“某个物品栈当前允许多少堆叠”的计算，
 * 都先收敛为这个结构，再交给规则引擎处理。
 *
 * 从 [ItemStack] 构造时，可选字段只在首次访问时计算；直接传入的字段值仍可用于
 * 测试和不需要物品栈的调用方。
 */
class StackContext(
    val itemId: String,
    val modId: String,
    val metadata: Int,
    type: String = "",
    val baseLimit: Int,
    oreNames: Set<String> = emptySet(),
    tab: String = "",
    material: String = "",
    private val stack: ItemStack? = null,
    private val requirements: RuntimeContextRequirements = RuntimeContextRequirements.DEFAULT_STACK_CONTEXT,
) {
    private val directType = type
    private val directOreNames = oreNames
    private val directTab = tab
    private val directMaterial = material

    val type: String by lazy(LazyThreadSafetyMode.NONE) {
        if (stack != null && requirements.needsType) {
            if (stack.item is ItemBlock) "block" else "item"
        } else {
            directType
        }
    }

    val oreNames: Set<String> by lazy(LazyThreadSafetyMode.NONE) {
        if (stack != null && requirements.requires(RuleFieldContextProvider.ORE_NAMES)) {
            RuleRuntime.oreDictIndex().getOreNames(stack)
        } else {
            directOreNames
        }
    }

    val tab: String by lazy(LazyThreadSafetyMode.NONE) {
        if (stack != null && requirements.requires(RuleFieldContextProvider.TAB)) {
            stack.item.creativeTab?.tabLabel.orEmpty()
        } else {
            directTab
        }
    }

    val material: String by lazy(LazyThreadSafetyMode.NONE) {
        if (stack != null && requirements.requires(RuleFieldContextProvider.MATERIAL)) {
            GregTechMaterialResolver.resolveMaterial(stack)
        } else {
            directMaterial
        }
    }

    fun copy(
        itemId: String = this.itemId,
        modId: String = this.modId,
        metadata: Int = this.metadata,
        type: String = this.type,
        baseLimit: Int = this.baseLimit,
        oreNames: Set<String> = this.oreNames,
        tab: String = this.tab,
        material: String = this.material,
    ): StackContext = StackContext(
        itemId = itemId,
        modId = modId,
        metadata = metadata,
        type = type,
        baseLimit = baseLimit,
        oreNames = oreNames,
        tab = tab,
        material = material,
    )

    operator fun component1(): String = itemId

    operator fun component2(): String = modId

    operator fun component3(): Int = metadata

    operator fun component4(): String = type

    operator fun component5(): Int = baseLimit

    operator fun component6(): Set<String> = oreNames

    operator fun component7(): String = tab

    operator fun component8(): String = material

    override fun equals(other: Any?): Boolean = this === other ||
        other is StackContext &&
        itemId == other.itemId &&
        modId == other.modId &&
        metadata == other.metadata &&
        type == other.type &&
        baseLimit == other.baseLimit &&
        oreNames == other.oreNames &&
        tab == other.tab &&
        material == other.material

    override fun hashCode(): Int {
        var result = itemId.hashCode()
        result = 31 * result + modId.hashCode()
        result = 31 * result + metadata
        result = 31 * result + type.hashCode()
        result = 31 * result + baseLimit
        result = 31 * result + oreNames.hashCode()
        result = 31 * result + tab.hashCode()
        result = 31 * result + material.hashCode()
        return result
    }

    override fun toString(): String = "StackContext(" +
        "itemId=$itemId, " +
        "modId=$modId, " +
        "metadata=$metadata, " +
        "type=$type, " +
        "baseLimit=$baseLimit, " +
        "oreNames=$oreNames, " +
        "tab=$tab, " +
        "material=$material)"

    fun toIdentity(): StackIdentity = StackIdentity(itemId, modId, metadata, type)
}
