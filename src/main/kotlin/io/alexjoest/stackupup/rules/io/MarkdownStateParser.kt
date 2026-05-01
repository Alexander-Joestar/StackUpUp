package io.alexjoest.stackupup.rules.io

internal object MarkdownStateParser {
    fun parse(lines: List<String>): MarkdownStateDocument {
        val stateRanges = findStateSections(lines)
        if (stateRanges.isEmpty()) {
            return MarkdownStateDocument(
                lines = lines,
                stateSectionLines = emptyList(),
                states = emptyMap(),
                errors = emptyList(),
            )
        }

        val sectionLines = ArrayList<String>()
        val states = LinkedHashMap<String, Boolean>()
        val errors = ArrayList<String>()
        for (range in stateRanges) {
            sectionLines += lines.subList(range.first, range.last + 1)
            var lineNum = range.first + 1
            for (line in lines.subList(range.first, range.last + 1)) {
                if (!line.trimStart().startsWith("-") && !line.trimStart().startsWith("*")) {
                    lineNum++
                    continue
                }
                val parsed = parseStateLine(line)
                if (parsed == null) {
                    errors += "Line $lineNum: invalid state declaration '${line.trim()}' - expected '- name = true|false'"
                } else {
                    val (name, value) = parsed
                    states[name] = value
                }
                lineNum++
            }
        }

        return MarkdownStateDocument(
            lines = lines,
            stateSectionLines = sectionLines,
            states = states,
            errors = errors,
        )
    }

    fun write(lines: List<String>, states: Map<String, Boolean>): List<String> {
        val source = lines.toMutableList()
        val stateRanges = findStateSections(source)
        if (stateRanges.isEmpty()) {
            return appendStateSection(source, states)
        }
        val stateRange = stateRanges.first()

        val orderedKeys = LinkedHashSet<String>(states.keys)
        val seen = LinkedHashSet<String>()
        var index = stateRange.first + 1
        while (index <= stateRange.last && index < source.size) {
            val parsed = parseStateLine(source[index])
            if (parsed != null) {
                val (name, value, indent) = parsed
                if (name in orderedKeys) {
                    source[index] = "${" ".repeat(indent)}- $name = ${states[name] == true}"
                    seen += name
                }
            }
            index++
        }

        val missing = orderedKeys.filterNot { it in seen }
        if (missing.isNotEmpty()) {
            val insertAt = findStateInsertIndex(source, stateRange.last + 1)
            val rendered = missing.map { "- $it = ${states[it] == true}" }
            source.addAll(insertAt, rendered)
        }
        return source
    }

    private fun appendStateSection(lines: MutableList<String>, states: Map<String, Boolean>): List<String> {
        if (lines.isNotEmpty() && lines.last().isNotBlank()) {
            lines += ""
        }
        lines += "# state"
        for ((name, value) in states) {
            lines += "- $name = $value"
        }
        return lines
    }

    private fun findStateSections(lines: List<String>): List<IntRange> {
        val ranges = ArrayList<IntRange>()
        var start = -1
        for (index in lines.indices) {
            val trimmed = lines[index].trim()
            if (trimmed.equals("# state", ignoreCase = true)) {
                start = index
                var end = lines.lastIndex
                for (next in index + 1 until lines.size) {
                    val nextTrimmed = lines[next].trim()
                    if (nextTrimmed.startsWith("# ") && !nextTrimmed.equals("# state", ignoreCase = true)) {
                        end = next - 1
                        break
                    }
                }
                ranges += start..end
            }
        }
        return ranges
    }

    private fun findStateInsertIndex(lines: List<String>, fallback: Int): Int {
        var index = fallback
        while (index < lines.size) {
            if (lines[index].trim().startsWith("# ")) {
                return index
            }
            index++
        }
        return lines.size
    }

    private fun parseStateLine(line: String): ParsedStateLine? {
        val trimmed = line.trimStart()
        val indent = line.length - trimmed.length
        val content = when {
            trimmed.startsWith("- ") -> trimmed.removePrefix("- ").trim()
            trimmed.startsWith("* ") -> trimmed.removePrefix("* ").trim()
            else -> return null
        }
        val parts = content.split("=", limit = 2)
        if (parts.size != 2) {
            return null
        }
        val name = parts[0].trim()
        val value = parts[1].trim().lowercase()
        if (name.isEmpty() || (value != "true" && value != "false")) {
            return null
        }
        return ParsedStateLine(name, value == "true", indent)
    }

    private data class ParsedStateLine(val name: String, val value: Boolean, val indent: Int)
}

data class MarkdownStateDocument(
    val lines: List<String>,
    val stateSectionLines: List<String>,
    val states: Map<String, Boolean>,
    val errors: List<String> = emptyList(),
)
