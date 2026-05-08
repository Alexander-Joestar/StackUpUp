package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class EnderIOMixinSourceTest {
    @Test
    fun `enderIoMachineMixin_shouldTargetNoArgInventoryLimitDescriptor`() {
        val source = String(
            Files.readAllBytes(
                Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/EnderIOMachineInventoryLimitMixin.java"),
            ),
            Charsets.UTF_8,
        )

        assertTrue(source.contains("getInventoryStackLimit()I"))
    }

    @Test
    fun `enderIoSlottedMixin_shouldTargetSlotAwareInventoryLimitDescriptor`() {
        val source = String(
            Files.readAllBytes(
                Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/EnderIOSlottedInventoryLimitMixin.java"),
            ),
            Charsets.UTF_8,
        )

        assertTrue(source.contains("getInventoryStackLimit(I)I"))
    }
}
