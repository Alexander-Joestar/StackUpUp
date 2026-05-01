package io.alexjoest.stackupup.rules.io

sealed class RuleGateExpression {
    abstract fun evaluate(context: RuleGateContext): Boolean
    abstract fun dependencies(): Set<String>
    abstract fun isConstant(): Boolean

    data class State(val name: String) : RuleGateExpression() {
        override fun evaluate(context: RuleGateContext): Boolean = context.state(name)
        override fun dependencies(): Set<String> = setOf(name.lowercase())
        override fun isConstant(): Boolean = false
    }

    data class ModLoaded(val modIds: List<String>) : RuleGateExpression() {
        constructor(modId: String) : this(listOf(modId))
        override fun evaluate(context: RuleGateContext): Boolean = modIds.all { context.modLoaded(it) }
        override fun dependencies(): Set<String> = emptySet()
        override fun isConstant(): Boolean = true
    }

    data class Not(val value: RuleGateExpression) : RuleGateExpression() {
        override fun evaluate(context: RuleGateContext): Boolean = !value.evaluate(context)
        override fun dependencies(): Set<String> = value.dependencies()
        override fun isConstant(): Boolean = value.isConstant()
    }

    data class And(val left: RuleGateExpression, val right: RuleGateExpression) : RuleGateExpression() {
        override fun evaluate(context: RuleGateContext): Boolean = left.evaluate(context) && right.evaluate(context)
        override fun dependencies(): Set<String> = left.dependencies() + right.dependencies()
        override fun isConstant(): Boolean = left.isConstant() && right.isConstant()
    }

    data class Or(val left: RuleGateExpression, val right: RuleGateExpression) : RuleGateExpression() {
        override fun evaluate(context: RuleGateContext): Boolean = left.evaluate(context) || right.evaluate(context)
        override fun dependencies(): Set<String> = left.dependencies() + right.dependencies()
        override fun isConstant(): Boolean = left.isConstant() && right.isConstant()
    }
}

sealed class MarkdownGateParseResult {
    data class Success(val expression: RuleGateExpression) : MarkdownGateParseResult()
    data class Failure(val message: String, val offset: Int) : MarkdownGateParseResult()
}
