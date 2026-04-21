package io.alexjoest.stackupup.bootstrap

import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.StackUpUpConfig
import kotlin.reflect.KProperty0
import zone.rong.mixinbooter.Context
import zone.rong.mixinbooter.ILateMixinLoader

class StackUpUpLateMixinLoader : ILateMixinLoader {
    override fun getMixinConfigs(): List<String> = modules.map(LateMixinModule::config)

    override fun shouldMixinConfigQueue(context: Context): Boolean {
        val module = modules.firstOrNull { it.config == context.mixinConfig() } ?: return true
        return module.enabled.get() && context.isModPresent(module.modId)
    }

    private data class LateMixinModule(
        val config: String,
        val modId: String,
        val enabled: KProperty0<Boolean>
    )

    companion object {
        // 这里只登记“可按模组维度启停”的 late mixin 模块。
        // 它和 FixedCompatTargets 不是同一个概念：
        // 前者负责告诉 MixinBooter 要不要装载某个 late 配置文件，
        // 后者负责告诉 dynamic ASM 哪些固定目标必须跳过，避免双重补丁。
        // 当前 Refined Storage 的已知固定目标已经迁入这里，
        // 因此 transformer 中不再保留它们的旧前缀 ASM 分支。
        private val modules: List<LateMixinModule> = listOf(
            LateMixinModule(
                StackUpUpIds.LATE_AE2_MIXIN_CONFIG,
                "appliedenergistics2",
                StackUpUpConfig::coremodPatchAppliedEnergistics2
            ),
            LateMixinModule(
                StackUpUpIds.LATE_ACTUALLY_ADDITIONS_MIXIN_CONFIG,
                "actuallyadditions",
                StackUpUpConfig::coremodPatchActuallyAdditions
            ),
            LateMixinModule(
                StackUpUpIds.LATE_CYCLOPSCORE_MIXIN_CONFIG,
                "cyclopscore",
                StackUpUpConfig::coremodPatchCyclopsCore
            ),
            LateMixinModule(
                StackUpUpIds.LATE_IC2_MIXIN_CONFIG,
                "ic2",
                StackUpUpConfig::coremodPatchIc2
            ),
            LateMixinModule(
                StackUpUpIds.LATE_MANTLE_MIXIN_CONFIG,
                "mantle",
                StackUpUpConfig::coremodPatchMantle
            ),
            LateMixinModule(
                StackUpUpIds.LATE_REFINED_STORAGE_MIXIN_CONFIG,
                "refinedstorage",
                StackUpUpConfig::coremodPatchRefinedStorage
            )
        )
    }
}
