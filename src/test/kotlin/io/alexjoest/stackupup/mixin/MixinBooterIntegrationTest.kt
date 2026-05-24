package io.alexjoest.stackupup.mixin

import io.alexjoest.stackupup.StackUpUpCore
import io.alexjoest.stackupup.bootstrap.StackUpUpLateMixinLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import zone.rong.mixinbooter.Context

class MixinBooterIntegrationTest {
    @Test
    fun `earlyConfigFileName_shouldBeStable`() {
        assertEquals(listOf("mixins.stackupup.early.json"), StackUpUpCore().getMixinConfigs())
    }

    @Test
    fun `lateConfigFileName_shouldBeStable`() {
        assertEquals(
            listOf(
                "mixins.stackupup.late.ae2.json",
                "mixins.stackupup.late.brandonscore.json",
                "mixins.stackupup.late.actuallyadditions.json",
                "mixins.stackupup.late.cyclopscore.json",
                "mixins.stackupup.late.enderio.json",
                "mixins.stackupup.late.ic2.json",
                "mixins.stackupup.late.mantle.json",
                "mixins.stackupup.late.refinedstorage.json",
                "mixins.stackupup.late.storagenetwork.json",
                "mixins.stackupup.late.integrateddynamics.json",
                "mixins.stackupup.late.limelib.json",
                "mixins.stackupup.late.immersiveengineering.json",
            ),
            StackUpUpLateMixinLoader().getMixinConfigs(),
        )
    }

    @Test
    fun `lateConfig_shouldQueueByModPresence`() {
        val loader = StackUpUpLateMixinLoader()
        assertTrue(
            loader.shouldMixinConfigQueue(
                Context(
                    "mixins.stackupup.late.ae2.json",
                    listOf("appliedenergistics2"),
                ),
            ),
        )
        assertFalse(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.ae2.json", emptyList())))

        assertTrue(
            loader.shouldMixinConfigQueue(
                Context("mixins.stackupup.late.actuallyadditions.json", listOf("actuallyadditions")),
            ),
        )
        assertFalse(
            loader.shouldMixinConfigQueue(
                Context(
                    "mixins.stackupup.late.actuallyadditions.json",
                    emptyList(),
                ),
            ),
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
                    listOf("cyclopscore"),
                ),
            ),
        )
        assertFalse(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.cyclopscore.json", emptyList())))

        assertTrue(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.enderio.json", listOf("enderio"))))
        assertFalse(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.enderio.json", emptyList())))

        assertTrue(
            loader.shouldMixinConfigQueue(
                Context(
                    "mixins.stackupup.late.refinedstorage.json",
                    listOf("refinedstorage"),
                ),
            ),
        )
        assertFalse(
            loader.shouldMixinConfigQueue(
                Context(
                    "mixins.stackupup.late.refinedstorage.json",
                    emptyList(),
                ),
            ),
        )

        assertTrue(
            loader.shouldMixinConfigQueue(
                Context("mixins.stackupup.late.integrateddynamics.json", listOf("integrateddynamics")),
            ),
        )
        assertFalse(
            loader.shouldMixinConfigQueue(
                Context("mixins.stackupup.late.integrateddynamics.json", emptyList()),
            ),
        )

        assertTrue(
            loader.shouldMixinConfigQueue(
                Context("mixins.stackupup.late.limelib.json", listOf("limelib")),
            ),
        )
        assertFalse(
            loader.shouldMixinConfigQueue(
                Context("mixins.stackupup.late.limelib.json", emptyList()),
            ),
        )

        assertTrue(
            loader.shouldMixinConfigQueue(
                Context("mixins.stackupup.late.immersiveengineering.json", listOf("immersiveengineering")),
            ),
        )
        assertFalse(
            loader.shouldMixinConfigQueue(
                Context("mixins.stackupup.late.immersiveengineering.json", emptyList()),
            ),
        )
    }
}
