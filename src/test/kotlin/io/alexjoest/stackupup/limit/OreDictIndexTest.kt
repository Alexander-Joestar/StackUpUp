package io.alexjoest.stackupup.limit

import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraft.init.Bootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OreDictIndexTest {
    @Test
    fun `同一物品与 metadata 应命中缓存`() {
        val index = OreDictIndex({ _, _ -> setOf("ingotSteel") })
        assertEquals(setOf("ingotSteel"), index.getOreNames("gregtech:gt.metaitem.01", 11305))
        assertEquals(setOf("ingotSteel"), index.getOreNames("gregtech:gt.metaitem.01", 11305))
        assertEquals(1, index.debugCacheSize())
    }

    @Test
    fun `应当直接使用原始物品栈做矿物辞典查询`() {
        Bootstrap.register()
        val seen = ArrayList<ItemStack>()
        val index = OreDictIndex.fromStackLoader { stack ->
            seen += stack.copy()
            setOf("ingotIron")
        }
        val item = Item().setRegistryName(ResourceLocation("stackupup_test", "dummy_item"))

        val result = index.getOreNames(ItemStack(item))

        assertEquals(setOf("ingotIron"), result)
        assertEquals(1, seen.size)
        assertEquals(item, seen.single().item)
    }

    @Test
    fun `空栈查询应直接返回空集合`() {
        Bootstrap.register()
        val index = OreDictIndex.fromStackLoader { error("空栈不应触发加载器") }
        assertTrue(index.getOreNames(ItemStack.EMPTY).isEmpty())
    }
}

