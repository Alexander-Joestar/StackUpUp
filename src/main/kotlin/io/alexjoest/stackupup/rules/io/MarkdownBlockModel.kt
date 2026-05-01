package io.alexjoest.stackupup.rules.io

data class MarkdownContainer(val blocks: List<MarkdownBlock>, val sections: List<MarkdownSection>) {
    val stateSections: List<MarkdownSection>
        get() = sections.filter { it.kind == MarkdownSectionKind.STATE }

    val ruleSections: List<MarkdownSection>
        get() = sections.filter { it.kind == MarkdownSectionKind.RULES }
}

data class MarkdownSection(val kind: MarkdownSectionKind, val heading: MarkdownBlock.Heading, val blocks: List<MarkdownBlock>)

enum class MarkdownSectionKind {
    STATE,
    RULES,
}

sealed class MarkdownBlock {
    abstract val lineNumber: Int
    abstract val rawLine: String

    data class Heading(val level: Int, val title: String, override val lineNumber: Int, override val rawLine: String) : MarkdownBlock()

    data class ListItem(val text: String, val indent: Int, override val lineNumber: Int, override val rawLine: String) : MarkdownBlock()

    data class FencedCodeBlock(
        val language: String?,
        val lines: List<String>,
        val startLine: Int,
        val endLine: Int,
        val openingLine: String,
        val closingLine: String?,
    ) : MarkdownBlock() {
        override val lineNumber: Int
            get() = startLine

        override val rawLine: String
            get() = openingLine

        val isRuleBlock: Boolean
            get() = language == "stackupup" || language == "su"
    }

    data class Text(val text: String, override val lineNumber: Int, override val rawLine: String) : MarkdownBlock()

    data class Blank(override val lineNumber: Int, override val rawLine: String) : MarkdownBlock()
}
