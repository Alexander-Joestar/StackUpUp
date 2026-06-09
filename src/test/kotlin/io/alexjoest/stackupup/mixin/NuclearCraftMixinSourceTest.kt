package io.alexjoest.stackupup.mixin

import io.alexjoest.stackupup.bootstrap.StackUpUpLateMixinLoader
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class NuclearCraftMixinSourceTest {
    @Test
    fun `nuclearCraftLateMixin_shouldNotShipSplitOnlyExpansionThatCanHideBackingInventoryTruncation`() {
        val configPath = Paths.get("src/main/resources/mixins.stackupup.late.nuclearcraft.json")
        val itemHandlerMixinPath =
            Paths.get("src/main/java/io/alexjoest/stackupup/mixin/late/NuclearCraftItemHandlerMixin.java")
        val itemHandlerHooksPath = Paths.get("src/main/kotlin/io/alexjoest/stackupup/NuclearCraftItemHandlerHooks.kt")

        assertFalse(Files.exists(configPath), "NC config must not be enabled without a backing inventory clamp mixin")
        assertFalse(
            Files.exists(itemHandlerMixinPath),
            "Do not ship a mixin that only expands ItemHandler#getStackSplitSize; it can make insertItem return EMPTY while ITileInventory truncates",
        )
        assertFalse(
            Files.exists(itemHandlerHooksPath),
            "NC split-limit hooks must stay absent unless they are paired with a real ITileInventory capacity fix or honest remainder handling",
        )
        assertTrue(StackUpUpLateMixinLoader().getMixinConfigs().none { it.contains("nuclearcraft") })
    }
}
