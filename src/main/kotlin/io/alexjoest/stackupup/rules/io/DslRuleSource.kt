package io.alexjoest.stackupup.rules.io

import java.io.File

object DslRuleSource {
    fun fromLines(lines: List<String>, gateContext: RuleGateContext = RuleGateContext.EMPTY): RuleLoadResult {
        val inputs = lines.mapIndexed { index, line ->
            RuleLineLoader.RuleLineInput(content = line, lineNumber = index + 1)
        }
        return RuleLineLoader.load(RuleConditionalPreprocessor.filter(inputs, gateContext))
    }

    fun fromFile(file: File, gateContext: RuleGateContext = RuleGateContext.EMPTY): RuleLoadResult {
        RuleFileTemplate.ensureExists(file)
        return fromNamedLines(file.readLines(Charsets.UTF_8), file.name, gateContext)
    }

    fun fromFiles(files: List<File>, gateContext: RuleGateContext = RuleGateContext.EMPTY): RuleLoadResult {
        val inputs = ArrayList<RuleLineLoader.RuleLineInput>()
        for (file in files) {
            if (!file.exists()) {
                continue
            }
            appendNamedLines(inputs, file.readLines(Charsets.UTF_8), file.name)
        }
        return RuleLineLoader.load(RuleConditionalPreprocessor.filter(inputs, gateContext))
    }

    private fun fromNamedLines(lines: List<String>, sourceName: String, gateContext: RuleGateContext): RuleLoadResult = RuleLineLoader.load(
        buildList(lines.size) {
            appendNamedLines(this, lines, sourceName)
        }.let { RuleConditionalPreprocessor.filter(it, gateContext) },
    )

    private fun appendNamedLines(target: MutableList<RuleLineLoader.RuleLineInput>, lines: List<String>, sourceName: String) {
        for ((index, line) in lines.withIndex()) {
            target += RuleLineLoader.RuleLineInput(
                content = line,
                lineNumber = index + 1,
                sourceName = sourceName,
            )
        }
    }
}
