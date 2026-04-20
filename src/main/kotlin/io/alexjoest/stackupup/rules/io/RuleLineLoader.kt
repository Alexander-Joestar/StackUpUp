package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.rules.compile.CompiledRule
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import io.alexjoest.stackupup.rules.compile.RuleSnapshot

internal object RuleLineLoader {
    fun load(inputs: List<RuleLineInput>): RuleLoadResult {
        val rules = ArrayList<CompiledRule>()
        val errors = ArrayList<String>()
        var inBlockComment = false

        inputs.forEachIndexed { index, input ->
            val sanitized = sanitizeLine(input.content, inBlockComment)
            inBlockComment = sanitized.inBlockComment
            val line = sanitized.content.trim()
            if (line.isEmpty()) {
                return@forEachIndexed
            }

            try {
                rules += RuleCompiler.compileLine(line, index + 1)
            } catch (t: Throwable) {
                val prefix = input.sourceName?.let { "[$it] " }.orEmpty()
                errors += "${prefix}第 ${input.lineNumber} 行加载失败：${t.message ?: "未知错误"}"
            }
        }

        return RuleLoadResult(
            snapshot = RuleSnapshot(
                version = System.nanoTime(),
                rules = rules
            ),
            errors = errors
        )
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

        return SanitizedLine(
            content = builder.toString(),
            inBlockComment = inBlockComment
        )
    }

    internal data class RuleLineInput(
        val content: String,
        val lineNumber: Int,
        val sourceName: String? = null
    )

    private data class SanitizedLine(
        val content: String,
        val inBlockComment: Boolean
    )
}
