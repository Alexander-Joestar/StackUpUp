package io.alexjoest.stackupup.dev

internal const val EQUIPMENT_OFF_FOCUS = "equipment_inventory_off"
internal const val EQUIPMENT_BLACKLIST_FOCUS = "equipment_inventory_blacklist"

private val equipmentOffProbe = DevEquipmentInventoryProbe(EQUIPMENT_OFF_FOCUS, false)
private val equipmentBlacklistProbe = DevEquipmentInventoryProbe(EQUIPMENT_BLACKLIST_FOCUS, true)

internal val focusedProbeIds: Set<String> = setOf(CORE_INVENTORY_FOCUS, EQUIPMENT_OFF_FOCUS, EQUIPMENT_BLACKLIST_FOCUS)

internal fun focusedProbe(focus: String): DevCompatProbe = when (focus) {
    CORE_INVENTORY_FOCUS -> DevCoreInventoryProbe
    EQUIPMENT_OFF_FOCUS -> equipmentOffProbe
    EQUIPMENT_BLACKLIST_FOCUS -> equipmentBlacklistProbe
    else -> error("unknown_focus: $focus")
}
