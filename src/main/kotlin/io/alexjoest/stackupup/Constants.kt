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

    const val DEV_AUTOMATION_PREFIX: String = "$MOD_ID.dev.autoTest"
    const val DEV_AUTOMATION_LEGACY_PREFIX: String = "stackup.dev.autoTest"
    const val DEV_AUTOMATION_WORLD_FOLDER: String = "${MOD_ID}_dev_autotest"

    const val PACKAGE_NAME: String = "io.alexjoest.stackupup"
    const val CORE_PACKAGE_NAME: String = "$PACKAGE_NAME.core"
    const val CONFIG_CLASS_NAME: String = "$PACKAGE_NAME.StackUpUpConfig"
    const val PROXY_CLIENT_CLASS_NAME: String = "$PACKAGE_NAME.ProxyClient"
    const val PROXY_COMMON_CLASS_NAME: String = "$PACKAGE_NAME.ProxyCommon"
    const val CONFIG_GUI_FACTORY_CLASS_NAME: String = "$PACKAGE_NAME.config.ConfigGuiFactory"
    const val STACK_LIMIT_HOOKS_INTERNAL_NAME: String = "io/alexjoest/stackupup/StackLimitHooks"

    const val EARLY_MIXIN_CONFIG: String = "mixins.$MOD_ID.early.json"
    const val LATE_AE2_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.ae2.json"
    const val LATE_AE2_SUPERGIANT_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.ae2supergiant.json"
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
    const val LATE_NUCLEARCRAFT_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.nuclearcraft.json"
    const val LATE_COLOSSALCHESTS_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.colossalchests.json"
    const val LATE_GREGTECH_MIXIN_CONFIG: String = "mixins.$MOD_ID.late.gregtech.json"
}

object Constants {
    const val COUNT_MAGIC: Int = -42
    const val VANILLA_STACK_LIMIT: Int = 64
}
