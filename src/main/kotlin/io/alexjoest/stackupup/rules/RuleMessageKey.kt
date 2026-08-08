package io.alexjoest.stackupup.rules

/**
 * 自有本地化键的统一模型（T8.1 A/B 收敛）。
 *
 * 原 A 类（规则错误消息枚举）与原 B 类（`Constants.kt` 中 `StackUpUpIds` 的裸字符串键）
 * 收敛为单一枚举；每个条目持有完整 `translationKey`，与 lang 文件键一一对应，
 * 由 [io.alexjoest.stackupup.LocalizedMessages] 加载时校验枚举与文件键集合双向一致。
 * C 类 `@Config.LangKey`（`StackUpUpConfig` 的 11 处）保持 Forge 配置注解语义，不并入本模型。
 *
 * 键名即事实源：重命名条目或键值属于兼容性变更，必须先同步两个 lang 文件，
 * 再由双语言完整性测试兜底。
 */
internal enum class RuleMessageKey(val translationKey: String) {
    // ---- message.stackupup.rule_error.*（原 A 类） ----
    UNSUPPORTED_REVERSE_OPERATOR("message.stackupup.rule_error.unsupported_reverse_operator"),
    UNSUPPORTED_COMPARISON_OPERATOR("message.stackupup.rule_error.unsupported_comparison_operator"),
    UNSUPPORTED_OPERATOR_FOR_FIELD("message.stackupup.rule_error.unsupported_operator_for_field"),
    MISSING_COMPARISON_OPERATOR("message.stackupup.rule_error.missing_comparison_operator"),
    MISSING_ACTION_OPERATOR("message.stackupup.rule_error.missing_action_operator"),
    TRAILING_CONTENT("message.stackupup.rule_error.trailing_content"),
    ACTION_VALUE_MUST_BE_INTEGER("message.stackupup.rule_error.action_value_must_be_integer"),
    ADD_ACTION_MISSING_SYMBOL("message.stackupup.rule_error.add_action_missing_symbol"),
    ADD_ACTION_MISSING_INTEGER("message.stackupup.rule_error.add_action_missing_integer"),
    SUBTRACT_ACTION_MISSING_SYMBOL("message.stackupup.rule_error.subtract_action_missing_symbol"),
    SUBTRACT_ACTION_MISSING_INTEGER("message.stackupup.rule_error.subtract_action_missing_integer"),
    MULTIPLY_ACTION_MISSING_SYMBOL("message.stackupup.rule_error.multiply_action_missing_symbol"),
    MULTIPLY_ACTION_MISSING_INTEGER("message.stackupup.rule_error.multiply_action_missing_integer"),
    DIVIDE_ACTION_MISSING_SYMBOL("message.stackupup.rule_error.divide_action_missing_symbol"),
    DIVIDE_ACTION_MISSING_INTEGER("message.stackupup.rule_error.divide_action_missing_integer"),
    UNSUPPORTED_ACTION_STEP("message.stackupup.rule_error.unsupported_action_step"),
    LIST_CONDITION_CANNOT_BE_EMPTY("message.stackupup.rule_error.list_condition_cannot_be_empty"),
    LIST_CONDITION_CONTAINS_EMPTY_ENTRY("message.stackupup.rule_error.list_condition_contains_empty_entry"),
    LIST_CONDITION_MISSING_RIGHT_BRACKET("message.stackupup.rule_error.list_condition_missing_right_bracket"),
    CONDITION_MUST_START_WITH_FIELD("message.stackupup.rule_error.condition_must_start_with_field"),
    UNSUPPORTED_FIELD("message.stackupup.rule_error.unsupported_field"),
    CONDITION_MISSING_VALUE("message.stackupup.rule_error.condition_missing_value"),
    LOAD_FAILED("message.stackupup.rule_error.load_failed"),
    LOAD_FAILED_WITH_SOURCE("message.stackupup.rule_error.load_failed_with_source"),
    UNKNOWN_ERROR("message.stackupup.rule_error.unknown_error"),
    GATE_EMPTY_EXPRESSION("message.stackupup.rule_error.gate_empty_expression"),
    GATE_UNEXPECTED_TOKEN("message.stackupup.rule_error.gate_unexpected_token"),
    GATE_EXPECTED_FUNCTION("message.stackupup.rule_error.gate_expected_function"),
    GATE_EXPECTED_LEFT_PAREN("message.stackupup.rule_error.gate_expected_left_paren"),
    GATE_EXPECTED_RIGHT_PAREN("message.stackupup.rule_error.gate_expected_right_paren"),
    GATE_EXPECTED_STRING_ARG("message.stackupup.rule_error.gate_expected_string_arg"),
    GATE_EXPECTED_STRING_ARG_AFTER_COMMA("message.stackupup.rule_error.gate_expected_string_arg_after_comma"),
    GATE_STATE_TAKES_ONE_ARG("message.stackupup.rule_error.gate_state_takes_one_arg"),
    GATE_UNKNOWN_FUNCTION("message.stackupup.rule_error.gate_unknown_function"),
    GATE_EXPECTED_AND("message.stackupup.rule_error.gate_expected_and"),
    GATE_EXPECTED_OR("message.stackupup.rule_error.gate_expected_or"),
    GATE_UNEXPECTED_CHARACTER("message.stackupup.rule_error.gate_unexpected_character"),
    GATE_UNTERMINATED_ESCAPE("message.stackupup.rule_error.gate_unterminated_escape"),
    GATE_UNTERMINATED_STRING("message.stackupup.rule_error.gate_unterminated_string"),
    GATE_PARSE_ERROR("message.stackupup.rule_error.gate_parse_error"),
    STATE_INVALID_DECLARATION("message.stackupup.rule_error.state_invalid_declaration"),
    STATE_ERROR_PREFIX("message.stackupup.rule_error.state_error_prefix"),
    INVALID_ITEM_LITERAL("message.stackupup.rule_error.invalid_item_literal"),
    ITEM_META_NOT_INTEGER("message.stackupup.rule_error.item_meta_not_integer"),
    ITEM_META_NEGATIVE("message.stackupup.rule_error.item_meta_negative"),
    ITEM_META_MISSING_ITEM_ID("message.stackupup.rule_error.item_meta_missing_item_id"),
    UNTERMINATED_QUOTED_LITERAL("message.stackupup.rule_error.unterminated_quoted_literal"),
    EMPTY_QUOTED_LITERAL("message.stackupup.rule_error.empty_quoted_literal"),

