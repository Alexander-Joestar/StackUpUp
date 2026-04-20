package io.alexjoest.stackupup.rules.compile

import io.alexjoest.stackupup.rules.ast.RuleStepAst
import io.alexjoest.stackupup.rules.model.RuleAction
import io.alexjoest.stackupup.rules.model.RuleStep
import io.alexjoest.stackupup.rules.parse.DslParser

object RuleCompiler {
    fun compileLine(line: String, lineNumber: Int): CompiledRule {
        val ast = DslParser.parseLine(line)
        return CompiledRule(
            lineNumber = lineNumber,
            sourceLine = line,
            action = RuleAction(ast.action.steps.map(::compileActionStep)),
            referencedFields = ast.condition.debugFields().toSet(),
            predicate = RuleConditionCompiler.compile(ast.condition)
        )
    }

    private fun compileActionStep(step: RuleStepAst): RuleStep {
        return RuleStep(step.kind, step.value)
    }
}
