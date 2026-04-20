package io.alexjoest.stackupup.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CoremodClassFilterTest {
    @Test
    fun `应当跳过明显无关的基础运行时类`() {
        assertEquals(true, CoremodClassFilter.shouldSkip("kotlin/jvm/internal/Intrinsics"))
        assertEquals(true, CoremodClassFilter.shouldSkip("java/lang/String"))
        assertEquals(true, CoremodClassFilter.shouldSkip("javax/annotation/Nullable"))
        assertEquals(true, CoremodClassFilter.shouldSkip("sun/misc/Unsafe"))
        assertEquals(true, CoremodClassFilter.shouldSkip("jdk/internal/loader/ClassLoaders"))
    }

    @Test
    fun `不应跳过可能需要补丁的游戏与模组类`() {
        assertEquals(false, CoremodClassFilter.shouldSkip("net/minecraft/item/ItemStack"))
        assertEquals(false, CoremodClassFilter.shouldSkip("net/minecraft/tileentity/TileEntityChest"))
        assertEquals(
            false,
            CoremodClassFilter.shouldSkip("com/raoulvdberge/refinedstorage/apiimpl/network/node/NetworkNodeStorageMonitor")
        )
        assertEquals(false, CoremodClassFilter.shouldSkip("gregtech/api/items/metaitem/MetaItem"))
    }
}

