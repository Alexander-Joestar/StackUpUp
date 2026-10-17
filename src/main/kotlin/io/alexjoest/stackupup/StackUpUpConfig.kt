package io.alexjoest.stackupup

import io.alexjoest.stackupup.compat.nuclearcraft.NuclearCraftCompat
import net.minecraftforge.common.config.Config

@Config(modid = StackUpUp.CONFIG_ID, name = StackUpUp.PUBLIC_ID, category = "")
@Config.LangKey(StackUpUpIds.CONFIG_LANG_ROOT)
object StackUpUpConfig {
    @JvmField
    @Config.Ignore
    var coremodActive: Boolean = false

    var maxStackSize: Int
        get() = general.maxStackSize
        set(value) {
            general.maxStackSize = value
        }

    @JvmField
    @Config.Name("general")
    @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.general.name")
    val general: General = General()

    @JvmField
    @Config.Name("compat")
    @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compat.name")
    val compat: Compat = Compat()

    @JvmField
    @Config.Name("client")
    @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.client.name")
    val client: Client = Client()

    @JvmField
    @Config.Name("debug")
    @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.debug.name")
    val debug: Debug = Debug()

    @JvmStatic
    fun applyReloadControlledValues() {
        NuclearCraftCompat.applyConfiguredUpgradeStackLimits()
    }

    class General {
        @JvmField
        @Config.Comment("Enable the DSL v2 rules file.")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.general.enableDslRules.name")
        @Config.RequiresMcRestart
        var enableDslRules: Boolean = true

        @JvmField
        @Config.Comment("Warn when the ruleset becomes unusually large or long.")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.general.ruleComplexityWarnings.name")
        var ruleComplexityWarnings: Boolean = true

        @JvmField
        @Config.Comment("Global compatibility upper bound used when a code path has no item stack context.")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.general.maxStackSize.name")
        @Config.RangeInt(min = 1, max = Int.MAX_VALUE)
        var maxStackSize: Int = 64
    }

    class Compat {
        @JvmField
        @Config.Name("inventoryExpansion")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compat.inventoryExpansion.name")
        val inventoryExpansion: InventoryExpansion = InventoryExpansion()

        @JvmField
        @Config.Name("vanilla")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compat.vanilla.name")
        val vanilla: Vanilla = Vanilla()

        @JvmField
        @Config.Name("nuclearcraft")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compat.nuclearcraft.name")
        val nuclearcraft: NuclearCraft = NuclearCraft()

        @JvmField
        @Config.Name("inventoryStackLimitBlacklist")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compat.inventoryStackLimitBlacklist.name")
        val inventoryStackLimitBlacklist: InventoryStackLimitBlacklist = InventoryStackLimitBlacklist()
    }

    class InventoryExpansion {
        @JvmField
        @Config.Comment(
            "Enable broad third-party inventory and optional capacity compatibility expansion by default. Set false only as a restart-time emergency fallback; vanilla player/container stack-rule support remains enabled. Changes require a game restart.",
        )
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compat.inventoryExpansion.enabled.name")
        @Config.RequiresMcRestart
        var enabled: Boolean = true
    }

    class InventoryStackLimitBlacklist {
        @JvmField
        @Config.Comment(
            "Forge mod IDs whose actual owned-package receivers or supported public owners bypass capacity expansion. Empty is a no-op; unlisted mods remain eligible. Superclass/interface ownership alone does not exclude addons. Shared creator, private delegate and receiverless constructor ownership remain UNKNOWN. Changes require a restart.",
        )
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compat.inventoryStackLimitBlacklist.modIds.name")
        @Config.RequiresMcRestart
        var modIds: Array<String> = emptyArray()
    }

    class Vanilla {
        @JvmField
        @Config.Comment(
            "Crafting container slot limit. Mainly a Performance setting rather than a balance setting: holding Shift to batch-craft very large stacks can cause severe lag. Values above 0 set a custom limit; 0 keeps vanilla behavior.",
        )
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compat.vanilla.craftingSlotLimit.name")
        @Config.RangeInt(min = 0)
        var craftingSlotLimit: Int = 64
    }

    class NuclearCraft {
        @JvmField
        @Config.Comment(
            "Speed upgrade stack limit. Values above 0 set a custom limit; 0 keeps NuclearCraft's native behavior.",
        )
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compat.nuclearcraft.speedUpgradeLimit.name")
        @Config.RangeInt(min = 0)
        var speedUpgradeLimit: Int = 0

        @JvmField
        @Config.Comment(
            "Energy upgrade stack limit. Values above 0 set a custom limit; 0 keeps NuclearCraft's native behavior.",
        )
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compat.nuclearcraft.energyUpgradeLimit.name")
        @Config.RangeInt(min = 0)
        var energyUpgradeLimit: Int = 0
    }

    class Client {
        @JvmField
        @Config.Comment("Tooltip stack display mode.")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.client.tooltipStackDisplayMode.name")
        var tooltipStackDisplayMode: TooltipStackDisplayMode = TooltipStackDisplayMode.ADVANCED

        @JvmField
        @Config.Comment("Minimum scale used when stack counts are squeezed into slot overlays.")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.client.fontScaleMinimum.name")
        @Config.RangeDouble(min = 0.0, max = 1.0)
        var fontScaleMinimum: Double = 0.6

        @JvmField
        @Config.Comment("Maximum scale used when stack counts are squeezed into slot overlays.")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.client.fontScaleMaximum.name")
        @Config.RangeDouble(min = 0.0, max = 1.0)
        var fontScaleMaximum: Double = 0.6

        @JvmField
        @Config.Comment("Use smooth scaling for slot count text instead of stepped scaling.")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.client.fontScaleLinear.name")
        var fontScaleLinear: Boolean = false

        @JvmField
        @Config.Comment("Display stack counts using capped compact text (e.g. 1.5K, 0.1M, 2.1B) before fitting and any font scaling.")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.client.alwaysCompactNumbers.name")
        var alwaysCompactNumbers: Boolean = false
    }

    class Debug {
        @JvmField
        @Config.Comment(
            "Diagnostic only: log every container merge slot limit resolution with both sides' inputs and the resolved value. Leave off in normal play; it is only meant to trace a single reproduction.",
        )
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.debug.mergeLimitTrace.name")
        var mergeLimitTrace: Boolean = false
    }
}

enum class TooltipStackDisplayMode {
    OFF,
    ALWAYS,
    ADVANCED,
}
