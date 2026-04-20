package io.alexjoest.stackupup.dev

import io.alexjoest.stackupup.StackUpUpIds

object DevAutomationConfig {
    private val settings: DevAutomationSettings = readSettings(System::getProperty)

    val enabled: Boolean = settings.enabled
    val mode: String = settings.mode
    val clientEnabled: Boolean = enabled && (mode == "client" || mode == "both")
    val serverEnabled: Boolean = enabled && (mode == "server" || mode == "both")
    val runServerMatrix: Boolean = settings.runServerMatrix
    val autoShutdown: Boolean = settings.autoShutdown
    val failFast: Boolean = settings.failFast
    val clearInventoryBeforeGive: Boolean = settings.clearInventoryBeforeGive
    val worldFolder: String = settings.worldFolder
    val worldName: String = settings.worldName
    val itemId: String = settings.itemId
    val itemMeta: Int = settings.itemMeta
    val oreName: String = settings.oreName
    val tempRule: String = settings.tempRule
    val itemCount: Int = settings.itemCount
    val compatProbeIds: Set<String> = settings.compatProbeIds

    val builtInMatrix: List<DevProbeTargetSpec> =
        listOf(
            DevProbeTargetSpec(name = "IngotSteel", oreName = "ingotSteel"),
            DevProbeTargetSpec(name = "PlateSteel", oreName = "plateSteel"),
            DevProbeTargetSpec(name = "DustSteel", oreName = "dustSteel"),
            DevProbeTargetSpec(name = "VacuumTube", itemId = "gregtech:meta_item_1", metadata = 516)
        )
}

data class DevProbeTargetSpec(
    val name: String,
    val oreName: String? = null,
    val itemId: String? = null,
    val metadata: Int? = null
)

internal data class DevAutomationSettings(
    val enabled: Boolean,
    val mode: String,
    val runServerMatrix: Boolean,
    val autoShutdown: Boolean,
    val failFast: Boolean,
    val clearInventoryBeforeGive: Boolean,
    val worldFolder: String,
    val worldName: String,
    val itemId: String,
    val itemMeta: Int,
    val oreName: String,
    val tempRule: String,
    val itemCount: Int,
    val compatProbeIds: Set<String>
)

internal fun readSettings(getProperty: (String) -> String?): DevAutomationSettings {
    fun readSetting(key: String, defaultValue: String): String {
        return getProperty("${StackUpUpIds.DEV_AUTOMATION_PREFIX}.$key")
            ?: getProperty("${StackUpUpIds.DEV_AUTOMATION_LEGACY_PREFIX}.$key")
            ?: defaultValue
    }

    fun readEnabled(defaultValue: String): String {
        return getProperty("${StackUpUpIds.DEV_AUTOMATION_PREFIX}.enabled")
            ?: getProperty(StackUpUpIds.DEV_AUTOMATION_PREFIX)
            ?: getProperty(StackUpUpIds.DEV_AUTOMATION_LEGACY_PREFIX)
            ?: defaultValue
    }

    return DevAutomationSettings(
        enabled = readEnabled("false").toBoolean(),
        mode = readSetting("mode", "client"),
        runServerMatrix = readSetting("matrix", "false").toBoolean(),
        autoShutdown = readSetting("autoShutdown", "true").toBoolean(),
        failFast = readSetting("failFast", "true").toBoolean(),
        clearInventoryBeforeGive = readSetting("clearInventoryBeforeGive", "true").toBoolean(),
        worldFolder = readSetting("worldFolder", StackUpUpIds.DEV_AUTOMATION_WORLD_FOLDER),
        worldName = readSetting("worldName", "StackUpUp 自动测试"),
        itemId = readSetting("item", ""),
        itemMeta = readSetting("meta", "11305").toIntOrNull() ?: 11305,
        oreName = readSetting("ore", "ingotSteel"),
        tempRule = readSetting("rule", "ore = ingotSteel -> 1024"),
        itemCount = readSetting("count", "128").toIntOrNull() ?: 128,
        compatProbeIds = parseRequestedProbeIds(readSetting("compat", ""))
    )
}

internal fun parseRequestedProbeIds(raw: String): Set<String> {
    return raw.split(',')
        .map(String::trim)
        .map(String::lowercase)
        .filter(String::isNotEmpty)
        .toCollection(LinkedHashSet())
}

internal fun selectRequestedProbeIds(requestedIds: Set<String>, availableIds: List<String>): List<String> {
    if (requestedIds.isEmpty()) {
        return availableIds
    }
    return availableIds.filter { it in requestedIds }
}
