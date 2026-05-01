package io.alexjoest.stackupup.config

internal object RawConfigFileScanner {
    fun sanitizeRootCategories(source: String, allowedRootCategories: Set<String>): SanitizedText {
        val normalizedSource = source.removePrefix("\uFEFF")
        val keptLines = ArrayList<String>()
        val removedRootCategories = LinkedHashSet<String>()
        var depth = 0
        var skipping = false
        val separator = detectLineSeparator(source)

        forEachLine(normalizedSource) { line ->
            val trimmed = contentBeforeComment(line).trim()
            if (!skipping && depth == 0) {
                val categoryName = topLevelCategoryName(trimmed)
                if (categoryName != null && categoryName !in allowedRootCategories) {
                    removedRootCategories += categoryName
                    skipping = true
                }
            }

            if (!skipping) {
                keptLines += line
            }
            depth += braceDelta(line)
            if (depth <= 0) {
                depth = 0
                skipping = false
            }
        }

        return SanitizedText(
            text = renderLines(keptLines, separator, source.endsWith(separator)),
            removedRootCategories = removedRootCategories.toList(),
        )
    }

    fun readBooleanCategory(source: String, categoryName: String): Map<String, Boolean> {
        val normalizedSource = source.removePrefix("\uFEFF")
        val result = LinkedHashMap<String, Boolean>()
        var depth = 0
        var collecting = false

        forEachLine(normalizedSource) { line ->
            val trimmed = contentBeforeComment(line).trim()
            if (!collecting && depth == 0) {
                val rootCategory = topLevelCategoryName(trimmed)
                if (rootCategory == categoryName) {
                    collecting = true
                }
            } else if (collecting && depth >= 1) {
                parseBooleanEntry(trimmed)?.let { (key, value) ->
                    result[key] = value
                }
            }
            depth += braceDelta(line)
            if (collecting && depth <= 0) {
                collecting = false
                depth = 0
            }
        }

        return result
    }

    private fun topLevelCategoryName(trimmedLine: String): String? {
        if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") || trimmedLine.startsWith("//")) {
            return null
        }
        if (!trimmedLine.endsWith("{")) {
            return null
        }
        return trimmedLine.removeSuffix("{").trim().takeIf { it.isNotEmpty() }
    }

    private fun parseBooleanEntry(trimmedLine: String): Pair<String, Boolean>? {
        if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") || trimmedLine.startsWith("//")) {
            return null
        }
        if (!trimmedLine.startsWith("B:") && !trimmedLine.startsWith("b:")) {
            return null
        }
        val keyPart = trimmedLine.substringAfter(':', "").substringBefore('=', "").trim()
        val valuePart = trimmedLine.substringAfter('=', "").trim()
        if (keyPart.isEmpty() || valuePart.isEmpty()) {
            return null
        }

        val normalizedKey = keyPart.removeSurrounding("\"")
        val normalizedValue = when (valuePart.lowercase()) {
            "true" -> true
            "false" -> false
            else -> return null
        }
        return normalizedKey to normalizedValue
    }

    private fun braceDelta(line: String): Int {
        val effectiveLine = contentBeforeComment(line)
        var delta = 0
        var inQuotes = false
        var index = 0
        while (index < effectiveLine.length) {
            val char = effectiveLine[index]
            if (char == '"') {
                inQuotes = !inQuotes
            } else if (!inQuotes) {
                when (char) {
                    '{' -> delta++
                    '}' -> delta--
                }
            }
            index++
        }
        return delta
    }

    private fun contentBeforeComment(line: String): String {
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            if (char == '"') {
                inQuotes = !inQuotes
            } else if (!inQuotes) {
                if (char == '#') {
                    return line.substring(0, index)
                }
                if (char == '/' && index + 1 < line.length && line[index + 1] == '/') {
                    return line.substring(0, index)
                }
            }
            index++
        }
        return line
    }

    private fun detectLineSeparator(source: String): String = when {
        source.contains("\r\n") -> "\r\n"
        source.contains("\n") -> "\n"
        source.contains("\r") -> "\r"
        else -> System.lineSeparator()
    }

    private fun renderLines(lines: List<String>, separator: String, preserveTrailingSeparator: Boolean): String {
        if (lines.isEmpty()) {
            return ""
        }
        val rendered = lines.joinToString(separator)
        return if (preserveTrailingSeparator) rendered + separator else rendered
    }

    private inline fun forEachLine(source: String, action: (String) -> Unit) {
        var start = 0
        val length = source.length
        while (start < length) {
            var end = start
            while (end < length) {
                val char = source[end]
                if (char == '\n' || char == '\r') {
                    break
                }
                end++
            }
            action(source.substring(start, end))
            if (end >= length) {
                return
            }
            start = if (source[end] == '\r' && end + 1 < length && source[end + 1] == '\n') {
                end + 2
            } else {
                end + 1
            }
        }
    }

    internal data class SanitizedText(val text: String, val removedRootCategories: List<String>)
}
