package io.alexjoest.stackupup.limit

import net.minecraft.init.Bootstrap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class StackContextResolverTest {
    @Test
    fun `应当把物品栈规范化为统一上下文`() {
        Bootstrap.register()
        val item = Item().setRegistryName(ResourceLocation("gregtech", "meta_ingot"))
        val stack = ItemStack(item, 1, 324)
        val index = OreDictIndex.fromStackLoader { setOf("ingotSteel") }

        val context = StackContextResolver.fromStack(stack = stack, baseLimit = 64, oreDictIndex = index)

        assertNotNull(context)
        assertEquals("gregtech:meta_ingot", context?.itemId)
        assertEquals("gregtech", context?.modId)
        assertEquals(324, context?.metadata)
        assertEquals("item", context?.type)
        assertEquals(64, context?.baseLimit)
        assertEquals(setOf("ingotSteel"), context?.oreNames)
    }
}

