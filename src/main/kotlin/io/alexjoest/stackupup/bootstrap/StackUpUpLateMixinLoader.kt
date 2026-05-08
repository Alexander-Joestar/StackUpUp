package io.alexjoest.stackupup.bootstrap

import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.config.MixinToggles
import zone.rong.mixinbooter.Context
import zone.rong.mixinbooter.ILateMixinLoader

class StackUpUpLateMixinLoader : ILateMixinLoader {
    override fun getMixinConfigs(): List<String> = modules.map { it.config }

    override fun shouldMixinConfigQueue(context: Context): Boolean {
        val module = modules.firstOrNull { it.config == context.mixinConfig() } ?: return true
        if (!context.isModPresent(module.modId)) return false
        return module.toggle()
    }

    companion object {
        private val modules: List<LateMixinModule> = listOf(
            LateMixinModule(StackUpUpIds.LATE_AE2_MIXIN_CONFIG, "appliedenergistics2") { MixinToggles.ae2 },
            LateMixinModule(StackUpUpIds.LATE_BRANDONSCORE_MIXIN_CONFIG, "brandonscore") { MixinToggles.brandonsCore },
            LateMixinModule(StackUpUpIds.LATE_ACTUALLY_ADDITIONS_MIXIN_CONFIG, "actuallyadditions") { MixinToggles.actuallyAdditions },
            LateMixinModule(StackUpUpIds.LATE_CYCLOPSCORE_MIXIN_CONFIG, "cyclopscore") { MixinToggles.cyclopsCore },
            LateMixinModule(StackUpUpIds.LATE_ENDERIO_MIXIN_CONFIG, "enderio") { MixinToggles.enderIo },
            LateMixinModule(StackUpUpIds.LATE_IC2_MIXIN_CONFIG, "ic2") { MixinToggles.ic2 },
            LateMixinModule(StackUpUpIds.LATE_MANTLE_MIXIN_CONFIG, "mantle") { MixinToggles.mantle },
            LateMixinModule(StackUpUpIds.LATE_REFINED_STORAGE_MIXIN_CONFIG, "refinedstorage") { MixinToggles.refinedStorage },
            LateMixinModule(StackUpUpIds.LATE_STORAGE_NETWORK_MIXIN_CONFIG, "storagenetwork") { MixinToggles.storageNetwork },
        )
    }

    private data class LateMixinModule(val config: String, val modId: String, val toggle: () -> Boolean)
}
