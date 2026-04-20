package io.alexjoest.stackupup.rules.persist

import java.io.File

class RuleBlockFileStore(
    private val file: File
) {
    fun readBlocks(): List<RuleTextBlock> = parseDocument().blocks

    fun replaceBlock(block: RuleTextBlock) {
        val document = parseDocument()
        val replaced = document.blocks.indexOfFirst { it.id == block.id }
        val nextBlocks = document.blocks.toMutableList()
        if (replaced >= 0) {
            nextBlocks[replaced] = block
        } else {
            nextBlocks += block
        }

        writeDocument(document.copy(blocks = nextBlocks))
    }

    private fun parseDocument(): RuleBlockDocument {
        if (!file.exists()) {
            return RuleBlockDocument(
                prefixLines = emptyList(),
                blocks = emptyList()
            )
        }

        val lines = file.readLines(Charsets.UTF_8)
        val prefix = ArrayList<String>()
        val blocks = ArrayList<RuleTextBlock>()
        var index = 0
        while (index < lines.size) {
            val beginId = parseBeginMarker(lines[index])
            if (beginId == null) {
                prefix += lines[index]
                index++
                continue
            }

            index++
            val blockLines = ArrayList<String>()
            while (index < lines.size && parseEndMarker(lines[index]) != beginId) {
                blockLines += lines[index]
                index++
            }
            if (index < lines.size) {
                index++
            }
            blocks += RuleTextBlock(beginId, blockLines)
        }

        return RuleBlockDocument(prefix, blocks)
    }

    private fun writeDocument(document: RuleBlockDocument) {
        file.parentFile?.mkdirs()
        val rendered = buildList {
            addAll(document.prefixLines)
            if (isNotEmpty() && last().isNotBlank()) {
                add("")
            }
            document.blocks.forEachIndexed { index, block ->
                add(beginMarker(block.id))
                addAll(block.lines)
                add(endMarker(block.id))
                if (index != document.blocks.lastIndex) {
                    add("")
                }
            }
        }
        file.writeText(rendered.joinToString(System.lineSeparator()) + System.lineSeparator(), Charsets.UTF_8)
    }

    private fun parseBeginMarker(line: String): String? {
        val trimmed = line.trim()
        return if (trimmed.startsWith(BEGIN_PREFIX)) trimmed.removePrefix(BEGIN_PREFIX).trim() else null
    }

    private fun parseEndMarker(line: String): String? {
        val trimmed = line.trim()
        return if (trimmed.startsWith(END_PREFIX)) trimmed.removePrefix(END_PREFIX).trim() else null
    }

    private fun beginMarker(id: String): String = "$BEGIN_PREFIX $id"

    private fun endMarker(id: String): String = "$END_PREFIX $id"

    private data class RuleBlockDocument(
        val prefixLines: List<String>,
        val blocks: List<RuleTextBlock>
    )

    companion object {
        private const val BEGIN_PREFIX: String = "# BEGIN stackupup:block"
        private const val END_PREFIX: String = "# END stackupup:block"
    }
}
