package io.alexjoest.stackupup

object StackUpUpIds {
    const val MOD_ID: String = Tags.MOD_ID
    const val CONFIG_ID: String = MOD_ID
    const val PUBLIC_ID: String = MOD_ID
    const val MOD_NAME: String = Tags.MOD_NAME
    const val RULE_FILE_EXTENSION: String = "su"
    const val RULES_DIRECTORY_NAME: String = MOD_ID
    const val RULES_FILE_NAME: String = "main.$RULE_FILE_EXTENSION"
    const val WORLD_MARKDOWN_RULES_FILE_NAME: String = "main.su.md"
    const val EXAMPLE_RULES_FILE_NAME: String = "example.su"
    const val EXAMPLE_MARKDOWN_RULES_FILE_NAME: String = "example.su.md"
    const val USER_RULES_FILE_NAME: String = "user.$RULE_FILE_EXTENSION"
    const val WORLD_RULES_FILE_NAME: String = "world.$RULE_FILE_EXTENSION"

    const val CONFIG_LANG_ROOT: String = "config.$MOD_ID"
    const val COMMAND_LANG_ROOT: String = "commands.$MOD_ID"
    const val MESSAGE_LANG_ROOT: String = "message.$MOD_ID"
    const val TOOLTIP_LANG_ROOT: String = "tooltip.$MOD_ID"

    const val DEV_AUTOMATION_PREFIX: String = "$MOD_ID.dev.autoTest"
    const val DEV_AUTOMATION_LEGACY_PREFIX: String = "stackup.dev.autoTest"
    const val DEV_AUTOMATION_WORLD_FOLDER: String = "${MOD_ID}_dev_autotest"

    const val PACKAGE_NAME: String = "io.alexjoest.stackupup"
    const val CORE_PACKAGE_NAME: String = "$PACKAGE_NAME.core"
    const val CONFIG_CLASS_NAME: String = "$PACKAGE_NAME.StackUpUpConfig"
    const val PROXY_CLIENT_CLASS_NAME: String = "$PACKAGE_NAME.ProxyClient"
    const val PROXY_COMMON_CLASS_NAME: String = "$PACKAGE_NAME.ProxyCommon"
    const val CONFIG_GUI_FACTORY_CLASS_NAME: String = "$PACKAGE_NAME.config.ConfigGuiFactory"
    const val DYNAMIC_COMPAT_TRANSFORMER_CLASS_NAME: String = "$CORE_PACKAGE_NAME.DynamicCompatTransformer"
    const val STACK_LIMIT_HOOKS_CLASS_NAME: String = "$PACKAGE_NAME.StackLimitHooks"
    const val STACK_LIMIT_HOOKS_INTERNAL_NAME: String = "io/alexjoest/stackupup/StackLimitHooks"

    const val EARLY_MIXIN_CONFIG: String = "mixins.$MOD_ID.early.json"

    const val CONFIG_TITLE_KEY: String = "$CONFIG_LANG_ROOT.title"
    const val COMMAND_USAGE_KEY: String = "$COMMAND_LANG_ROOT.usage"
    const val COMMAND_STATE_GET_KEY: String = "$COMMAND_LANG_ROOT.state.get"
    const val COMMAND_STATE_SET_KEY: String = "$COMMAND_LANG_ROOT.state.set"
    const val COMMAND_STATE_MISSING_KEY: String = "$COMMAND_LANG_ROOT.state.missing"
    const val COMMAND_RELOAD_SUCCESS_KEY: String = "$COMMAND_LANG_ROOT.reload.success"
    const val COMMAND_EDIT_SUCCESS_KEY: String = "$COMMAND_LANG_ROOT.edit.success"
    const val COMMAND_EDIT_MISSING_KEY: String = "$COMMAND_LANG_ROOT.edit.missing"
    const val COMMAND_EDIT_UNSUPPORTED_KEY: String = "$COMMAND_LANG_ROOT.edit.unsupported"
    const val COMMAND_EDIT_FAILED_KEY: String = "$COMMAND_LANG_ROOT.edit.failed"
    const val RULE_COMPLEXITY_PREFIX_KEY: String = "$MESSAGE_LANG_ROOT.rule_complexity.prefix"
    const val RULE_COMPLEXITY_RULE_COUNT_KEY: String = "$MESSAGE_LANG_ROOT.rule_complexity.rule_count"
    const val RULE_COMPLEXITY_RULE_LENGTH_KEY: String = "$MESSAGE_LANG_ROOT.rule_complexity.rule_length"
    const val RULE_COMPLEXITY_TOTAL_LENGTH_KEY: String = "$MESSAGE_LANG_ROOT.rule_complexity.total_length"
    const val RULE_LIMIT_CLAMP_KEY: String = "$MESSAGE_LANG_ROOT.rule_limit.clamp"
    const val RULE_RELOAD_ERROR_PREFIX_KEY: String = "$MESSAGE_LANG_ROOT.rule_reload_error.prefix"
    const val TOOLTIP_CURRENT_MAX_KEY: String = "$TOOLTIP_LANG_ROOT.current_max"
}

object Constants {
    const val COUNT_MAGIC: Int = -42
    const val VANILLA_STACK_LIMIT: Int = 64
}
