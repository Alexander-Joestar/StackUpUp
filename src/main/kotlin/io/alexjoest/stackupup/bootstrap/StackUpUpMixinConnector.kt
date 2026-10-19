package io.alexjoest.stackupup.bootstrap

import io.alexjoest.stackupup.StackUpUpCore
import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.core.InventoryExpansionStartupConfig
import io.alexjoest.stackupup.core.InventoryStackLimitHook
import net.minecraft.launchwrapper.Launch
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.spongepowered.asm.mixin.Mixins
import org.spongepowered.asm.mixin.connect.IMixinConnector
import zone.rong.mixinbooter.service.ModDiscoverer
import java.io.File
import java.util.jar.JarFile

/**
 * MixinBooter 11.17 的 `IMixinConnector` 装载入口。
 *
 * `connect()` 通过 `Mixins.addConfiguration` 按当前冲突状态、模块表和 mod 在场探针注册
 * early/late 配置。容量配置额外受扩容开关、冲突状态和启动黑名单约束；Supergiant `ae2` 的
 * fallback 只检查已知 marker resource，不能证明一般的 mod 在场或归属。
 *
 * 早期配置按顺序入队；某个入队操作失败时，之前成功入队的配置不会回滚，late 配置仍会继续处理。
 */
class StackUpUpMixinConnector : IMixinConnector {
    private val logger: Logger = LogManager.getLogger("stackupup.mixin.connector")

    override fun connect() {
        val policy = InventoryExpansionStartupConfig.load()
        InventoryStackLimitHook.configureExpansionEnabled(policy.enabled)
        if (policy.enabled && policy.modIds.isNotEmpty()) {
            logger.info(
                "Inventory capacity expansion enabled; blacklist modIds bypass matching owned-package inventories only: {}",
                policy.modIds.joinToString(", "),
            )
        }
        val conflicts = StackUpUpCore.ensureConflictState()
        connectEarly(conflicts)
        connectLateWithClassicModules(
            conflicts = conflicts,
            blacklistedModIds = policy.modIds.toSet(),
        )
    }

    internal fun connectEarly(conflicts: List<String>, queueConfiguration: (String) -> Unit = { config -> Mixins.addConfiguration(config) }) {
        if (!shouldQueueEarly(conflicts)) {
            return
        }
        try {
            queueConfiguration(StackUpUpIds.EARLY_MIXIN_CONFIG)
            queueConfiguration("mixins.stackupup.baseline.early.json")
            if (InventoryStackLimitHook.isExpansionEnabled()) {
                queueConfiguration("mixins.stackupup.capacity.early.json")
            }
        } catch (e: Exception) {
            logger.error(
                "Early mixin configuration enqueue failed; previously queued early configs remain registered and late mixins continue",
                e,
            )
        }
    }

    internal fun connectLate(
        conflicts: List<String>,
        isModPresent: (String) -> Boolean = { modId -> isModPresentForConnector(modId) },
        blacklistedModIds: Set<String> = emptySet(),
        queueConfiguration: (String) -> Unit = { config -> Mixins.addConfiguration(config) },
    ) {
        connectLateInternal(
            conflicts = conflicts,
            isModPresent = isModPresent,
            blacklistedModIds = blacklistedModIds,
            includeClassic = false,
            queueConfiguration = queueConfiguration,
        )
    }

    internal fun connectLateWithClassicModules(
        conflicts: List<String>,
        isModPresent: (String) -> Boolean = { modId -> isModPresentForConnector(modId) },
        blacklistedModIds: Set<String> = emptySet(),
        queueConfiguration: (String) -> Unit = { config -> Mixins.addConfiguration(config) },
    ) {
        connectLateInternal(
            conflicts = conflicts,
            isModPresent = isModPresent,
            blacklistedModIds = blacklistedModIds,
            includeClassic = true,
            queueConfiguration = queueConfiguration,
        )
    }

    private fun connectLateInternal(
        conflicts: List<String>,
        isModPresent: (String) -> Boolean,
        blacklistedModIds: Set<String>,
        includeClassic: Boolean,
        queueConfiguration: (String) -> Unit,
    ) {
        val normalModules = if (includeClassic) modules + classicModules else modules
        for ((config) in normalModules) {
            if (!shouldQueue(config, isModPresent, normalModules)) {
                continue
            }
            queueConfiguration(config)
        }
        if (!shouldQueueCapacity(conflicts)) {
            if (conflicts.isNotEmpty()) {
                logger.error(
                    "Capacity late mixin configs are NOT queued because conflicting stacking mods were detected [{}]",
                    conflicts.joinToString(", "),
                )
            }
            return
        }
        val capacityModules = if (includeClassic) this.capacityModules + classicCapacityModules else this.capacityModules
        for ((config, modId) in capacityModules) {
            if (modId in blacklistedModIds) {
                logger.info("Skipping capacity mixin config '{}': mod '{}' is blacklisted", config, modId)
                continue
            }
            if (!shouldQueue(config, isModPresent, capacityModules)) {
                continue
            }
            queueConfiguration(config)
        }
    }

