package io.alexjoest.stackupup.mixin.late.ic2classic

import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.bootstrap.StackUpUpMixinConnector
import io.alexjoest.stackupup.core.InventoryStackLimitHook
import io.alexjoest.stackupup.core.resetInventoryExpansionForTesting
import net.minecraft.init.Bootstrap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class Ic2ClassicMixinSourceTest {
    @BeforeEach
    fun resetExpansion() {
        resetInventoryExpansionForTesting()
    }

    @AfterEach
    fun restoreExpansion() {
        resetInventoryExpansionForTesting()
    }

    @Test
    fun classicConfigs_shouldUseClassicTargetWithoutTouchingExperimentalTarget() {
        val lateConfig = read("src/main/resources/mixins.stackupup.late.ic2classic.json")
        val capacityConfig = read("src/main/resources/mixins.stackupup.capacity.late.ic2classic.json")
        val classicMixin = read("src/main/java/io/alexjoest/stackupup/mixin/late/ic2classic/SlotBaseMixin.java")
        val experimentalMixin = read("src/main/java/io/alexjoest/stackupup/mixin/late/ic2/InvSlotMixin.java")

        assertTrue(lateConfig.contains("io.alexjoest.stackupup.mixin.late.ic2classic"))
        assertTrue(lateConfig.contains("SlotBaseMixin"), "native-64 safety mixin must be in the always-queued config")
        assertFalse(capacityConfig.contains("SlotBaseMixin"), "optional capacity config must remain separate")
        assertTrue(classicMixin.contains("@Pseudo"))
        assertTrue(classicMixin.contains("remap = false"))
        assertTrue(classicMixin.contains("require = 1"))
        assertTrue(classicMixin.contains(StackUpUpIds.IC2_CLASSIC_TARGET_CLASS))
        assertTrue(classicMixin.contains("func_75219_a()I"))
        assertFalse(classicMixin.contains("func_75215_d"), "不得在无 remainder 返回路径的 writer 上做截断注入")
        assertFalse(classicMixin.contains("setCount"), "不得使用写入后截断或补偿")
        assertFalse(classicMixin.contains(StackUpUpIds.IC2_EXPERIMENTAL_TARGET_CLASS))
        assertTrue(experimentalMixin.contains(StackUpUpIds.IC2_EXPERIMENTAL_TARGET_CLASS))
        assertFalse(experimentalMixin.contains(StackUpUpIds.IC2_CLASSIC_TARGET_CLASS))
    }

    @Test
    fun classicRegistration_shouldUseIc2ModIdForBothProfiles() {
        InventoryStackLimitHook.configureExpansionEnabled(true)
        val queued = queueIc2()

        assertTrue(queued.contains(StackUpUpIds.LATE_IC2_CLASSIC_MIXIN_CONFIG))
        assertTrue(queued.contains(StackUpUpIds.CAPACITY_LATE_IC2_CLASSIC_MIXIN_CONFIG))
        assertTrue(queued.contains(StackUpUpIds.LATE_IC2_MIXIN_CONFIG))
        assertTrue(queued.contains("mixins.stackupup.capacity.late.ic2.json"))

        val absent = queueIc2(modPresent = false)
        assertFalse(absent.contains(StackUpUpIds.LATE_IC2_CLASSIC_MIXIN_CONFIG))
        assertFalse(absent.contains(StackUpUpIds.CAPACITY_LATE_IC2_CLASSIC_MIXIN_CONFIG))
    }

    @Test
    fun classicCapacity_shouldStayNativeWhenExpansionIsOffOrBlacklisted() {
        val connector = StackUpUpMixinConnector()
        val alwaysQueuedConfig = read("src/main/resources/mixins.stackupup.late.ic2classic.json")
        assertTrue(alwaysQueuedConfig.contains("SlotBaseMixin"))

        InventoryStackLimitHook.configureExpansionEnabled(false)
        val disabledQueued = mutableListOf<String>()
        connector.connectLateWithClassicModules(
            conflicts = emptyList(),
            isModPresent = { it == StackUpUpIds.IC2_MOD_ID },
            queueConfiguration = { disabledQueued += it },
        )
        assertTrue(disabledQueued.contains(StackUpUpIds.LATE_IC2_CLASSIC_MIXIN_CONFIG))
        assertFalse(disabledQueued.contains(StackUpUpIds.CAPACITY_LATE_IC2_CLASSIC_MIXIN_CONFIG))

        resetInventoryExpansionForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(true)
        val blacklistedQueued = mutableListOf<String>()
        connector.connectLateWithClassicModules(
            conflicts = emptyList(),
            isModPresent = { it == StackUpUpIds.IC2_MOD_ID },
            blacklistedModIds = setOf(StackUpUpIds.IC2_MOD_ID),
            queueConfiguration = { blacklistedQueued += it },
        )
        assertTrue(blacklistedQueued.contains(StackUpUpIds.LATE_IC2_CLASSIC_MIXIN_CONFIG))
        assertFalse(blacklistedQueued.contains(StackUpUpIds.CAPACITY_LATE_IC2_CLASSIC_MIXIN_CONFIG))
    }

    @Test
    fun nativeSlotGuard_shouldSplitOfferedStackBeforeClassicWriter() {
        Bootstrap.register()
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "ic2_classic_writer_item"))
        val offered = ItemStack(item, 150, 0)
        val writerLimit = SlotBaseMixin.keepNativeSlotLimit(256)

        val accepted = offered.splitStack(minOf(offered.count, writerLimit))

        assertEquals(64, writerLimit)
        assertEquals(64, accepted.count)
        assertEquals(86, offered.count)
        assertEquals(150, accepted.count + offered.count)
        assertEquals(64, SlotBaseMixin.keepNativeSlotLimit(64))
        assertEquals(16, SlotBaseMixin.keepNativeSlotLimit(16))
    }

    private fun queueIc2(modPresent: Boolean = true): List<String> {
        val queued = mutableListOf<String>()
        StackUpUpMixinConnector().connectLateWithClassicModules(
            conflicts = emptyList(),
            isModPresent = { modPresent && it == StackUpUpIds.IC2_MOD_ID },
            queueConfiguration = { queued += it },
        )
        return queued
    }

    private fun read(path: String): String = String(Files.readAllBytes(Paths.get(path)), Charsets.UTF_8)
}
