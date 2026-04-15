package pl.asie.stackup

object StackUpConfig {
    @JvmField
    var coremodActive: Boolean = false

    @JvmField
    var coremodPatchRefinedStorage: Boolean = true

    @JvmField
    var coremodPatchMantle: Boolean = true

    @JvmField
    var coremodPatchIc2: Boolean = true

    @JvmField
    var coremodPatchAppliedEnergistics2: Boolean = true

    @JvmField
    var coremodPatchActuallyAdditions: Boolean = true

    @JvmField
    var scriptingActive: Boolean = false

    @JvmField
    var scaleTextLinearly: Boolean = false

    @JvmField
    var lowestScaleDown: Float = 0.0f

    @JvmField
    var highestScaleDown: Float = 1.0f

    @JvmField
    var equalScaleDown: Boolean = false

    @JvmField
    var compatChiselsBits: Boolean = true
}
