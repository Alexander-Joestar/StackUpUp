package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.parse.DslParser

object RuleCompiler {
    fun compileLine(line: String, lineNumber: Int): CompiledRule {
        val ast = DslParser.parseLine(line)
        return CompiledRule(
            lineNumber = lineNumber,
            sourceLine = line,
            action = ast.action,
            referencedFields = ast.condition.debugFields().toSet(),
            predicate = RuleConditionCompiler.compile(ast.condition),
        )
    }
}
