package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.StackUpUpCore
import io.alexjoest.stackupup.bootstrap.StackUpUpLateMixinLoader
import zone.rong.mixinbooter.Context

class MixinBooterIntegrationTest {
    @Test
    fun `早期配置文件名应稳定`() {
        assertEquals(listOf("mixins.stackupup.early.json"), StackUpUpCore().getMixinConfigs())
    }

    @Test
    fun `后期配置文件名应稳定`() {
        assertEquals(
            listOf(
                "mixins.stackupup.late.ae2.json",
                "mixins.stackupup.late.brandonscore.json",
                "mixins.stackupup.late.actuallyadditions.json",
                "mixins.stackupup.late.cyclopscore.json",
                "mixins.stackupup.late.enderio.json",
                "mixins.stackupup.late.ic2.json",
                "mixins.stackupup.late.mantle.json",
                "mixins.stackupup.late.refinedstorage.json"
            ),
            StackUpUpLateMixinLoader().getMixinConfigs()
        )
    }

    @Test
    fun `后期配置应按模组存在排队`() {
        val loader = StackUpUpLateMixinLoader()
        assertTrue(
            loader.shouldMixinConfigQueue(
                Context(
                    "mixins.stackupup.late.ae2.json",
                    listOf("appliedenergistics2")
                )
            )
        )
        assertFalse(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.ae2.json", emptyList())))

        assertTrue(
            loader.shouldMixinConfigQueue(
                Context("mixins.stackupup.late.actuallyadditions.json", listOf("actuallyadditions"))
            )
        )
        assertFalse(
            loader.shouldMixinConfigQueue(
                Context(
                    "mixins.stackupup.late.actuallyadditions.json",
                    emptyList()
                )
            )
        )

        assertTrue(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.brandonscore.json", listOf("brandonscore"))))
        assertFalse(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.brandonscore.json", emptyList())))

        assertTrue(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.mantle.json", listOf("mantle"))))
        assertFalse(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.mantle.json", emptyList())))

        assertTrue(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.ic2.json", listOf("ic2"))))
        assertTrue(
            loader.shouldMixinConfigQueue(
                Context(
                    "mixins.stackupup.late.cyclopscore.json",
                    listOf("cyclopscore")
                )
            )
        )
        assertFalse(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.cyclopscore.json", emptyList())))

        assertTrue(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.enderio.json", listOf("enderio"))))
        assertFalse(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.enderio.json", emptyList())))

        assertTrue(
            loader.shouldMixinConfigQueue(
                Context(
                    "mixins.stackupup.late.refinedstorage.json",
                    listOf("refinedstorage")
                )
            )
        )
        assertFalse(
            loader.shouldMixinConfigQueue(
                Context(
                    "mixins.stackupup.late.refinedstorage.json",
                    emptyList()
                )
            )
        )
    }
}
