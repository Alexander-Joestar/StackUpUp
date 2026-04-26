package io.alexjoest.stackupup.rules.io

import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.TextComponentTranslation
import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.StackUpUpIds

object RuleFeedback {
    fun emitReloadErrors(report: RuleReloadReport, send: (ITextComponent) -> Unit) {
        if (report.errors.isEmpty()) {
            return
        }

        send(TextComponentTranslation(StackUpUpIds.RULE_RELOAD_ERROR_PREFIX_KEY))
        for (error in report.errors) {
            send(error.toTextComponent())
        }
    }

    fun emitWarnings(report: RuleReloadReport, send: (ITextComponent) -> Unit) {
        if (!StackUpUpConfig.ruleComplexityWarnings || report.warnings.isEmpty()) {
            return
        }

        send(TextComponentTranslation(StackUpUpIds.RULE_COMPLEXITY_PREFIX_KEY))
        for (warning in report.warnings) {
            send(warning.toTextComponent())
        }
    }
}
