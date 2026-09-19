package io.alexjoest.stackupup.bootstrap

import io.alexjoest.stackupup.StackUpUpCore
import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.config.MixinToggles
import net.minecraft.launchwrapper.Launch
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.spongepowered.asm.mixin.Mixins
import org.spongepowered.asm.mixin.connect.IMixinConnector
import zone.rong.mixinbooter.service.ModDiscoverer
import java.io.File
import java.util.jar.JarFile

/**
 * Mixin 装载入口（MixinBooter 11 官方路径，替代已弃用的 [zone.rong.mixinbooter.IEarlyMixinLoader] 与
 * [zone.rong.mixinbooter.ILateMixinLoader]）。
 *
 * 注册方式：jar manifest 的 `MixinConnector` 属性指向本类（MixinBooter 11.13 的
 * `MixinPlatformAgentDefault.prepare()` 读取，属性名取自 CleanMix `ManifestAttributes.MIXINCONNECTOR`）；
 * `MixinConnectorManager.loadConnectors()` 经无参构造器实例化后，在 `MixinBooterPlugin.injectData` 的
 * `platform.inject()` 内调用 [connect]——时序在 early loader 收集之后、late CONSTRUCTING 之前。11.13
 * CleanMix 模型中 DEFAULT 配置统一在 DEFAULT 阶段（EnvironmentStateTweaker → gotoPhase(DEFAULT)）装载，
 * 与入队时刻无关，因此 connect() 里 add 的早/晚配置无行为差异（全部配置 target 均为 `@env(DEFAULT)`）。
 *
 * 必须声明为 class 而非 Kotlin object：`MixinConnectorManager` 用 `getDeclaredConstructor().newInstance()`
 * 且不调用 setAccessible，Kotlin object 编译为私有构造器会导致 IllegalAccessException、connector 永不装载；
 * 与旧 `ILateMixinLoader`（class + 公共无参构造）同一约束。
 *
 * 装载语义与旧 loader 完全一致：
 * - early：冲突检测（[StackUpUpCore.ensureConflictState]）非空 → ERROR + 不 add（冲突禁用设计）；通过 → add；
 * - late：按模块表逐配置判断 mod 在场（[ModDiscoverer.isModPresent]；Supergiant `ae2` 的结果为 false
 *   或不可用时回退到 Cleanroom marker probe）+ [MixinToggles] 开关 → 条件 add；
 *   不在模块表的配置不装载。
 */
class StackUpUpMixinConnector : IMixinConnector {
    private val logger: Logger = LogManager.getLogger("stackupup.mixin.connector")

    override fun connect() {
        connectEarly()
        connectLate()
    }

    private fun connectEarly() {
        val conflicts = StackUpUpCore.ensureConflictState()
        if (!shouldQueueEarly(conflicts)) {
            return
        }
        try {
            Mixins.addConfiguration(StackUpUpIds.EARLY_MIXIN_CONFIG)
        } catch (e: Exception) {
            logger.error("Early mixin loading failed; early mixins are disabled (late mixins continue)", e)
        }
    }

