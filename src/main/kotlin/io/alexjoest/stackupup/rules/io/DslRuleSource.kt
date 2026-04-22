package io.alexjoest.stackupup.rules.io

import java.io.File

object DslRuleSource {
    fun fromLines(lines: List<String>): RuleLoadResult =
        RuleLineLoader.load(
            lines.mapIndexed { index, line ->
                RuleLineLoader.RuleLineInput(content = line, lineNumber = index + 1)
            }
        )

    fun fromFile(file: File): RuleLoadResult {
        RuleFileTemplate.ensureExists(file)
        return fromNamedLines(file.readLines(Charsets.UTF_8), file.name)
    }

    fun fromFiles(files: List<File>): RuleLoadResult {
        val inputs = ArrayList<RuleLineLoader.RuleLineInput>()
        for (file in files) {
            if (!file.exists()) {
                continue
            }
            appendNamedLines(inputs, file.readLines(Charsets.UTF_8), file.name)
        }
        return RuleLineLoader.load(inputs)
    }

    private fun fromNamedLines(lines: List<String>, sourceName: String): RuleLoadResult =
        RuleLineLoader.load(
            buildList(lines.size) {
                appendNamedLines(this, lines, sourceName)
            }
        )

    private fun appendNamedLines(
        target: MutableList<RuleLineLoader.RuleLineInput>,
        lines: List<String>,
        sourceName: String
    ) {
        for ((index, line) in lines.withIndex()) {
            target += RuleLineLoader.RuleLineInput(
                content = line,
                lineNumber = index + 1,
                sourceName = sourceName
            )
        }
    }
}
