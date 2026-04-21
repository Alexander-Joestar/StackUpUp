package io.alexjoest.stackupup

import java.io.File
import net.minecraft.item.Item
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.config.Config
import net.minecraftforge.common.config.ConfigManager
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.client.event.ConfigChangedEvent
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.SidedProxy
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.event.FMLServerStartedEvent
import net.minecraftforge.fml.common.event.FMLServerStartingEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.registry.ForgeRegistries
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import io.alexjoest.stackupup.Tags
import io.alexjoest.stackupup.config.LegacyConfigMigration
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.io.RuleFileLocator
import io.alexjoest.stackupup.rules.io.RuleReloadReport
import io.alexjoest.stackupup.rules.io.RuleSourceLocator

@Mod(
    modid = StackUpUpIds.MOD_ID,
    name = StackUpUpIds.MOD_NAME,
    version = StackUpUp.VERSION,
    dependencies = "required-after:mixinbooter@[10.0,);required-after:forgelin_continuous@[2.1.0.0,);before:refinedstorage;before:mantle;before:ic2;before:appliedenergistics2;before:actuallyadditions",
    guiFactory = StackUpUpIds.CONFIG_GUI_FACTORY_CLASS_NAME
)
class StackUpUp {
    private var hadPostInit: Boolean = false

    private fun handleConfigChanged(runtime: Boolean) {
        ConfigManager.sync(CONFIG_ID, Config.Type.INSTANCE)
        StackUpUpConfig.applyRuntimeValues()
    }

    @SubscribeEvent
    fun onConfigChanged(event: ConfigChangedEvent.OnConfigChangedEvent) {
        if (MOD_ID == event.modID && (event.configID == null || CONFIG_ID == event.configID)) {
            handleConfigChanged(true)
        }
    }

    @Mod.EventHandler
    fun preInit(event: FMLPreInitializationEvent) {
        StackUpUpConfig.coremodActive = StackUpUpCore.isCoremodInjected()
        if (!StackUpUpConfig.coremodActive) {
            throw RuntimeException("Cannot load StackUpUp - coremod not present!")
        }

        logger = LogManager.getLogger()
        LegacyConfigMigration.migrate(event.modConfigurationDirectory)
        RuleFileLocator.setConfigDirectory(event.modConfigurationDirectory)
        handleConfigChanged(false)

        MinecraftForge.EVENT_BUS.register(this)
        MinecraftForge.EVENT_BUS.register(proxy)
        proxy?.registerDevAutomation()

    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onRegisterItems(event: RegistryEvent.Register<Item>) {
        if (hadPostInit) {
            reload()
        }
    }

    @Mod.EventHandler
    fun postInit(event: FMLPostInitializationEvent) {
        reload()
        hadPostInit = true
    }

    @Mod.EventHandler
    fun serverStarting(event: FMLServerStartingEvent) {
        event.registerServerCommand(CommandStackUpUp())
    }

    @Mod.EventHandler
    fun serverStarted(event: FMLServerStartedEvent) {
        val server = FMLCommonHandler.instance().minecraftServerInstance ?: return
        DevAutomationBridge.runServerAutomation(server)
    }

    companion object {
        const val VERSION: String = Tags.VERSION
        const val MOD_ID: String = StackUpUpIds.MOD_ID
        const val CONFIG_ID: String = StackUpUpIds.CONFIG_ID
        const val PUBLIC_ID: String = StackUpUpIds.PUBLIC_ID
        const val RULES_FILE_NAME_PUBLIC: String = StackUpUpIds.RULES_FILE_NAME

        @SidedProxy(
            modId = MOD_ID,
            clientSide = StackUpUpIds.PROXY_CLIENT_CLASS_NAME,
            serverSide = StackUpUpIds.PROXY_COMMON_CLASS_NAME
        )
        @JvmField
        var proxy: ProxyCommon? = null

        @JvmField
        var logger: Logger? = null

        @JvmStatic
        fun backupStackSize(item: Item) {
            StackSizeBackupRegistry.backup(item)
        }

        @JvmStatic
        fun reload(): RuleReloadReport {
            val report = RuleRuntimeCoordinator.reload()
            proxy?.markRuleStatusDirty()
            logReloadReport(report)
            return report
        }

        @JvmStatic
        fun getRulesFile(): File = RuleRuntimeCoordinator.getRulesFile()

        @JvmStatic
        fun getWorldRulesFile(): File? = RuleRuntimeCoordinator.getWorldRulesFile()

        @JvmStatic
        fun persistWorldRules(sourceId: String, lines: List<String>): Boolean = RuleRuntimeCoordinator.persistWorldRules(sourceId, lines)

        private fun logReloadReport(report: RuleReloadReport) {
            requireNotNull(logger).info("Loaded {} DSL rules from {}", report.snapshot.rules.size, report.file.absolutePath)
            report.errors.forEach(requireNotNull(logger)::error)
            for (warning in report.warnings) {
                if (!StackUpUpConfig.ruleComplexityWarnings) {
                    continue
                }
                requireNotNull(logger).warn("[Rule Complexity] {} {}", warning.translationKey, warning.args.joinToString())
            }
        }

    }
}




