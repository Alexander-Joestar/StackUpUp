package io.alexjoest.stackupup.limit

import gregtech.api.items.metaitem.MetaItem
import net.minecraft.init.Bootstrap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GregTechMaterialResolverTest {
    @AfterEach
    fun resetResolver() {
        GregTechMaterialResolver.resetReflectionForTesting()
    }

    @Test
    fun `shouldReturnEmptyForRegularStackWhenGregTechIsNotLoaded`() {
        Bootstrap.register()
        val item = Item().setRegistryName(ResourceLocation("minecraft", "stone"))
        val stack = ItemStack(item, 1, 0)

        val material = assertDoesNotThrow<String> {
            GregTechMaterialResolver.resolveMaterial(stack)
        }

        assertEquals("", material)
    }

    @Test
    fun `primaryReflectionFailure_shouldStillAllowFallbackResolver`() {
        Bootstrap.register()
        val item = object : MetaItem() {
            override fun getItem(stack: ItemStack): Any = object {
                @Suppress("unused")
                fun getMaterial(): Any = object {
                    @Suppress("unused")
                    fun getName(): String = "fallback_steel"
                }
            }
        }.setRegistryName(ResourceLocation("gregtech", "meta_item"))
        val stack = ItemStack(item, 1, 0)

        val material = GregTechMaterialResolver.resolveMaterialForTesting(stack, gregTechLoaded = true)

        assertEquals("fallback_steel", material)
    }

    @Test
    fun `materialName_shouldUseRegistryNameObjectToStringBeforeGetName`() {
        val material = object {
            @Suppress("unused")
            fun getRegistryName(): Any = object {
                override fun toString(): String = "gregtech:steel"
            }

            @Suppress("unused")
            fun getName(): String = "steel_by_name"
        }

        assertEquals("gregtech:steel", GregTechMaterialResolver.materialNameForTesting(material))
    }

    @Test
    fun `materialName_shouldFallbackToGetNameWhenRegistryNameMissing`() {
        val material = object {
            @Suppress("unused")
            fun getName(): String = "steel"
        }

        assertEquals("steel", GregTechMaterialResolver.materialNameForTesting(material))
    }
}
