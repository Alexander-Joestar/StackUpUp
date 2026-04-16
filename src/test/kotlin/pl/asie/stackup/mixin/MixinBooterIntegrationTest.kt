package pl.asie.stackup.mixin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.asie.stackup.StackUpCore
import pl.asie.stackup.bootstrap.StackUpLateMixins

class MixinBooterIntegrationTest {
    @Test
    fun `早期配置文件名应稳定`() {
        assertEquals(listOf("mixins.stackup.early.json"), StackUpCore().getMixinConfigs())
    }

    @Test
    fun `后期配置文件名应稳定`() {
        assertEquals(listOf("mixins.stackup.late.json"), StackUpLateMixins().getMixinConfigs())
    }
}
