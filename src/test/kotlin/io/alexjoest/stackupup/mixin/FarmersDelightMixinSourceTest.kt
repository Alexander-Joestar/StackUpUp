package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class FarmersDelightMixinSourceTest {
    @Test
    fun farmersDelightMixin_shouldTargetOnlyWritable64Inventories() {
        val source = String(
            Files.readAllBytes(
                Paths.get(
                    "src/main/java/io/alexjoest/stackupup/mixin/late/farmersdelight/FarmersDelightInventoryLimitMixin.java",
                ),
            ),
            Charsets.UTF_8,
        )
        val config = String(
            Files.readAllBytes(Paths.get("src/main/resources/mixins.stackupup.capacity.late.farmersdelight.json")),
            Charsets.UTF_8,
        )

        assertTrue(config.contains("FarmersDelightInventoryLimitMixin"))
        assertTrue(source.contains("getInventoryStackLimit()I"))
        assertTrue(source.contains("func_70297_j_()I"))
        assertTrue(source.contains("TileEntityBasket"))
        assertTrue(source.contains("TileEntityCabinet"))
        assertTrue(source.contains("TileEntityCookingPot"))
        assertTrue(source.contains("TileEntitySkillet"))
        assertTrue(source.contains("private int stackupup${'$'}expandDefaultInventoryLimit"))
        assertTrue(source.contains("StackLimitHooks.shouldBypassInventoryStackLimit(this)"))
        assertTrue(source.contains("StackLimitHooks.getInventoryCompatibilityStackSize(original)"))
        assertTrue(source.contains("require = 0"))
        assertFalse(source.contains("TileEntityCuttingBoard"))
        assertFalse(source.contains("TileEntityStove"))
        assertFalse(source.contains("@Redirect"))
        assertFalse(source.contains("@Overwrite"))
    }
}
