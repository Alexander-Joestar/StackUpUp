package io.alexjoest.stackupup.rules.io

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownContainerScannerTest {
    @Test
    fun `shouldSplitStateAndRulesSections`() {
        val document = MarkdownContainerScanner.scan(
            listOf(
                "# intro",
                "plain documentation",
                "",
                "# state",
                "- phase1 = true",
                "  plain note under state",
                "",
                "# rules",
                "## gate1",
                "```stackupup",
                "item = minecraft:egg -> 64",
                "```",
            ),
        )

        assertEquals(2, document.sections.size)
        assertEquals(MarkdownSectionKind.STATE, document.sections[0].kind)
        assertEquals(MarkdownSectionKind.RULES, document.sections[1].kind)
        assertEquals(1, document.sections[0].blocks.count { it is MarkdownBlock.ListItem })
        assertTrue(document.sections[0].blocks.any { it is MarkdownBlock.Text })
        assertEquals(2, document.sections[1].blocks.size)
    }

    @Test
    fun `shouldTrackHeadingLevelsAndFenceLanguages`() {
        val document = MarkdownContainerScanner.scan(
            listOf(
                "# rules",
                "## gate1",
                "### gate2",
                "```su",
                "item = minecraft:egg -> 64",
                "```",
                "```text",
                "not semantic",
                "```",
            ),
        )

        val rulesSection = document.sections.single()
        val headings = rulesSection.blocks.filterIsInstance<MarkdownBlock.Heading>()
        val fences = rulesSection.blocks.filterIsInstance<MarkdownBlock.FencedCodeBlock>()

        assertEquals(listOf(2, 3), headings.map { it.level })
        assertEquals(listOf("su", "text"), fences.map { it.language })
        assertTrue(fences.first().isRuleBlock)
    }

    @Test
    fun `shouldContinueAfterUnclosedFence`() {
        val document = MarkdownContainerScanner.scan(
            listOf(
                "# rules",
                "```stackupup",
                "item = minecraft:egg -> 64",
                "# state",
                "- phase1 = true",
            ),
        )

        assertTrue(document.blocks.any { it is MarkdownBlock.FencedCodeBlock })
        assertEquals(4, document.blocks.size)
    }
}
