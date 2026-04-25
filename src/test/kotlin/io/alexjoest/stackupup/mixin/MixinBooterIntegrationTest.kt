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
    fun `后期配置应按模组存在与配置开关排队`() {
        val loader = StackUpUpLateMixinLoader()
        val oldAe2 = StackUpUpConfig.coremodPatchAppliedEnergistics2
        val oldActuallyAdditions = StackUpUpConfig.coremodPatchActuallyAdditions
        val oldCyclopsCore = StackUpUpConfig.coremodPatchCyclopsCore
        val oldEnderIO = StackUpUpConfig.coremodPatchEnderIO
        val oldMantle = StackUpUpConfig.coremodPatchMantle
        val oldIc2 = StackUpUpConfig.coremodPatchIc2
        val oldRs = StackUpUpConfig.coremodPatchRefinedStorage

        try {
            StackUpUpConfig.coremodPatchAppliedEnergistics2 = true
            assertTrue(
                loader.shouldMixinConfigQueue(
                    Context(
                        "mixins.stackupup.late.ae2.json",
                        listOf("appliedenergistics2")
                    )
                )
            )
            assertFalse(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.ae2.json", emptyList())))

            StackUpUpConfig.coremodPatchActuallyAdditions = true
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

            StackUpUpConfig.coremodPatchMantle = false
            assertFalse(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.mantle.json", listOf("mantle"))))

            StackUpUpConfig.coremodPatchMantle = true
            StackUpUpConfig.coremodPatchIc2 = true
            assertTrue(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.ic2.json", listOf("ic2"))))

            StackUpUpConfig.coremodPatchCyclopsCore = true
            assertTrue(
                loader.shouldMixinConfigQueue(
                    Context(
                        "mixins.stackupup.late.cyclopscore.json",
                        listOf("cyclopscore")
                    )
                )
            )
            assertFalse(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.cyclopscore.json", emptyList())))

            StackUpUpConfig.coremodPatchEnderIO = true
            assertTrue(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.enderio.json", listOf("enderio"))))
            assertFalse(loader.shouldMixinConfigQueue(Context("mixins.stackupup.late.enderio.json", emptyList())))

            StackUpUpConfig.coremodPatchRefinedStorage = true
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
        } finally {
            StackUpUpConfig.coremodPatchAppliedEnergistics2 = oldAe2
            StackUpUpConfig.coremodPatchActuallyAdditions = oldActuallyAdditions
            StackUpUpConfig.coremodPatchCyclopsCore = oldCyclopsCore
            StackUpUpConfig.coremodPatchEnderIO = oldEnderIO
            StackUpUpConfig.coremodPatchMantle = oldMantle
            StackUpUpConfig.coremodPatchIc2 = oldIc2
            StackUpUpConfig.coremodPatchRefinedStorage = oldRs
        }
    }
}