    private fun connectLate() {
        val isModPresent: (String) -> Boolean = { modId ->
            isModPresentForConnector(modId)
        }
        for ((config) in modules) {
            if (!shouldQueue(config, isModPresent)) {
                continue
            }
            Mixins.addConfiguration(config)
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
        if (forgePresence == true) {
            return true
        }
        return try {
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
     * early 装载决策（原 getMixinConfigs 的冲突分支语义）：冲突非空 → ERROR 说明原因并拒绝装载；
     * 无冲突 → 允许装载。冲突禁用是既有设计，但必须 ERROR 说明原因，不再静默返回空表（T14.5 停止条件 1/5）。
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

    /**
     * 单配置装载决策（原 shouldMixinConfigQueue(Context) 语义；connect() 无 Context 参数，mod 在场判断改为
     * 注入式谓词，生产路径由 [connectLate] 传入按 modId 分派的在场判断）。
     */
    internal fun shouldQueue(config: String, isModPresent: (String) -> Boolean): Boolean {
        val module = modules.firstOrNull { it.config == config }
        if (module == null) {
            // T14.5 停止条件 3：不在模块表的配置不再无条件入队（原 ?: return true 会绕过 mod/toggle 检查静默全量装载）。
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
        if (!module.toggle()) {
            logger.info("Skipping late mixin config '{}': MixinToggles.{} is disabled", module.config, module.toggleName)
            return false
        }
        return true
    }

    internal data class LateMixinModule(val config: String, val modId: String, val toggleName: String, val toggle: () -> Boolean)

    internal val modules: List<LateMixinModule> = listOf(
        LateMixinModule(StackUpUpIds.LATE_AE2_MIXIN_CONFIG, "appliedenergistics2", "ae2") { MixinToggles.ae2 },
        LateMixinModule(StackUpUpIds.LATE_AE2_SUPERGIANT_MIXIN_CONFIG, "ae2", "ae2Supergiant") { MixinToggles.ae2Supergiant },
        LateMixinModule(StackUpUpIds.LATE_BRANDONSCORE_MIXIN_CONFIG, "brandonscore", "brandonsCore") { MixinToggles.brandonsCore },
        LateMixinModule(StackUpUpIds.LATE_ACTUALLY_ADDITIONS_MIXIN_CONFIG, "actuallyadditions", "actuallyAdditions") { MixinToggles.actuallyAdditions },
        LateMixinModule(StackUpUpIds.LATE_CYCLOPSCORE_MIXIN_CONFIG, "cyclopscore", "cyclopsCore") { MixinToggles.cyclopsCore },
        LateMixinModule(StackUpUpIds.LATE_ENDERIO_MIXIN_CONFIG, "enderio", "enderIo") { MixinToggles.enderIo },
        LateMixinModule(StackUpUpIds.LATE_IC2_MIXIN_CONFIG, "ic2", "ic2") { MixinToggles.ic2 },
        LateMixinModule(StackUpUpIds.LATE_MANTLE_MIXIN_CONFIG, "mantle", "mantle") { MixinToggles.mantle },
        LateMixinModule(StackUpUpIds.LATE_REFINED_STORAGE_MIXIN_CONFIG, "refinedstorage", "refinedStorage") { MixinToggles.refinedStorage },
        LateMixinModule(StackUpUpIds.LATE_STORAGE_NETWORK_MIXIN_CONFIG, "storagenetwork", "storageNetwork") { MixinToggles.storageNetwork },
        LateMixinModule(StackUpUpIds.LATE_INTEGRATEDDYNAMICS_MIXIN_CONFIG, "integrateddynamics", "integratedDynamics") { MixinToggles.integratedDynamics },
        LateMixinModule(StackUpUpIds.LATE_LIMELIB_MIXIN_CONFIG, "limelib", "limeLib") { MixinToggles.limeLib },
        LateMixinModule(StackUpUpIds.LATE_IMMERSIVEENGINEERING_MIXIN_CONFIG, "immersiveengineering", "immersiveEngineering") {
            MixinToggles.immersiveEngineering
        },
        LateMixinModule(StackUpUpIds.LATE_NUCLEARCRAFT_MIXIN_CONFIG, "nuclearcraft", "nuclearCraft") { MixinToggles.nuclearCraft },
        LateMixinModule(StackUpUpIds.LATE_COLOSSALCHESTS_MIXIN_CONFIG, "colossalchests", "colossalChests") { MixinToggles.colossalChests },
        LateMixinModule(StackUpUpIds.LATE_GREGTECH_MIXIN_CONFIG, "gregtech", "gregTech") { MixinToggles.gregTech },
    )

    private companion object {
        private const val AE2_MOD_ID = "ae2"
        private val AE2_SUPERGIANT_MARKERS = listOf(
            "ae2/api/inventories/InternalInventory.class",
            "ae2/util/inv/AppEngInternalInventory.class",
        )
    }
}
