package io.alexjoest.stackupup.bootstrap

import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.config.EarlyCompatConfigReader
import zone.rong.mixinbooter.Context
import zone.rong.mixinbooter.ILateMixinLoader

class StackUpUpLateMixinLoader : ILateMixinLoader {
    override fun getMixinConfigs(): List<String> = modules.map { it.config }

    override fun shouldMixinConfigQueue(context: Context): Boolean {
        val module = modules.firstOrNull { it.config == context.mixinConfig() } ?: return true
        if (!context.isModPresent(module.modId)) {
            return false
        }
        // 注意：这里不能用 StackUpUpConfig.compatibility.*，
        // 因为 MixinBooter 在 @Config 注解填充之前就调用了此方法。
        // EarlyCompatConfigReader 直接读取原始 cfg 文本，不依赖 Forge @Config。
        return EarlyCompatConfigReader.isModuleEnabled(module.configName)
    }

    private data class LateMixinModule(val config: String, val modId: String, val configName: String)

    companion object {
        private val modules: List<LateMixinModule> = listOf(
            LateMixinModule(
                StackUpUpIds.LATE_AE2_MIXIN_CONFIG,
                "appliedenergistics2",
                "ae2",
            ),
            LateMixinModule(
                StackUpUpIds.LATE_BRANDONSCORE_MIXIN_CONFIG,
                "brandonscore",
                "brandonsCore",
            ),
            LateMixinModule(
                StackUpUpIds.LATE_ACTUALLY_ADDITIONS_MIXIN_CONFIG,
                "actuallyadditions",
                "actuallyAdditions",
            ),
            LateMixinModule(
                StackUpUpIds.LATE_CYCLOPSCORE_MIXIN_CONFIG,
                "cyclopscore",
                "cyclopsCore",
            ),
            LateMixinModule(
                StackUpUpIds.LATE_ENDERIO_MIXIN_CONFIG,
                "enderio",
                "enderIo",
            ),
            LateMixinModule(
                StackUpUpIds.LATE_IC2_MIXIN_CONFIG,
                "ic2",
                "ic2",
            ),
            LateMixinModule(
                StackUpUpIds.LATE_MANTLE_MIXIN_CONFIG,
                "mantle",
                "mantle",
            ),
            LateMixinModule(
                StackUpUpIds.LATE_REFINED_STORAGE_MIXIN_CONFIG,
                "refinedstorage",
                "refinedStorage",
            ),
            LateMixinModule(
                StackUpUpIds.LATE_STORAGE_NETWORK_MIXIN_CONFIG,
                "storagenetwork",
                "storageNetwork",
            ),
        )
    }
}
