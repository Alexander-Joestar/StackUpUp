package io.alexjoest.stackupup.bootstrap

import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.config.MixinToggles
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import zone.rong.mixinbooter.Context
import zone.rong.mixinbooter.ILateMixinLoader

class StackUpUpLateMixinLoader : ILateMixinLoader {
    override fun getMixinConfigs(): List<String> {
        // T14.5 停止条件 3：启动期校验 loader 注册表 ↔ JSON 资源 ↔ 类登记（双向中的正向）。
        // late 是 optional 层：问题只按 ERROR 记录、不中止装载——MixinBooter 的 late loader 循环
        // 对抛出的异常整体中断（LoadControllerMixin 证据），抛异常会连带中止全部剩余 late 配置。
        val problems = MixinConfigValidator.validateConfigs(
            modules.map { it.config },
            javaClass.classLoader,
        )
        MixinConfigValidator.logProblems(problems)
        // late 目标类全部来自第三方 mod（T2a §5 无 jar/源码），目标存在性不校验，记录 UNKNOWN。
        logger.info("Late mixin configs target third-party mod classes; target existence is not validated (UNKNOWN, no third-party sources)")
        return modules.map { it.config }
    }

    override fun shouldMixinConfigQueue(context: Context): Boolean {
        val module = modules.firstOrNull { it.config == context.mixinConfig() }
        if (module == null) {
            // T14.5 停止条件 3：不在模块表的配置不再无条件入队（原 ?: return true 会绕过 mod/toggle 检查静默全量装载）。
            logger.error(
                "Refusing to queue unknown late mixin config '{}': not registered in the module table",
                context.mixinConfig(),
            )
            return false
        }
        if (!context.isModPresent(module.modId)) {
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

    companion object {
        private val logger: Logger = LogManager.getLogger("stackupup.mixin.late")

        private val modules: List<LateMixinModule> = listOf(
            LateMixinModule(StackUpUpIds.LATE_AE2_MIXIN_CONFIG, "appliedenergistics2", "ae2") { MixinToggles.ae2 },
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
    }

    private data class LateMixinModule(val config: String, val modId: String, val toggleName: String, val toggle: () -> Boolean)
}
