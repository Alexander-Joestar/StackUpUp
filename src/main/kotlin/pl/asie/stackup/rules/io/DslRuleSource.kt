package pl.asie.stackup.rules.io

import pl.asie.stackup.rules.compile.CompiledRule
import pl.asie.stackup.rules.compile.RuleCompiler
import pl.asie.stackup.rules.compile.RuleSnapshot
import java.io.File

class DslRuleSource private constructor(
    private val linesProvider: () -> List<String>
) {
    fun load(): RuleLoadResult {
        val rules = ArrayList<CompiledRule>()
        val errors = ArrayList<String>()

        linesProvider().forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) {
                return@forEachIndexed
            }

            try {
                rules += RuleCompiler.compileLine(line, lineNumber)
            } catch (t: Throwable) {
                errors += "第 $lineNumber 行加载失败：${t.message ?: "未知错误"}"
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

    companion object {
        fun fromLines(lines: List<String>): DslRuleSource = DslRuleSource { lines }

        fun fromFile(file: File): DslRuleSource = DslRuleSource {
            if (!file.exists()) {
                emptyList()
            } else {
                file.readLines(Charsets.UTF_8)
            }
        }
    }
}
