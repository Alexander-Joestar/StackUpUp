package io.alexjoest.stackupup.core

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DynamicCompatTargetClassifierTest {
    @Test
    fun `固定目标应直接跳过动态分类`() {
        for (target in FixedCompatTargets.all()) {
            assertEquals(
                DynamicCompatTargetProfile.NONE,
                DynamicCompatTargetClassifier.classify(target),
                "固定目标不应再次进入动态分类: $target"
            )
        }
    }

    @Test
    fun `自定义库存桥接类应分类为 inventory`() {
        assertEquals(
            DynamicCompatTargetProfile.INVENTORY,
            DynamicCompatTargetClassifier.classify("io.alexjoest.stackupup.core.TestInventoryBridge")
        )
    }

    @Test
    fun `forge 物品处理器包装器应分类为 item handler`() {
        assertEquals(
            DynamicCompatTargetProfile.ITEM_HANDLER,
            DynamicCompatTargetClassifier.classify("net.minecraftforge.items.wrapper.PlayerInvWrapper")
        )
    }

    @Test
    fun `slot 子类应分类为 slot`() {
        assertEquals(
            DynamicCompatTargetProfile.SLOT,
            DynamicCompatTargetClassifier.classify("net.minecraft.inventory.SlotCrafting")
        )
    }

    @Test
    fun `明显无关的类应分类为空`() {
        assertEquals(
            DynamicCompatTargetProfile.NONE,
            DynamicCompatTargetClassifier.classify("java.lang.String")
        )
    }

    @Test
    fun `定向分类不应在未命中的 profile 上继续做关系判断`() {
        assertEquals(
            DynamicCompatTargetProfile.NONE,
            DynamicCompatTargetClassifier.classify(
                "net.minecraftforge.items.wrapper.PlayerInvWrapper",
                DynamicCompatTargetProfile.INVENTORY
            )
        )
        assertEquals(
            DynamicCompatTargetProfile.NONE,
            DynamicCompatTargetClassifier.classify(
                "net.minecraft.inventory.SlotCrafting",
                DynamicCompatTargetProfile.ITEM_HANDLER
            )
        )
    }
    @Test
    fun `fixed target 鎺㈤拡瀛愰泦搴旂敱鍗曚竴鍚嶅崟鎺ㄥ`() {
        assertArrayEquals(
            arrayOf(
                "org.cyclops.cyclopscore.inventory.SimpleInventory",
                "net.minecraftforge.items.SlotItemHandler",
                "net.minecraftforge.items.wrapper.InvWrapper",
                "net.minecraftforge.items.wrapper.SidedInvWrapper",
                "net.minecraftforge.items.wrapper.CombinedInvWrapper",
                "net.minecraftforge.items.wrapper.RangedWrapper"
            ),
            FixedCompatTargets.probeTargets()
        )
    }
}
