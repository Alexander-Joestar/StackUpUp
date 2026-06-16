package io.alexjoest.stackupup.limit

import gregtech.api.items.metaitem.MetaItem
import gregtech.api.unification.OreDictUnifier
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
        OreDictUnifier.materialStack = null
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
    fun `primaryResolver_shouldReadPublicMaterialStackGetMaterial`() {
        Bootstrap.register()
        OreDictUnifier.materialStack = TestMaterialStack(TestMaterial("gregtech:steel", "steel_by_name"))
        val item = Item().setRegistryName(ResourceLocation("gregtech", "meta_ingot"))
        val stack = ItemStack(item, 1, 0)

        val material = GregTechMaterialResolver.resolveMaterialForTesting(stack, gregTechLoaded = true)

        assertEquals("gregtech:steel", material)
    }

    @Test
    fun `primaryReflectionFailure_shouldStillAllowFallbackResolver`() {
        Bootstrap.register()
        val item = object : MetaItem() {
            override fun getItem(stack: ItemStack): Any = TestValueItem(TestMaterial(null, "fallback_steel"))
        }.setRegistryName(ResourceLocation("gregtech", "meta_item"))
        val stack = ItemStack(item, 1, 0)

        val material = GregTechMaterialResolver.resolveMaterialForTesting(stack, gregTechLoaded = true)

        assertEquals("fallback_steel", material)
    }

    @Test
    fun `materialName_shouldUseResourceLocationRegistryNameBeforeGetName`() {
        val material = object {
            fun getRegistryName(): ResourceLocation = ResourceLocation("gregtech", "steel")

            fun getName(): String = "steel_by_name"
        }

        assertEquals("gregtech:steel", GregTechMaterialResolver.materialNameForTesting(material))
    }

    @Test
    fun `materialName_shouldFallbackToGetNameWhenRegistryNameMissing`() {
        val material = object {
            fun getName(): String = "steel"
        }

        assertEquals("steel", GregTechMaterialResolver.materialNameForTesting(material))
    }

    @Test
    fun `materialName_shouldRejectDebugToStringWhenPublicMethodsMissing`() {
        val material = object {
            override fun toString(): String = "gregtech:bronze"
        }

        assertEquals("", GregTechMaterialResolver.materialNameForTesting(material))
    }

    @Test
    fun `materialName_shouldAcceptResourceLocationRegistryName`() {
        val material = object {
            fun getRegistryName(): ResourceLocation = ResourceLocation("gregtech", "bronze")
        }

        assertEquals("gregtech:bronze", GregTechMaterialResolver.materialNameForTesting(material))
    }

    @Test
    fun `fallbackResolver_shouldReturnEmptyWhenValueItemMaterialGetterIsNotPublicByBoundary`() {
        Bootstrap.register()
        val item = object : MetaItem() {
            override fun getItem(stack: ItemStack): Any = object {
                @Suppress("unused")
                private fun getMaterial(): Any = object {
                    fun getName(): String = "hidden_steel"
                }
            }
        }.setRegistryName(ResourceLocation("gregtech", "meta_item"))
        val stack = ItemStack(item, 1, 0)

        val material = GregTechMaterialResolver.resolveMaterialForTesting(stack, gregTechLoaded = true)

        assertEquals("", material)
    }
}

class TestMaterialStack(private val material: Any) {
    fun getMaterial(): Any = material
}

class TestValueItem(private val material: Any) {
    fun getMaterial(): Any = material
}

class TestMaterial(
    private val registryName: String?,
    private val name: String,
) {
    fun getRegistryName(): ResourceLocation? =
        registryName?.let { ResourceLocation(it) }

    fun getName(): String = name
}
