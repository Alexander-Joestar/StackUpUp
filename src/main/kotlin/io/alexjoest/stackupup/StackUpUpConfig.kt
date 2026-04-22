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
    @Config.Name("general")
    @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.general.name")
    val general: General = General()

    @JvmField
    @Config.Name("modpatches")
    @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.modpatches.name")
    val modPatches: ModPatches = ModPatches()

    @JvmField
    @Config.Name("client")
    @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.client.name")
    val client: Client = Client()

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

    var coremodPatchRefinedStorage: Boolean
        get() = modPatches.refinedStorage
        set(value) {
            modPatches.refinedStorage = value
        }

    var coremodPatchMantle: Boolean
        get() = modPatches.mantle
        set(value) {
            modPatches.mantle = value
        }

    var coremodPatchIc2: Boolean
        get() = modPatches.industrialCraft2
        set(value) {
            modPatches.industrialCraft2 = value
        }

    var coremodPatchAppliedEnergistics2: Boolean
        get() = modPatches.appliedEnergistics2
        set(value) {
            modPatches.appliedEnergistics2 = value
        }

    var coremodPatchActuallyAdditions: Boolean
        get() = modPatches.actuallyAdditions
        set(value) {
            modPatches.actuallyAdditions = value
        }

    var coremodPatchCyclopsCore: Boolean
        get() = modPatches.cyclopsCore
        set(value) {
            modPatches.cyclopsCore = value
        }

    @JvmStatic
    fun applyRuntimeValues() {
        equalScaleDown = abs(client.fontScaleMinimum - client.fontScaleMaximum) <= 0.001
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
    }

    class ModPatches {
        @JvmField
        @Config.Comment("Patch Refined Storage inventories for large-stack compatibility.")
        @Config.Name("refinedstorage")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.modpatches.refinedstorage.name")
        @Config.RequiresMcRestart
        var refinedStorage: Boolean = true

        @JvmField
        @Config.Comment("Patch Mantle and Tinkers Construct inventories for large-stack compatibility.")
        @Config.Name("mantle")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.modpatches.mantle.name")
        @Config.RequiresMcRestart
        var mantle: Boolean = true

        @JvmField
        @Config.Comment("Patch IndustrialCraft 2 inventories for large-stack compatibility.")
        @Config.Name("industrialcraft2")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.modpatches.industrialcraft2.name")
        @Config.RequiresMcRestart
        var industrialCraft2: Boolean = true

        @JvmField
        @Config.Comment("Patch Applied Energistics 2 inventories for large-stack compatibility.")
        @Config.Name("appliedenergistics2")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.modpatches.appliedenergistics2.name")
        @Config.RequiresMcRestart
        var appliedEnergistics2: Boolean = true

        @JvmField
        @Config.Comment("Patch Actually Additions inventories for large-stack compatibility.")
        @Config.Name("actuallyadditions")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.modpatches.actuallyadditions.name")
        @Config.RequiresMcRestart
        var actuallyAdditions: Boolean = true

        @JvmField
        @Config.Comment("Patch CyclopsCore inventories for large-stack compatibility.")
        @Config.Name("cyclopscore")
        @Config.LangKey("${StackUpUpIds.CONFIG_LANG_ROOT}.modpatches.cyclopscore.name")
        @Config.RequiresMcRestart
        var cyclopsCore: Boolean = true

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
}

enum class TooltipStackDisplayMode {
    OFF,
    ALWAYS,
    ADVANCED
}


