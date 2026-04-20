package io.alexjoest.stackupup.rules.io

import java.io.File

class DslRuleSource private constructor(
    private val inputsProvider: () -> List<RuleLineLoader.RuleLineInput>
) {
    fun load(): RuleLoadResult = RuleLineLoader.load(inputsProvider())

    companion object {
        fun fromLines(lines: List<String>): DslRuleSource = DslRuleSource {
            lines.mapIndexed { index, line ->
                RuleLineLoader.RuleLineInput(content = line, lineNumber = index + 1)
            }
        }

        fun fromFile(file: File): DslRuleSource = DslRuleSource {
            RuleFileTemplate.ensureExists(file)
            file.readLines(Charsets.UTF_8).mapIndexed { index, line ->
                RuleLineLoader.RuleLineInput(content = line, lineNumber = index + 1, sourceName = file.name)
            }
        }

        fun fromFiles(files: List<File>): DslRuleSource = DslRuleSource {
            buildList {
                files.forEach { file ->
                    if (!file.exists()) {
                        return@forEach
                    }

                    file.readLines(Charsets.UTF_8).forEachIndexed { index, line ->
                        add(
                            RuleLineLoader.RuleLineInput(
                                content = line,
                                lineNumber = index + 1,
                                sourceName = file.name
                            )
                        )
                    }
                }
            }
        }
    }
}

