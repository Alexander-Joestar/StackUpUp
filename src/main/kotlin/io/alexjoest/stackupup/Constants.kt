package io.alexjoest.stackupup

import io.alexjoest.stackupup.rules.RuleMessageKey

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
    const val LATE_AE2_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.ae2.json"
    const val LATE_ACTUALLY_ADDITIONS_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.actuallyadditions.json"
    const val LATE_CYCLOPSCORE_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.cyclopscore.json"
    const val LATE_BRANDONSCORE_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.brandonscore.json"
    const val LATE_ENDERIO_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.enderio.json"
    const val LATE_IC2_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.ic2.json"
    const val LATE_MANTLE_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.mantle.json"
    const val LATE_REFINED_STORAGE_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.refinedstorage.json"
    const val LATE_STORAGE_NETWORK_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.storagenetwork.json"
    const val LATE_INTEGRATEDDYNAMICS_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.integrateddynamics.json"
    const val LATE_LIMELIB_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.limelib.json"
    const val LATE_IMMERSIVEENGINEERING_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.immersiveengineering.json"

    // B 类裸字符串键已收敛为 RuleMessageKey 统一键模型（T8.1）；
    // 以下投影仅为既有调用方兼容，不再维护独立字面量，值由枚举唯一决定。
    val CONFIG_TITLE_KEY: String = RuleMessageKey.CONFIG_TITLE.translationKey
    val COMMAND_USAGE_KEY: String = RuleMessageKey.COMMAND_USAGE.translationKey
    val COMMAND_STATE_GET_KEY: String = RuleMessageKey.COMMAND_STATE_GET.translationKey
    val COMMAND_STATE_SET_KEY: String = RuleMessageKey.COMMAND_STATE_SET.translationKey
    val COMMAND_STATE_MISSING_KEY: String = RuleMessageKey.COMMAND_STATE_MISSING.translationKey
    val COMMAND_RELOAD_SUCCESS_KEY: String = RuleMessageKey.COMMAND_RELOAD_SUCCESS.translationKey
    val COMMAND_EDIT_SUCCESS_KEY: String = RuleMessageKey.COMMAND_EDIT_SUCCESS.translationKey
    val COMMAND_EDIT_MISSING_KEY: String = RuleMessageKey.COMMAND_EDIT_MISSING.translationKey
    val COMMAND_EDIT_UNSUPPORTED_KEY: String = RuleMessageKey.COMMAND_EDIT_UNSUPPORTED.translationKey
    val COMMAND_EDIT_FAILED_KEY: String = RuleMessageKey.COMMAND_EDIT_FAILED.translationKey
    val RULE_COMPLEXITY_PREFIX_KEY: String = RuleMessageKey.RULE_COMPLEXITY_PREFIX.translationKey
    val RULE_COMPLEXITY_RULE_COUNT_KEY: String = RuleMessageKey.RULE_COMPLEXITY_RULE_COUNT.translationKey
    val RULE_COMPLEXITY_RULE_LENGTH_KEY: String = RuleMessageKey.RULE_COMPLEXITY_RULE_LENGTH.translationKey
    val RULE_COMPLEXITY_TOTAL_LENGTH_KEY: String = RuleMessageKey.RULE_COMPLEXITY_TOTAL_LENGTH.translationKey
    val RULE_LIMIT_CLAMP_KEY: String = RuleMessageKey.RULE_LIMIT_CLAMP.translationKey
    val RULE_RELOAD_ERROR_PREFIX_KEY: String = RuleMessageKey.RULE_RELOAD_ERROR_PREFIX.translationKey
    val TOOLTIP_CURRENT_MAX_KEY: String = RuleMessageKey.TOOLTIP_CURRENT_MAX.translationKey
}

object Constants {
    const val COUNT_MAGIC: Int = -42
    const val VANILLA_STACK_LIMIT: Int = 64
}
