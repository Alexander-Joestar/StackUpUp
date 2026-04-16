package pl.asie.stackup.bootstrap

import zone.rong.mixinbooter.ILateMixinLoader

class StackUpLateMixins : ILateMixinLoader {
    override fun getMixinConfigs(): List<String> = listOf("mixins.stackup.late.json")
}
