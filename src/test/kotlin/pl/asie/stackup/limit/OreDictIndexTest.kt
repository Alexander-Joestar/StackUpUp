package pl.asie.stackup.limit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OreDictIndexTest {
    @Test
    fun `同一物品与 metadata 应命中缓存`() {
        val index = OreDictIndex { _, _ -> setOf("ingotSteel") }
        assertEquals(setOf("ingotSteel"), index.getOreNames("gregtech:gt.metaitem.01", 11305))
        assertEquals(setOf("ingotSteel"), index.getOreNames("gregtech:gt.metaitem.01", 11305))
        assertEquals(1, index.debugCacheSize())
    }
}
