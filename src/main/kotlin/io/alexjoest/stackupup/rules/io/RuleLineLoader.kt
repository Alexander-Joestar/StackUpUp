package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.rules.RuleMessageKey
import io.alexjoest.stackupup.rules.RuleMessages
import io.alexjoest.stackupup.rules.compile.CompiledRule
import io.alexjoest.stackupup.rules.compile.RuleCompiler

internal object RuleLineLoader {
    fun load(inputs: List<RuleLineInput>): RuleLoadResult {
        val rules = ArrayList<CompiledRule>(inputs.size)
        val errors = ArrayList<String>()
        var inBlockComment = false

        for ((index, input) in inputs.withIndex()) {
            val sanitized = sanitizeLine(input.content, inBlockComment)
            inBlockComment = sanitized.inBlockComment
            val line = sanitized.content.trim()
            if (line.isEmpty()) {
                continue
            }

            try {
                rules += RuleCompiler.compileLine(line, index + 1)
            } catch (t: Throwable) {
                errors += input.formatError(t)
            }
        }

        return RuleLoadResult.compiled(rules, errors)
    }

    private fun sanitizeLine(rawLine: String, initialInBlockComment: Boolean): SanitizedLine {
        val builder = StringBuilder(rawLine.length)
        var index = 0
        var inBlockComment = initialInBlockComment

        while (index < rawLine.length) {
            val current = rawLine[index]
            val next = rawLine.getOrNull(index + 1)

            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false
                    index += 2
                } else {
                    index++
                }
                continue
            }

            if (current == '/' && next == '*') {
                inBlockComment = true
                index += 2
                continue
            }

            if (current == '#') {
                break
            }

            if (current == '/' && next == '/') {
                break
            }

            builder.append(current)
            index++
        }

        return SanitizedLine(builder.toString(), inBlockComment)
    }

    internal data class RuleLineInput(
        val content: String,
        val lineNumber: Int,
        val sourceName: String? = null
    ) {
        fun formatError(throwable: Throwable): String {
            val message = throwable.message ?: RuleMessages.format(RuleMessageKey.UNKNOWN_ERROR)
            return if (sourceName == null) {
                RuleMessages.format(RuleMessageKey.LOAD_FAILED, lineNumber, message)
            } else {
                RuleMessages.format(RuleMessageKey.LOAD_FAILED_WITH_SOURCE, sourceName, lineNumber, message)
            }
        }
    }

    private data class SanitizedLine(
        val content: String,
        val inBlockComment: Boolean
    )
}
