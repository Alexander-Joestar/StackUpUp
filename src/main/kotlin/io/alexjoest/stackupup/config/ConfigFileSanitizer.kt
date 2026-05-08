package io.alexjoest.stackupup.config

import io.alexjoest.stackupup.StackUpUp
import java.io.File

internal object ConfigFileSanitizer {
    private val allowedRootCategories = linkedSetOf(
        "general",
        "client",
        "compatibility",
    )

    fun sanitize(configDirectory: File) {
        val configFile = File(configDirectory, "${StackUpUp.PUBLIC_ID}.cfg")
        if (!configFile.exists()) {
            return
        }

        val originalText = configFile.readText(Charsets.UTF_8)
        val sanitized = sanitizeRootCategories(originalText, allowedRootCategories)
        if (sanitized.removedRootCategories.isEmpty()) {
            return
        }

        configFile.writeText(sanitized.text, Charsets.UTF_8)
        if (sanitized.removedRootCategories.isNotEmpty()) {
            StackUpUp.logger?.info(
                "Sanitized {} by removing unsupported root categories: {}",
                configFile.name,
                sanitized.removedRootCategories.joinToString(", "),
            )
        }
    }

    private data class SanitizedConfig(
        val text: String,
        val removedRootCategories: Set<String>,
    )

    private fun sanitizeRootCategories(source: String, allowedRoots: Set<String>): SanitizedConfig {
        val normalized = source.removePrefix("\uFEFF")
        val lines = normalized.splitToSequence('\n').toList()
        val output = StringBuilder(normalized.length)
        val removedRoots = linkedSetOf<String>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val lineWithoutCarriageReturn = line.trimEnd('\r')
            val trimmed = lineWithoutCarriageReturn.trimStart()
            val indent = lineWithoutCarriageReturn.substring(0, lineWithoutCarriageReturn.length - trimmed.length)
            val categoryName = parseRootCategoryName(trimmed)
            if (categoryName != null && categoryName !in allowedRoots) {
                removedRoots += categoryName
                i += skipCategoryBlock(lines, i, categoryName)
                continue
            }
            output.append(indent).append(trimmed)
            if (i < lines.size - 1) {
                output.append('\n')
            }
            i++
        }

        return SanitizedConfig(output.toString(), removedRoots)
    }

    private fun parseRootCategoryName(trimmedLine: String): String? {
        if (!trimmedLine.endsWith("{")) {
            return null
        }
        val category = trimmedLine.removeSuffix("{").trim()
        return if (category.isNotEmpty() && category.none { it.isWhitespace() }) category else null
    }

    private fun skipCategoryBlock(lines: List<String>, startIndex: Int, categoryName: String): Int {
        var depth = 0
        var consumed = 0
        while (startIndex + consumed < lines.size) {
            val line = lines[startIndex + consumed].trimEnd('\r')
            depth += countCharOutsideQuotes(line, '{')
            depth -= countCharOutsideQuotes(line, '}')
            consumed++
            if (depth <= 0) {
                break
            }
        }
        return consumed
    }

    private fun countCharOutsideQuotes(line: String, target: Char): Int {
        var count = 0
        var inQuotes = false
        var escaped = false
        for (char in line) {
            if (escaped) {
                escaped = false
                continue
            }
            when (char) {
                '\\' -> escaped = true
                '"' -> inQuotes = !inQuotes
                target -> if (!inQuotes) count++
            }
        }
        return count
    }
}
