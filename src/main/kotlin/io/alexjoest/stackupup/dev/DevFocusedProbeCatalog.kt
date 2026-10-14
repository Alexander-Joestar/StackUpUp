package io.alexjoest.stackupup.dev

internal const val EQUIPMENT_OFF_FOCUS = "equipment_inventory_off"
internal const val EQUIPMENT_OFF_BLACKLIST_FOCUS = "equipment_inventory_off_blacklist"
internal const val EQUIPMENT_ON_FOCUS = "equipment_inventory_on"
internal const val EQUIPMENT_BLACKLIST_FOCUS = "equipment_inventory_blacklist"

internal const val AE2_OWNER_OFF_FOCUS = "ae2_owner_off"
internal const val AE2_OWNER_BLACKLIST_FOCUS = "ae2_owner_blacklist"

private val equipmentOffProbe = DevEquipmentInventoryProbe(EQUIPMENT_OFF_FOCUS, false)
private val equipmentOffBlacklistProbe = DevEquipmentInventoryProbe(EQUIPMENT_OFF_BLACKLIST_FOCUS, false, true)
private val equipmentOnProbe = DevEquipmentInventoryProbe(EQUIPMENT_ON_FOCUS, true, false)
private val equipmentBlacklistProbe = DevEquipmentInventoryProbe(EQUIPMENT_BLACKLIST_FOCUS, true)
private val ae2OwnerOffProbe = DevAe2ProbeAdapter(AE2_OWNER_OFF_FOCUS, false)
private val ae2OwnerBlacklistProbe = DevAe2ProbeAdapter(AE2_OWNER_BLACKLIST_FOCUS, true)

internal val focusedProbeIds: Set<String> = setOf(
    CORE_INVENTORY_FOCUS,
    EQUIPMENT_OFF_FOCUS,
    EQUIPMENT_OFF_BLACKLIST_FOCUS,
    EQUIPMENT_ON_FOCUS,
    EQUIPMENT_BLACKLIST_FOCUS,
    AE2_OWNER_OFF_FOCUS,
    AE2_OWNER_BLACKLIST_FOCUS,
)

internal fun focusedProbe(focus: String): DevCompatProbe = when (focus) {
    CORE_INVENTORY_FOCUS -> DevCoreInventoryProbe
    EQUIPMENT_OFF_FOCUS -> equipmentOffProbe
    EQUIPMENT_OFF_BLACKLIST_FOCUS -> equipmentOffBlacklistProbe
    EQUIPMENT_ON_FOCUS -> equipmentOnProbe
    EQUIPMENT_BLACKLIST_FOCUS -> equipmentBlacklistProbe
    AE2_OWNER_OFF_FOCUS -> ae2OwnerOffProbe
    AE2_OWNER_BLACKLIST_FOCUS -> ae2OwnerBlacklistProbe
    else -> error("unknown_focus: $focus")
}