    internal fun isModPresentForConnector(
        modId: String,
        forgeProbe: () -> Boolean? = { safeForgeModPresence(modId) },
        cleanroomProbe: () -> Boolean = { cleanroomAe2Presence() },
    ): Boolean {
        if (modId != AE2_MOD_ID) {
            return ModDiscoverer.isModPresent(modId)
        }
        val forgePresence = try {
            forgeProbe()
        } catch (e: Throwable) {
            logger.warn(
                "ModDiscoverer presence probe for '{}' failed during connector initialization; trying Cleanroom marker probe",
                modId,
                e,
            )
            null
        }
        return when (forgePresence) {
            true -> true
            false, null -> try {
                cleanroomProbe()
            } catch (e: Throwable) {
                logger.warn(
                    "Cleanroom marker probe for '{}' failed; treating mod as absent",
                    modId,
                    e,
                )
                false
            }
        }
    }

    private fun safeForgeModPresence(modId: String): Boolean? = try {
        if (ModDiscoverer.isModPresent(modId)) true else null
    } catch (e: Throwable) {
        logger.warn(
            "ModDiscoverer presence probe for '{}' is unavailable during connector initialization; " +
                "trying Cleanroom marker probe",
            modId,
            e,
        )
        null
    }

    private fun cleanroomAe2Presence(): Boolean {
        val classLoaders = listOfNotNull(
            javaClass.classLoader,
            Thread.currentThread().contextClassLoader,
        ).distinct()
        if (AE2_SUPERGIANT_MARKERS.any { marker ->
                classLoaders.any { classLoader ->
                    try {
                        classLoader.getResource(marker) != null
                    } catch (e: Throwable) {
                        logger.warn("Cleanroom marker resource lookup failed for '{}'", marker, e)
                        false
                    }
                }
            }
        ) {
            return true
        }
        val minecraftHome: File = try {
            Launch.minecraftHome
        } catch (e: Throwable) {
            logger.warn("Unable to read Launch.minecraftHome for Cleanroom marker probe", e)
            return false
        }
        return listOf(
            File(minecraftHome, "mods"),
            File(File(minecraftHome, "mods"), "1.12.2"),
        ).distinct().any(::cleanroomJarDirectoryPresence)
    }

    private fun cleanroomJarDirectoryPresence(directory: File): Boolean {
        if (!directory.isDirectory) {
            return false
        }
        val candidates = try {
            directory.listFiles()
                ?.filter { file ->
                    file.isFile && (file.name.endsWith(".jar", ignoreCase = true) || file.name.endsWith(".zip", ignoreCase = true))
                }
                .orEmpty()
        } catch (e: Throwable) {
            logger.warn("Unable to list candidate mods in '{}' for Cleanroom marker probe", directory, e)
            return false
        }
        return candidates.any(::jarContainsAe2SupergiantMarker)
    }

    private fun jarContainsAe2SupergiantMarker(file: File): Boolean = try {
        JarFile(file).use { jar ->
            AE2_SUPERGIANT_MARKERS.any { marker -> jar.getEntry(marker) != null }
        }
    } catch (e: Throwable) {
        logger.debug("Unable to inspect '{}' for Cleanroom marker", file, e)
        false
    }

    /**
     * early 配置入队决策：冲突状态非空时拒绝所有 early 配置并记录原因；无冲突时允许入队。
     */
    internal fun shouldQueueEarly(conflicts: List<String>): Boolean {
        if (conflicts.isEmpty()) {
            return true
        }
        logger.error(
            "Early mixin config '{}' is NOT queued: conflicting stacking mods detected [{}]; " +
                "all early mixins disabled by conflict-disable design",
            StackUpUpIds.EARLY_MIXIN_CONFIG,
            conflicts.joinToString(", "),
        )
        return false
    }

    internal fun shouldQueueCapacity(conflicts: List<String>): Boolean = InventoryStackLimitHook.isExpansionEnabled() && conflicts.isEmpty()

    /**
     * 单配置装载决策：只允许模块表中登记的配置，并通过注入式谓词判断所需 mod 是否在场。
     */
    internal fun shouldQueue(config: String, isModPresent: (String) -> Boolean): Boolean = shouldQueue(config, isModPresent, modules)

    internal fun shouldQueue(config: String, isModPresent: (String) -> Boolean, registry: List<LateMixinModule>): Boolean {
        val module = registry.firstOrNull { it.config == config }
        if (module == null) {
            // T14.5 停止条件 3：不在模块表的配置不再无条件入队（原 ?: return true 会绕过模块检查静默全量装载）。
            logger.error(
                "Refusing to queue unknown late mixin config '{}': not registered in the module table",
                config,
            )
            return false
        }
        if (!isModPresent(module.modId)) {
            // T14.5 停止条件 2：optional 缺失必须留日志（原零日志跳过），required:false 配置同样覆盖。
            logger.info("Skipping late mixin config '{}': required mod '{}' is not present", module.config, module.modId)
            return false
        }
        return true
    }

    internal data class LateMixinModule(val config: String, val modId: String)

