package io.alexjoest.stackupup.rules.io

import java.io.File

class RuleStateStore(private val file: File) {
    fun readStates(): Map<String, Boolean> {
        if (!file.exists()) {
            return emptyMap()
        }
        return MarkdownStateParser.parse(file.readLines(Charsets.UTF_8)).states
    }

    fun writeStates(states: Map<String, Boolean>): Boolean {
        val lines = if (file.exists()) {
            file.readLines(Charsets.UTF_8)
        } else {
            emptyList()
        }
        val before = MarkdownStateParser.parse(lines).states
        if (before == states) {
            return false
        }
        val rendered = MarkdownStateParser.write(lines, states)
        file.parentFile?.mkdirs()
        file.writeText(rendered.joinToString(System.lineSeparator()) + System.lineSeparator(), Charsets.UTF_8)
        return true
    }
}
