package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.StackUpUpIds
import java.util.Locale
import net.minecraft.util.text.translation.I18n
import net.minecraft.util.text.translation.LanguageMap

internal object RuleMessages {
    private const val ERROR_ROOT: String = "${StackUpUpIds.MESSAGE_LANG_ROOT}.rule_error"
    private const val EN_US_PATH: String = "/assets/${StackUpUpIds.MOD_ID}/lang/en_us.lang"
    private val fallbackBundle: Map<String, String> by lazy { loadBundle(EN_US_PATH) }

    fun unsupportedReverseOperator(symbol: String): String = format("unsupported_reverse_operator", symbol)

    fun unsupportedComparisonOperator(symbol: String): String = format("unsupported_comparison_operator", symbol)

    fun missingComparisonOperator(): String = format("missing_comparison_operator")

    fun missingActionOperator(): String = format("missing_action_operator")

    fun trailingContent(): String = format("trailing_content")

    fun actionValueMustBeInteger(): String = format("action_value_must_be_integer")

    fun addActionMissingSymbol(): String = format("add_action_missing_symbol")

    fun addActionMissingInteger(): String = format("add_action_missing_integer")

    fun subtractActionMissingSymbol(): String = format("subtract_action_missing_symbol")

    fun subtractActionMissingInteger(): String = format("subtract_action_missing_integer")

    fun multiplyActionMissingSymbol(): String = format("multiply_action_missing_symbol")

    fun multiplyActionMissingInteger(): String = format("multiply_action_missing_integer")

    fun divideActionMissingSymbol(): String = format("divide_action_missing_symbol")

    fun divideActionMissingInteger(): String = format("divide_action_missing_integer")

    fun unsupportedActionStep(lexeme: String): String = format("unsupported_action_step", lexeme)

    fun listConditionCannotBeEmpty(): String = format("list_condition_cannot_be_empty")

    fun listConditionContainsEmptyEntry(): String = format("list_condition_contains_empty_entry")

    fun listConditionMissingRightBracket(): String = format("list_condition_missing_right_bracket")

    fun conditionMustStartWithField(): String = format("condition_must_start_with_field")

    fun unsupportedField(field: String): String = format("unsupported_field", field)

    fun conditionMissingValue(): String = format("condition_missing_value")

    fun loadFailed(lineNumber: Int, sourceName: String?, message: String): String =
        if (sourceName == null) {
            format("load_failed", lineNumber, message)
        } else {
            format("load_failed_with_source", sourceName, lineNumber, message)
        }

    fun unknownError(): String = format("unknown_error")

    private fun format(suffix: String, vararg args: Any): String {
        val key = "$ERROR_ROOT.$suffix"
        val translated = I18n.translateToLocalFormatted(key, *args)
        if (translated != key && !translated.startsWith("Format error:", ignoreCase = true)) {
            return translated
        }

        val template = fallbackBundle[key] ?: I18n.translateToFallback(key)
        if (template == key || template.startsWith("Format error:", ignoreCase = true)) {
            return key
        }
        return String.format(Locale.ROOT, template, *args)
    }

    private fun loadBundle(path: String): Map<String, String> {
        val stream = RuleMessages::class.java.getResourceAsStream(path) ?: return emptyMap()
        return stream.use(LanguageMap::parseLangFile)
    }
}