    internal val modules: List<LateMixinModule> = listOf(
        LateMixinModule(StackUpUpIds.LATE_AE2_MIXIN_CONFIG, "appliedenergistics2"),
        LateMixinModule(StackUpUpIds.LATE_AE2_SUPERGIANT_MIXIN_CONFIG, "ae2"),
        LateMixinModule(StackUpUpIds.LATE_BRANDONSCORE_MIXIN_CONFIG, "brandonscore"),
        LateMixinModule(StackUpUpIds.LATE_ACTUALLY_ADDITIONS_MIXIN_CONFIG, "actuallyadditions"),
        LateMixinModule(StackUpUpIds.LATE_CYCLOPSCORE_MIXIN_CONFIG, "cyclopscore"),
        LateMixinModule(StackUpUpIds.LATE_ENDERIO_MIXIN_CONFIG, "enderio"),
        LateMixinModule(StackUpUpIds.LATE_IC2_MIXIN_CONFIG, StackUpUpIds.IC2_MOD_ID),
        LateMixinModule(StackUpUpIds.LATE_MANTLE_MIXIN_CONFIG, "mantle"),
        LateMixinModule(StackUpUpIds.LATE_REFINED_STORAGE_MIXIN_CONFIG, "refinedstorage"),
        LateMixinModule(StackUpUpIds.LATE_STORAGE_NETWORK_MIXIN_CONFIG, "storagenetwork"),
        LateMixinModule(StackUpUpIds.LATE_INTEGRATEDDYNAMICS_MIXIN_CONFIG, "integrateddynamics"),
        LateMixinModule(StackUpUpIds.LATE_LIMELIB_MIXIN_CONFIG, "limelib"),
        LateMixinModule(StackUpUpIds.LATE_IMMERSIVEENGINEERING_MIXIN_CONFIG, "immersiveengineering"),
        LateMixinModule(StackUpUpIds.LATE_NUCLEARCRAFT_MIXIN_CONFIG, "nuclearcraft"),
        LateMixinModule(StackUpUpIds.LATE_COLOSSALCHESTS_MIXIN_CONFIG, "colossalchests"),
        LateMixinModule(StackUpUpIds.LATE_GREGTECH_MIXIN_CONFIG, "gregtech"),
        LateMixinModule(StackUpUpIds.LATE_TECHREBORN_MIXIN_CONFIG, "techreborn"),
        LateMixinModule(StackUpUpIds.LATE_FARMERS_DELIGHT_MIXIN_CONFIG, "farmersdelight"),
    )

    internal val classicModules: List<LateMixinModule> = listOf(
        LateMixinModule(StackUpUpIds.LATE_IC2_CLASSIC_MIXIN_CONFIG, StackUpUpIds.IC2_MOD_ID),
    )

    internal val capacityModules: List<LateMixinModule> = listOf(
        LateMixinModule("mixins.stackupup.capacity.late.ae2.json", "appliedenergistics2"),
        LateMixinModule("mixins.stackupup.capacity.late.ae2supergiant.json", "ae2"),
        LateMixinModule("mixins.stackupup.capacity.late.brandonscore.json", "brandonscore"),
        LateMixinModule("mixins.stackupup.capacity.late.actuallyadditions.json", "actuallyadditions"),
        LateMixinModule("mixins.stackupup.capacity.late.cyclopscore.json", "cyclopscore"),
        LateMixinModule("mixins.stackupup.capacity.late.enderio.json", "enderio"),
        LateMixinModule("mixins.stackupup.capacity.late.ic2.json", StackUpUpIds.IC2_MOD_ID),
        LateMixinModule("mixins.stackupup.capacity.late.mantle.json", "mantle"),
        LateMixinModule("mixins.stackupup.capacity.late.refinedstorage.json", "refinedstorage"),
        LateMixinModule("mixins.stackupup.capacity.late.integrateddynamics.json", "integrateddynamics"),
        LateMixinModule("mixins.stackupup.capacity.late.limelib.json", "limelib"),
        LateMixinModule("mixins.stackupup.capacity.late.immersiveengineering.json", "immersiveengineering"),
        LateMixinModule("mixins.stackupup.capacity.late.nuclearcraft.json", "nuclearcraft"),
        LateMixinModule("mixins.stackupup.capacity.late.colossalchests.json", "colossalchests"),
        LateMixinModule("mixins.stackupup.capacity.late.techreborn.json", "techreborn"),
        LateMixinModule("mixins.stackupup.capacity.late.farmersdelight.json", "farmersdelight"),
    )

    internal val classicCapacityModules: List<LateMixinModule> = listOf(
        LateMixinModule(StackUpUpIds.CAPACITY_LATE_IC2_CLASSIC_MIXIN_CONFIG, StackUpUpIds.IC2_MOD_ID),
    )

    private companion object {
        private const val AE2_MOD_ID = "ae2"
        private val AE2_SUPERGIANT_MARKERS = listOf(
            "ae2/api/inventories/InternalInventory.class",
            "ae2/util/inv/AppEngInternalInventory.class",
        )
    }
}
