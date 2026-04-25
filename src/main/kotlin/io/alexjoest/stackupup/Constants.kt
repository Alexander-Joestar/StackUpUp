package io.alexjoest.stackupup

object StackUpUpIds {
    const val MOD_ID: String = "stackupup"
    const val CONFIG_ID: String = MOD_ID
    const val PUBLIC_ID: String = MOD_ID
    const val MOD_NAME: String = "StackUpUp"
    const val RULE_FILE_EXTENSION: String = "su"
    const val RULES_DIRECTORY_NAME: String = MOD_ID
    const val RULES_FILE_NAME: String = "main.$RULE_FILE_EXTENSION"
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
    const val LATE_AE2_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.ae2.json"
    const val LATE_ACTUALLY_ADDITIONS_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.actuallyadditions.json"
    const val LATE_CYCLOPSCORE_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.cyclopscore.json"
    const val LATE_IC2_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.ic2.json"
    const val LATE_MANTLE_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.mantle.json"
    const val LATE_REFINED_STORAGE_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.refinedstorage.json"

    const val CONFIG_TITLE_KEY: String = "$CONFIG_LANG_ROOT.title"
    const val COMMAND_USAGE_KEY: String = "$COMMAND_LANG_ROOT.usage"
    const val COMMAND_RELOAD_SUCCESS_KEY: String = "$COMMAND_LANG_ROOT.reload.success"
    const val COMMAND_EDIT_SUCCESS_KEY: String = "$COMMAND_LANG_ROOT.edit.success"
    const val COMMAND_EDIT_MISSING_KEY: String = "$COMMAND_LANG_ROOT.edit.missing"
    const val COMMAND_EDIT_UNSUPPORTED_KEY: String = "$COMMAND_LANG_ROOT.edit.unsupported"
    const val COMMAND_EDIT_FAILED_KEY: String = "$COMMAND_LANG_ROOT.edit.failed"
    const val RULE_COMPLEXITY_PREFIX_KEY: String = "$MESSAGE_LANG_ROOT.rule_complexity.prefix"
    const val RULE_COMPLEXITY_RULE_COUNT_KEY: String = "$MESSAGE_LANG_ROOT.rule_complexity.rule_count"
    const val RULE_COMPLEXITY_RULE_LENGTH_KEY: String = "$MESSAGE_LANG_ROOT.rule_complexity.rule_length"
    const val RULE_COMPLEXITY_TOTAL_LENGTH_KEY: String = "$MESSAGE_LANG_ROOT.rule_complexity.total_length"
    const val RULE_RELOAD_ERROR_PREFIX_KEY: String = "$MESSAGE_LANG_ROOT.rule_reload_error.prefix"
    const val TOOLTIP_CURRENT_MAX_KEY: String = "$TOOLTIP_LANG_ROOT.current_max"
}

object Constants {
    const val COUNT_MAGIC: Int = -42
    const val VANILLA_STACK_LIMIT: Int = 64
}
