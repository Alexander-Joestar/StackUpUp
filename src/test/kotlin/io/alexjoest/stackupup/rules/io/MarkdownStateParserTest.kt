package io.alexjoest.stackupup.rules.io

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownStateParserTest {
    @Test
    fun shouldParseStateEntriesFromStateSection() {
        val document = MarkdownStateParser.parse(
            listOf(
                "# intro",
                "",
                "# state",
                "This line is commentary.",
                "- phase1 = true",
                "- phase2 = false",
                "# rules",
                "item = minecraft:egg -> 64",
            ),
        )

        assertEquals(mapOf("phase1" to true, "phase2" to false), document.states)
        assertTrue(document.stateSectionLines.any { it.contains("commentary") })
    }

    @Test
    fun `shouldMergeMultipleStateSections`() {
        val document = MarkdownStateParser.parse(
            listOf(
                "# state",
                "- phase1 = true",
                "# rules",
                "item = minecraft:egg -> 64",
                "# state",
                "- phase2 = false",
            ),
        )

        assertEquals(mapOf("phase1" to true, "phase2" to false), document.states)
        assertEquals(4, document.stateSectionLines.size)
    }

    @Test
    fun `shouldReportInvalidStateLines`() {
        val document = MarkdownStateParser.parse(
            listOf(
                "# state",
                "- bad_value",
                "- name = maybe",
                "- valid = true",
            ),
        )

        assertEquals(mapOf("valid" to true), document.states)
        assertEquals(2, document.errors.size)
        assertTrue(document.errors.any { it.contains("bad_value") })
        assertTrue(document.errors.any { it.contains("maybe") })
    }
}
