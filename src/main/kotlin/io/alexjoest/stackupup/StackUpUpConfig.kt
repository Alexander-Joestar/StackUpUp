package io.alexjoest.stackupup

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

    @JvmField
    @Config.Name("general")
    @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.general.name")
    val general: General = General()

    @JvmField
    @Config.Name("client")
    @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.client.name")
    val client: Client = Client()

    @JvmStatic
    fun applyReloadControlledValues() {
        activeMaxStackSize = general.maxStackSize
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
        @Config.Comment("Display stack counts using a short capped format (e.g. 1.5k, 0.1m, 2.1b) instead of scaling.")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.client.alwaysCompactNumbers.name")
        var alwaysCompactNumbers: Boolean = false
    }
}

enum class TooltipStackDisplayMode {
    OFF,
    ALWAYS,
    ADVANCED,
}
