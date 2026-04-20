package io.alexjoest.stackupup.rules.io

import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.TextComponentString
import net.minecraft.util.text.TextComponentTranslation
import io.alexjoest.stackupup.StackUpUpConfig
import io.alexjoest.stackupup.StackUpUpIds

object RuleFeedback {
    fun emitReloadErrors(report: RuleReloadReport, send: (ITextComponent) -> Unit) {
        if (report.errors.isEmpty()) {
            return
        }

        send(TextComponentTranslation(StackUpUpIds.RULE_RELOAD_ERROR_PREFIX_KEY))
        report.errors.forEach { send(TextComponentString(it)) }
    }

    fun emitWarnings(report: RuleReloadReport, send: (ITextComponent) -> Unit) {
        if (!StackUpUpConfig.ruleComplexityWarnings || !report.shouldWarn) {
            return
        }

        send(TextComponentTranslation(StackUpUpIds.RULE_COMPLEXITY_PREFIX_KEY))
        report.warnings.forEach {
            send(TextComponentTranslation(it.translationKey, *it.args))
        }
    }
}
