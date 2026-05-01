package io.alexjoest.stackupup.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CoremodClassFilterTest {
    @Test
    fun `shouldSkipUnrelatedRuntimeClasses`() {
        assertEquals(true, CoremodClassFilter.shouldSkip("kotlin/jvm/internal/Intrinsics"))
        assertEquals(true, CoremodClassFilter.shouldSkip("java/lang/String"))
        assertEquals(true, CoremodClassFilter.shouldSkip("javax/annotation/Nullable"))
        assertEquals(true, CoremodClassFilter.shouldSkip("sun/misc/Unsafe"))
        assertEquals(true, CoremodClassFilter.shouldSkip("jdk/internal/loader/ClassLoaders"))
    }

    @Test
    fun `shouldNotSkipGameAndModClasses`() {
        assertEquals(false, CoremodClassFilter.shouldSkip("net/minecraft/item/ItemStack"))
        assertEquals(false, CoremodClassFilter.shouldSkip("net/minecraft/tileentity/TileEntityChest"))
        assertEquals(
            false,
            CoremodClassFilter.shouldSkip("com/raoulvdberge/refinedstorage/apiimpl/network/node/NetworkNodeStorageMonitor"),
        )
        assertEquals(false, CoremodClassFilter.shouldSkip("gregtech/api/items/metaitem/MetaItem"))
    }
}
