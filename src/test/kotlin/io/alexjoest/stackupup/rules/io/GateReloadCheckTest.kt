package io.alexjoest.stackupup.rules.io

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GateReloadCheckTest {
    @Test
    fun `无任何 gate 时 state 变化不需要 reload`() {
        val lines = """
            # state
            - phase1 = false

            # rules
            ## always
            ```su
            item = minecraft:egg -> 128
            ```
        """.trimIndent().lines()

        assertFalse(GateReloadCheck.needsReload(lines, mapOf("phase1" to false), mapOf("phase1" to true)))
    }

    @Test
    fun `gate 引用该 state 且求值结果变化时需要 reload`() {
        val lines = """
            # state
            - phase1 = false

            # rules
            ## state("phase1")
            ```su
            item = minecraft:egg -> 128
            ```
        """.trimIndent().lines()

        assertTrue(GateReloadCheck.needsReload(lines, mapOf("phase1" to false), mapOf("phase1" to true)))
    }

    @Test
    fun `gate 引用该 state 但新旧值相同时不需要 reload`() {
        val lines = """
            # rules
            ## state("phase1")
            ```su
            item = minecraft:egg -> 128
            ```
        """.trimIndent().lines()

        assertFalse(GateReloadCheck.needsReload(lines, mapOf("phase1" to true), mapOf("phase1" to true)))
    }

    @Test
    fun `gate 未引用被写入的 state 时不需要 reload`() {
        val lines = """
            # rules
            ## state("other")
            ```su
            item = minecraft:egg -> 128
            ```
        """.trimIndent().lines()

        assertFalse(GateReloadCheck.needsReload(lines, mapOf("phase1" to false), mapOf("phase1" to true)))
    }

    @Test
    fun `复合 gate 求值结果不变时不需要 reload`() {
        val lines = """
            # rules
            ## state("a") || state("b")
            ```su
            item = minecraft:egg -> 128
            ```
        """.trimIndent().lines()
        // b 已为 true，写入 a=false→true 不改变整个 gate 结果
        val oldStates = mapOf("a" to false, "b" to true)
        val newStates = mapOf("a" to true, "b" to true)

        assertFalse(GateReloadCheck.needsReload(lines, oldStates, newStates))
    }

    @Test
    fun `复合 gate 求值结果变化时需要 reload`() {
        val lines = """
            # rules
            ## state("a") || state("b")
            ```su
            item = minecraft:egg -> 128
            ```
        """.trimIndent().lines()
        val oldStates = mapOf("a" to false, "b" to false)
        val newStates = mapOf("a" to true, "b" to false)

        assertTrue(GateReloadCheck.needsReload(lines, oldStates, newStates))
    }

    @Test
    fun `always 标题与一级标题不作为 gate 参与判断`() {
        val lines = """
            # rules
            ## always
            ### state("phase1")
            ```su
            item = minecraft:egg -> 128
            ```
        """.trimIndent().lines()

        // 只有 ### state("phase1") 是 gate;phase1 false→true 时结果变化,应 reload
        assertTrue(GateReloadCheck.needsReload(lines, mapOf("phase1" to false), mapOf("phase1" to true)))
    }
}
