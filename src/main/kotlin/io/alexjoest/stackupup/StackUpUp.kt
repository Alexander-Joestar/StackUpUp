package io.alexjoest.stackupup

import io.alexjoest.stackupup.config.ConfigFileSanitizer
import io.alexjoest.stackupup.rules.io.RuleFileLocator
import io.alexjoest.stackupup.rules.io.RuleReloadReport
import io.alexjoest.stackupup.rules.io.RuleSourceLocator
import io.alexjoest.stackupup.rules.io.RuleStateStore
import net.minecraft.item.Item
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.config.Config
import net.minecraftforge.common.config.ConfigManager
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.client.event.ConfigChangedEvent
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.SidedProxy
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.event.FMLServerStartedEvent
import net.minecraftforge.fml.common.event.FMLServerStartingEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicReference

@Mod(
    modid = StackUpUpIds.MOD_ID,
    name = StackUpUpIds.MOD_NAME,
    version = StackUpUp.VERSION,
    dependencies = (
        "required-after:mixinbooter@[10.0,);required-after:forgelin_continuous@[2.1.0.0,);" +
            "before:stackup;before:refinedstorage;before:mantle;before:ic2;" +
            "before:appliedenergistics2;before:actuallyadditions"
        ),
    guiFactory = StackUpUpIds.CONFIG_GUI_FACTORY_CLASS_NAME,
)
class StackUpUp {
    private var hadPostInit: Boolean = false

    private fun handleConfigChanged(activateReloadControlledValues: Boolean = false) {
        ConfigManager.sync(CONFIG_ID, Config.Type.INSTANCE)
        StackUpUpConfig.applyRuntimeValues()
        if (activateReloadControlledValues) {
            StackUpUpConfig.applyReloadControlledValues()
        }
    }

    @SubscribeEvent
    fun onConfigChanged(event: ConfigChangedEvent.OnConfigChangedEvent) {
        if (MOD_ID == event.modID && (event.configID == null || CONFIG_ID == event.configID)) {
            handleConfigChanged()
            ConfigFileSanitizer.sanitize(Loader.instance().configDir)
        }
    }

    @Mod.EventHandler
    fun preInit(event: FMLPreInitializationEvent) {
        logger = LogManager.getLogger()
        if (StackUpUpCore.isDisabledForConflict()) {
            MinecraftForge.EVENT_BUS.register(proxy)
            proxy?.markConflictDisabled(StackUpUpCore.conflictingMods())
            logger?.warn(
                "StackUpUp disabled itself early because conflicting stacking mods were detected: {}",
                StackUpUpCore.conflictingMods().joinToString(", "),
            )
            return
        }

        StackUpUpConfig.coremodActive = StackUpUpCore.isCoremodInjected()
        if (!StackUpUpConfig.coremodActive) {
            throw RuntimeException("Cannot load StackUpUp - coremod not present!")
        }

        RuleFileLocator.setConfigDirectory(event.modConfigurationDirectory)
        handleConfigChanged(activateReloadControlledValues = true)
        ConfigFileSanitizer.sanitize(event.modConfigurationDirectory)

        MinecraftForge.EVENT_BUS.register(this)
        MinecraftForge.EVENT_BUS.register(proxy)
        proxy?.registerDevAutomation()
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onRegisterItems(@Suppress("UNUSED_PARAMETER") event: RegistryEvent.Register<Item>) {
        if (hadPostInit) {
            reload()
        }
    }

    @Mod.EventHandler
    fun postInit(@Suppress("UNUSED_PARAMETER") event: FMLPostInitializationEvent) {
        if (StackUpUpCore.isDisabledForConflict()) {
            return
        }
        reload()
        hadPostInit = true
    }

    @Mod.EventHandler
    fun serverStarting(event: FMLServerStartingEvent) {
        if (StackUpUpCore.isDisabledForConflict()) {
            return
        }
        event.registerServerCommand(CommandStackUpUp())
    }

    @Mod.EventHandler
    fun serverStarted(@Suppress("UNUSED_PARAMETER") event: FMLServerStartedEvent) {
        if (StackUpUpCore.isDisabledForConflict()) {
            return
        }
        initWorldMarkdown()
        reload()
        val server = FMLCommonHandler.instance().minecraftServerInstance ?: return
        DevAutomationBridge.runServerAutomation(server)
    }

    private fun initWorldMarkdown() {
        val worldMarkdownFile = RuleSourceLocator.resolveWorldMarkdownFile() ?: return
        if (worldMarkdownFile.exists()) {
            return
        }
        val templateFile = File(RuleFileLocator.resolveRulesDirectory(), StackUpUpIds.RULES_FILE_NAME)
        if (!templateFile.exists()) {
            return
        }
        worldMarkdownFile.parentFile?.mkdirs()
        try {
            Files.copy(templateFile.toPath(), worldMarkdownFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            logger?.warn("Failed to copy markdown template to world: {}", e.message)
        }
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
            serverSide = StackUpUpIds.PROXY_COMMON_CLASS_NAME,
        )
        @JvmField
        var proxy: ProxyCommon? = null

        @JvmField
        var logger: Logger? = null

        private val stateStoreCache = AtomicReference<RuleStateStore?>()

        private fun stateStore(): RuleStateStore? {
            val cached = stateStoreCache.get()
            if (cached != null) {
                return cached
            }
            val worldMarkdownFile = RuleSourceLocator.resolveWorldMarkdownFile() ?: return null
            val store = RuleStateStore(worldMarkdownFile)
            return if (stateStoreCache.compareAndSet(null, store)) {
                store
            } else {
                stateStoreCache.get()
            }
        }

        @JvmStatic
        @Synchronized
        fun getState(name: String): Boolean {
            val store = stateStore() ?: run {
                logger?.warn("Cannot read state '{}' because world markdown storage is unavailable", name)
                return false
            }
            return store.readStates()[name] ?: false
        }

        @JvmStatic
        @Synchronized
        fun setState(name: String, value: Boolean) {
            val store = stateStore() ?: run {
                logger?.warn("Cannot write state '{}' because world markdown storage is unavailable", name)
                return
            }
            val states = store.readStates().toMutableMap()
            states[name] = value
            val changed = store.writeStates(states)
            if (!changed) return
            reload()
        }

        @JvmStatic
        fun reload(): RuleReloadReport = RuleRuntimeCoordinator.run {
            StackUpUpConfig.applyReloadControlledValues()
            reload()
        }.also { report ->
            proxy?.markRuleStatusDirty()
            logReloadReport(report)
        }

        private fun logReloadReport(report: RuleReloadReport) {
            val activeLogger = logger ?: return
            activeLogger.info("Loaded {} DSL rules from {}", report.snapshot.rules.size, report.file.absolutePath)
            report.errors.map { it.format() }.forEach(activeLogger::error)
            for (warning in report.warnings) {
                if (!StackUpUpConfig.ruleComplexityWarnings) {
                    continue
                }
                activeLogger.warn(warning.format())
            }
        }
    }
}