    // ---- commands.stackupup.*（原 B 类） ----
    COMMAND_USAGE("commands.stackupup.usage"),
    COMMAND_RELOAD_SUCCESS("commands.stackupup.reload.success"),
    COMMAND_EDIT_SUCCESS("commands.stackupup.edit.success"),
    COMMAND_EDIT_MISSING("commands.stackupup.edit.missing"),
    COMMAND_EDIT_UNSUPPORTED("commands.stackupup.edit.unsupported"),
    COMMAND_EDIT_FAILED("commands.stackupup.edit.failed"),
    COMMAND_STATE_GET("commands.stackupup.state.get"),
    COMMAND_STATE_SET("commands.stackupup.state.set"),
    COMMAND_STATE_MISSING("commands.stackupup.state.missing"),

    // ---- message.stackupup.*（原 B 类） ----
    RULE_COMPLEXITY_PREFIX("message.stackupup.rule_complexity.prefix"),
    RULE_RELOAD_ERROR_PREFIX("message.stackupup.rule_reload_error.prefix"),
    RULE_COMPLEXITY_RULE_COUNT("message.stackupup.rule_complexity.rule_count"),
    RULE_COMPLEXITY_RULE_LENGTH("message.stackupup.rule_complexity.rule_length"),
    RULE_COMPLEXITY_TOTAL_LENGTH("message.stackupup.rule_complexity.total_length"),
    RULE_LIMIT_CLAMP("message.stackupup.rule_limit.clamp"),

    // ---- config.stackupup.*（原 B 类，仅供配置 GUI 标题） ----
    CONFIG_TITLE("config.stackupup.title"),

    // ---- tooltip.stackupup.*（原 B 类） ----
    TOOLTIP_CURRENT_MAX("tooltip.stackupup.current_max"),
    ;

    companion object {
        private val byTranslationKey: Map<String, RuleMessageKey> by lazy {
            entries.associateBy(RuleMessageKey::translationKey)
        }

        fun fromTranslationKey(translationKey: String): RuleMessageKey? = byTranslationKey[translationKey]
    }
}
