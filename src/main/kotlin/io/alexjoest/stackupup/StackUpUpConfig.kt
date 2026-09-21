package io.alexjoest.stackupup

import io.alexjoest.stackupup.compat.nuclearcraft.NuclearCraftCompat
import net.minecraftforge.common.config.Config

@Config(modid = StackUpUp.CONFIG_ID, name = StackUpUp.PUBLIC_ID, category = "")
@Config.LangKey(StackUpUpIds.CONFIG_LANG_ROOT)
object StackUpUpConfig {
    @JvmField
    @Config.Ignore
    var coremodActive: Boolean = false

    @JvmField
    @Config.Ignore
    var activeMaxStackSize: Int = 64

    val craftingSlotLimit: Int
        get() = compat.vanilla.craftingSlotLimit.takeIf { it > 0 } ?: Constants.VANILLA_STACK_LIMIT

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

    @JvmStatic
    fun applyReloadControlledValues() {
        activeMaxStackSize = general.maxStackSize
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
        @Config.Name("vanilla")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compat.vanilla.name")
        val vanilla: Vanilla = Vanilla()

        @JvmField
        @Config.Name("nuclearcraft")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compat.nuclearcraft.name")
        val nuclearcraft: NuclearCraft = NuclearCraft()
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
}

enum class TooltipStackDisplayMode {
    OFF,
    ALWAYS,
    ADVANCED,
}
