package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.limit.RuleRuntime
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.registry.ForgeRegistries
import net.minecraftforge.oredict.OreDictionary

/**
 * 运行时目标栈解析器。
 *
 * 负责把“显式 item/meta 或矿辞名”解析成当前真实存在的物品栈，
 * 供客户端自动验收和服务端自动探针共用。
 */
object DevTargetRuntimeResolver {
    fun resolve(): ResolvedDevTarget? = resolve(
        DevProbeTargetSpec(
            name = "Default",
            oreName = DevAutomationConfig.oreName,
            itemId = DevAutomationConfig.itemId.ifBlank { null },
            metadata = DevAutomationConfig.itemMeta,
        ),
    )

    fun resolve(spec: DevProbeTargetSpec): ResolvedDevTarget? {
        val explicitCandidate = resolveExplicitCandidate(spec)
        val oreCandidates = resolveOreCandidates(spec)
        val selected = selectCandidate(
            explicitItemId = spec.itemId,
            explicitMeta = spec.metadata ?: DevAutomationConfig.itemMeta,
            preferredOreName = spec.oreName ?: "",
            candidates = buildList {
                explicitCandidate?.let { add(it.toCandidate()) }
                addAll(oreCandidates.map { it.toCandidate() })
            },
        ) ?: return null

        return sequenceOf(explicitCandidate)
            .filterNotNull()
            .plus(oreCandidates.asSequence())
            .firstOrNull { it.itemId == selected.itemId && it.meta == selected.meta }
    }

    private fun resolveExplicitCandidate(spec: DevProbeTargetSpec): ResolvedDevTarget? {
        val itemId = spec.itemId ?: return null
        val metadata = spec.metadata ?: return null
        if (itemId.isBlank()) {
            return null
        }

        val item = try {
            ForgeRegistries.ITEMS.getValue(ResourceLocation(itemId))
        } catch (_: IllegalArgumentException) {
            null
        } ?: return null

        return ResolvedDevTarget(
            itemId = itemId,
            meta = metadata,
            stack = ItemStack(item, 1, metadata),
        )
    }

    private fun resolveOreCandidates(spec: DevProbeTargetSpec): List<ResolvedDevTarget> {
        val oreName = spec.oreName ?: return emptyList()
        return OreDictionary.getOres(oreName, false)
            .asSequence()
            .filterNot { it.isEmpty }
            .map { it.copy() }
            .mapNotNull { stack ->
                val itemId = stack.item.registryName?.toString() ?: return@mapNotNull null
                ResolvedDevTarget(itemId = itemId, meta = stack.metadata, stack = stack)
            }
            .toList()
    }

    private fun ResolvedDevTarget.toCandidate(): DevTargetCandidate = DevTargetCandidate(
        itemId = itemId,
        meta = meta,
        oreNames = RuleRuntime.oreDictIndex().getOreNames(stack),
    )

    /**
     * 开发期目标选择策略只服务自动验收，
     * 并回到解析器内部后可以减少跨文件跳转。
     */
    internal fun selectCandidate(
        explicitItemId: String?,
        explicitMeta: Int,
        preferredOreName: String,
        candidates: List<DevTargetCandidate>,
    ): DevTargetCandidate? {
        val normalizedItemId = explicitItemId?.takeIf(String::isNotBlank)
        if (normalizedItemId != null) {
            candidates.firstOrNull { it.itemId == normalizedItemId && it.meta == explicitMeta }?.let { return it }
        }

        val oreMatches = candidates.filter { preferredOreName in it.oreNames }
        oreMatches.firstOrNull { it.itemId.startsWith("gregtech:") }?.let { return it }
        return oreMatches.firstOrNull()
    }
}

data class ResolvedDevTarget(val itemId: String, val meta: Int, val stack: ItemStack)

internal data class DevTargetCandidate(val itemId: String, val meta: Int, val oreNames: Set<String>)
