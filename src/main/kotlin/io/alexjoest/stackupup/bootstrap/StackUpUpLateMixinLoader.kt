package io.alexjoest.stackupup.bootstrap

import io.alexjoest.stackupup.StackUpUpIds
import zone.rong.mixinbooter.Context
import zone.rong.mixinbooter.ILateMixinLoader

class StackUpUpLateMixinLoader : ILateMixinLoader {
    override fun getMixinConfigs(): List<String> = modules.map(LateMixinModule::config)

    override fun shouldMixinConfigQueue(context: Context): Boolean {
        val module = modules.firstOrNull { it.config == context.mixinConfig() } ?: return true
        return context.isModPresent(module.modId)
    }

    private data class LateMixinModule(
        val config: String,
        val modId: String
    )

    companion object {
        // 这里只登记“可按模组维度启停”的 late mixin 模块。
        // 它和 FixedCompatTargets 不是同一个概念：
        // 前者负责告诉 MixinBooter 要不要装载某个 late 配置文件，
        // 后者负责告诉 dynamic ASM 哪些固定目标必须跳过，避免双重补丁。
        // 这些模块现在默认“检测到对应模组就装载”，
        // 不再暴露误导性的普通配置开关；若以后要移除某个兼容模块，
        // 直接删掉对应条目或配置文件即可。
        private val modules: List<LateMixinModule> = listOf(
            LateMixinModule(
                StackUpUpIds.LATE_AE2_MIXIN_CONFIG,
                "appliedenergistics2"
            ),
            LateMixinModule(
                StackUpUpIds.LATE_BRANDONSCORE_MIXIN_CONFIG,
                "brandonscore"
            ),
            LateMixinModule(
                StackUpUpIds.LATE_ACTUALLY_ADDITIONS_MIXIN_CONFIG,
                "actuallyadditions"
            ),
            LateMixinModule(
                StackUpUpIds.LATE_CYCLOPSCORE_MIXIN_CONFIG,
                "cyclopscore"
            ),
            LateMixinModule(
                StackUpUpIds.LATE_ENDERIO_MIXIN_CONFIG,
                "enderio"
            ),
            LateMixinModule(
                StackUpUpIds.LATE_IC2_MIXIN_CONFIG,
                "ic2"
            ),
            LateMixinModule(
                StackUpUpIds.LATE_MANTLE_MIXIN_CONFIG,
                "mantle"
            ),
            LateMixinModule(
                StackUpUpIds.LATE_REFINED_STORAGE_MIXIN_CONFIG,
                "refinedstorage"
            )
        )
    }
}
