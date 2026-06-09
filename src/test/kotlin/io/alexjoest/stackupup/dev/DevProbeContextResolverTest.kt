package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.limit.GregTechMaterialResolver
import io.alexjoest.stackupup.limit.RuleRuntime
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import net.minecraft.init.Bootstrap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class DevProbeContextResolverTest {
    @AfterEach
    fun tearDownRuntime() {
        RuleRuntime.replaceSnapshot(RuleSnapshot(version = 0L, rules = emptyList()))
        GregTechMaterialResolver.resetResolverForTesting()
    }

    @Test
    fun `shouldUseCurrentRuntimeContextRequirements`() {
        Bootstrap.register()
        var materialCalls = 0
        GregTechMaterialResolver.installResolverForTesting {
            materialCalls++
            "steel"
        }
        RuleRuntime.replaceSnapshot(
            RuleSnapshot(
                version = 1L,
                rules = listOf(RuleCompiler.compileLine("material = steel -> 2048", 1)),
            ),
        )
        val item = Item().setRegistryName(ResourceLocation("gregtech", "meta_item_1"))
        val stack = ItemStack(item, 1, 1000)

        val context = resolveDevProbeContext(stack, baseLimit = 64)

        assertNotNull(context)
        assertEquals("steel", context?.material)
        assertEquals(1, materialCalls)
    }
}
