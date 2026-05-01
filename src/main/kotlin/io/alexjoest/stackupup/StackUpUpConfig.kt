package io.alexjoest.stackupup

import net.minecraftforge.common.config.Config
import kotlin.math.abs

@Config(modid = StackUpUp.CONFIG_ID, name = StackUpUp.PUBLIC_ID, category = "")
@Config.LangKey(StackUpUpIds.CONFIG_LANG_ROOT)
object StackUpUpConfig {
    @JvmField
    @Config.Ignore
    var coremodActive: Boolean = false

    @JvmField
    @Config.Ignore
    var equalScaleDown: Boolean = false

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

    @JvmField
    @Config.Name("compatibility")
    @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compatibility.name")
    val compatibility: Compatibility = Compatibility()

    var enableDslRules: Boolean
        get() = general.enableDslRules
        set(value) {
            general.enableDslRules = value
        }

    var tooltipStackDisplayMode: TooltipStackDisplayMode
        get() = general.tooltipStackDisplayMode
        set(value) {
            general.tooltipStackDisplayMode = value
        }

    var ruleComplexityWarnings: Boolean
        get() = general.ruleComplexityWarnings
        set(value) {
            general.ruleComplexityWarnings = value
        }

    var maxStackSize: Int
        get() = activeMaxStackSize
        set(value) {
            general.maxStackSize = value
            activeMaxStackSize = value
        }

    var scaleTextLinearly: Boolean
        get() = client.fontScaleLinear
        set(value) {
            client.fontScaleLinear = value
        }

    var lowestScaleDown: Float
        get() = client.fontScaleMinimum.toFloat()
        set(value) {
            client.fontScaleMinimum = value.toDouble()
        }

    var highestScaleDown: Float
        get() = client.fontScaleMaximum.toFloat()
        set(value) {
            client.fontScaleMaximum = value.toDouble()
        }

    @JvmStatic
    fun applyRuntimeValues() {
        equalScaleDown = abs(client.fontScaleMinimum - client.fontScaleMaximum) <= 0.001
    }

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
        @Config.Comment("Tooltip stack display mode.")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.general.tooltipStackDisplayMode.name")
        var tooltipStackDisplayMode: TooltipStackDisplayMode = TooltipStackDisplayMode.ADVANCED

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
    }

    class Compatibility {
        @JvmField
        @Config.Comment("Enable late-mixin compatibility patch for Applied Energistics 2.")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compatibility.ae2.name")
        @Config.RequiresMcRestart
        var ae2: Boolean = true

        @JvmField
        @Config.Comment("Enable late-mixin compatibility patch for Brandon's Core.")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compatibility.brandonscore.name")
        @Config.RequiresMcRestart
        var brandonsCore: Boolean = true

        @JvmField
        @Config.Comment("Enable late-mixin compatibility patch for Actually Additions.")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compatibility.actuallyadditions.name")
        @Config.RequiresMcRestart
        var actuallyAdditions: Boolean = true

        @JvmField
        @Config.Comment("Enable late-mixin compatibility patch for Cyclops Core.")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compatibility.cyclopscore.name")
        @Config.RequiresMcRestart
        var cyclopsCore: Boolean = true

        @JvmField
        @Config.Comment("Enable late-mixin compatibility patch for Ender IO.")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compatibility.enderio.name")
        @Config.RequiresMcRestart
        var enderIo: Boolean = true

        @JvmField
        @Config.Comment("Enable late-mixin compatibility patch for IndustrialCraft 2.")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compatibility.ic2.name")
        @Config.RequiresMcRestart
        var ic2: Boolean = true

        @JvmField
        @Config.Comment("Enable late-mixin compatibility patch for Mantle (Tinkers' Construct).")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compatibility.mantle.name")
        @Config.RequiresMcRestart
        var mantle: Boolean = true

        @JvmField
        @Config.Comment("Enable late-mixin compatibility patch for Refined Storage.")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.compatibility.refinedstorage.name")
        @Config.RequiresMcRestart
        var refinedStorage: Boolean = true
    }
}

enum class TooltipStackDisplayMode {
    OFF,
    ALWAYS,
    ADVANCED,
}
