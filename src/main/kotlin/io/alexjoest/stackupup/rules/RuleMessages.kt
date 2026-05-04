package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.StackUpUpIds
import net.minecraft.util.text.translation.LanguageMap

internal enum class RuleMessageKey(val suffix: String) {
    UNSUPPORTED_REVERSE_OPERATOR("unsupported_reverse_operator"),
    UNSUPPORTED_COMPARISON_OPERATOR("unsupported_comparison_operator"),
    MISSING_COMPARISON_OPERATOR("missing_comparison_operator"),
    MISSING_ACTION_OPERATOR("missing_action_operator"),
    TRAILING_CONTENT("trailing_content"),
    ACTION_VALUE_MUST_BE_INTEGER("action_value_must_be_integer"),
    ADD_ACTION_MISSING_SYMBOL("add_action_missing_symbol"),
    ADD_ACTION_MISSING_INTEGER("add_action_missing_integer"),
    SUBTRACT_ACTION_MISSING_SYMBOL("subtract_action_missing_symbol"),
    SUBTRACT_ACTION_MISSING_INTEGER("subtract_action_missing_integer"),
    MULTIPLY_ACTION_MISSING_SYMBOL("multiply_action_missing_symbol"),
    MULTIPLY_ACTION_MISSING_INTEGER("multiply_action_missing_integer"),
    DIVIDE_ACTION_MISSING_SYMBOL("divide_action_missing_symbol"),
    DIVIDE_ACTION_MISSING_INTEGER("divide_action_missing_integer"),
    UNSUPPORTED_ACTION_STEP("unsupported_action_step"),
    LIST_CONDITION_CANNOT_BE_EMPTY("list_condition_cannot_be_empty"),
    LIST_CONDITION_CONTAINS_EMPTY_ENTRY("list_condition_contains_empty_entry"),
    LIST_CONDITION_MISSING_RIGHT_BRACKET("list_condition_missing_right_bracket"),
    CONDITION_MUST_START_WITH_FIELD("condition_must_start_with_field"),
    UNSUPPORTED_FIELD("unsupported_field"),
    CONDITION_MISSING_VALUE("condition_missing_value"),
    LOAD_FAILED("load_failed"),
    LOAD_FAILED_WITH_SOURCE("load_failed_with_source"),
    UNKNOWN_ERROR("unknown_error"),
    GATE_EMPTY_EXPRESSION("gate_empty_expression"),
    GATE_UNEXPECTED_TOKEN("gate_unexpected_token"),
    GATE_EXPECTED_FUNCTION("gate_expected_function"),
    GATE_EXPECTED_LEFT_PAREN("gate_expected_left_paren"),
    GATE_EXPECTED_RIGHT_PAREN("gate_expected_right_paren"),
    GATE_EXPECTED_STRING_ARG("gate_expected_string_arg"),
    GATE_EXPECTED_STRING_ARG_AFTER_COMMA("gate_expected_string_arg_after_comma"),
    GATE_STATE_TAKES_ONE_ARG("gate_state_takes_one_arg"),
    GATE_UNKNOWN_FUNCTION("gate_unknown_function"),
    GATE_EXPECTED_AND("gate_expected_and"),
    GATE_EXPECTED_OR("gate_expected_or"),
    GATE_UNEXPECTED_CHARACTER("gate_unexpected_character"),
    GATE_UNTERMINATED_ESCAPE("gate_unterminated_escape"),
    GATE_UNTERMINATED_STRING("gate_unterminated_string"),
    GATE_PARSE_ERROR("gate_parse_error"),
    STATE_INVALID_DECLARATION("state_invalid_declaration"),
    STATE_ERROR_PREFIX("state_error_prefix"),
    ;

    val translationKey: String = "${StackUpUpIds.MESSAGE_LANG_ROOT}.rule_error.$suffix"
}

internal object RuleMessages {
    private var loadedLanguageCode: String = ""

    init {
        injectLanguage("en_us")
        loadedLanguageCode = "en_us"
    }

    // 这里只保留“消息定义与格式化辅助”职责。
    // 玩家可见文本应尽量传递 LocalizedMessage，到客户端边界再转组件。
    fun message(key: RuleMessageKey, vararg args: Any): LocalizedMessage = LocalizedMessage(key.translationKey, args.toList())

    fun exception(key: RuleMessageKey, vararg args: Any): LocalizedRuleException = LocalizedRuleException(message(key, *args))

    @Suppress("DEPRECATION")
    fun formatRaw(translationKey: String, vararg args: Any): String = net.minecraft.util.text.translation.I18n.translateToLocalFormatted(translationKey, *args)

    fun syncLanguage(languageCode: String) {
        val normalizedCode = languageCode.lowercase()
        if (normalizedCode == loadedLanguageCode) {
            return
        }

        injectLanguage("en_us")
        if (normalizedCode != "en_us") {
            injectLanguage(normalizedCode)
        }
        loadedLanguageCode = normalizedCode
    }

    private fun injectLanguage(languageCode: String) {
        RuleMessages::class.java
            .getResourceAsStream("/assets/${StackUpUpIds.MOD_ID}/lang/$languageCode.lang")
            ?.use(LanguageMap::inject)
    }
}
