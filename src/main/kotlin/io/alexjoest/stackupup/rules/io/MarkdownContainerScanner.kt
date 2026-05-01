package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.StackUpUp

object MarkdownContainerScanner {
    fun scan(text: String): MarkdownContainer = scan(text.lines())

    fun scan(lines: List<String>): MarkdownContainer {
        val blocks = ArrayList<MarkdownBlock>(lines.size)
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val language = parseFenceLanguage(line)
            if (language != null) {
                val fence = readFence(lines, index, language)
                blocks += fence.block
                index = fence.nextIndex
                if (fence.unclosed) {
                    StackUpUp.logger?.warn(
                        "Unclosed fenced code block starting at line {} ended at EOF.",
                        index,
                    )
                }
                continue
            }

            val lineNumber = index + 1
            val heading = parseHeading(line, lineNumber)
            when {
                heading != null -> blocks += heading
                line.isBlank() -> blocks += MarkdownBlock.Blank(lineNumber, line)
                else -> blocks += parseListItem(line, lineNumber) ?: MarkdownBlock.Text(line, lineNumber, line)
            }
            index++
        }

        return MarkdownContainer(blocks = blocks, sections = buildSections(blocks))
    }

    private fun parseHeading(line: String, lineNumber: Int): MarkdownBlock.Heading? {
        var level = 0
        while (level < line.length && line[level] == '#') {
            level++
        }
        if (level == 0 || level > 6 || line.getOrNull(level)?.isWhitespace() != true) {
            return null
        }
        return MarkdownBlock.Heading(
            level = level,
            title = line.substring(level).trim(),
            lineNumber = lineNumber,
            rawLine = line,
        )
    }

    private fun parseListItem(line: String, lineNumber: Int): MarkdownBlock.ListItem? {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith("- ")) {
            return null
        }
        return MarkdownBlock.ListItem(
            text = trimmed.removePrefix("- ").trim(),
            indent = line.length - trimmed.length,
            lineNumber = lineNumber,
            rawLine = line,
        )
    }

    private fun parseFenceLanguage(line: String): String? {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith("```")) {
            return null
        }
        return trimmed.removePrefix("```")
            .trim()
            .substringBefore(' ')
            .lowercase()
    }

    private fun readFence(lines: List<String>, startIndex: Int, language: String): FenceReadResult {
        val body = ArrayList<String>()
        var index = startIndex + 1
        var closingLine: String? = null
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```")) {
                closingLine = line
                index++
                break
            }
            if (trimmed.startsWith("# ")) {
                break
            }
            body += line
            index++
        }
        return FenceReadResult(
            block = MarkdownBlock.FencedCodeBlock(
                language = language.takeIf { it.isNotEmpty() },
                lines = body,
                startLine = startIndex + 1,
                endLine = if (closingLine == null) lines.size else index,
                openingLine = lines[startIndex],
                closingLine = closingLine,
            ),
            nextIndex = index,
            unclosed = closingLine == null,
        )
    }

    private fun buildSections(blocks: List<MarkdownBlock>): List<MarkdownSection> {
        val sections = ArrayList<MarkdownSection>()
        var heading: MarkdownBlock.Heading? = null
        var kind: MarkdownSectionKind? = null
        var sectionBlocks = ArrayList<MarkdownBlock>()

        fun flush() {
            val currentHeading = heading
            val currentKind = kind
            if (currentHeading != null && currentKind != null) {
                sections += MarkdownSection(currentKind, currentHeading, sectionBlocks.toList())
            }
            heading = null
            kind = null
            sectionBlocks = ArrayList()
        }

        for (block in blocks) {
            if (block is MarkdownBlock.Heading && block.level == 1) {
                flush()
                val nextKind = sectionKind(block.title)
                if (nextKind != null) {
                    heading = block
                    kind = nextKind
                }
                continue
            }

            if (kind != null) {
                sectionBlocks += block
            }
        }
        flush()
        return sections
    }

    private fun sectionKind(title: String): MarkdownSectionKind? = when (title.lowercase()) {
        "state" -> MarkdownSectionKind.STATE
        "rules" -> MarkdownSectionKind.RULES
        else -> null
    }

    private data class FenceReadResult(val block: MarkdownBlock.FencedCodeBlock, val nextIndex: Int, val unclosed: Boolean)
}
