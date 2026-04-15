package pl.asie.stackup

import com.google.common.collect.ImmutableList
import gnu.trove.map.TObjectIntMap
import gnu.trove.map.hash.TObjectIntHashMap
import net.minecraft.block.Block
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.item.ItemBlock
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.config.Configuration
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.client.event.ConfigChangedEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.SidedProxy
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.event.FMLServerStartingEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.registry.ForgeRegistries
import net.minecraftforge.registries.IForgeRegistry
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import pl.asie.stackup.config.ConfigUtils
import pl.asie.stackup.script.ScriptHandler
import pl.asie.stackup.script.TokenBoolean
import pl.asie.stackup.script.TokenClass
import pl.asie.stackup.script.TokenProvider
import pl.asie.stackup.script.TokenResourceLocation
import java.io.File
import java.util.Objects

@Mod(
    modid = "stackup",
    name = "StackUpUp",
    version = StackUp.VERSION,
    dependencies = "before:refinedstorage;before:mantle;before:ic2;before:appliedenergistics2;before:actuallyadditions",
    guiFactory = "pl.asie.stackup.config.ConfigGuiFactory"
)
class StackUp {
    private var hadPostInit: Boolean = false

    private fun handleConfigChanged(runtime: Boolean) {
        if (!runtime) {
            StackUpConfig.scriptingActive = ConfigUtils.getBoolean(
                config,
                "general",
                "enableScripting",
                true,
                "Enable StackUp's own rules/scripting format.",
                true
            )
            maxStackSize = ConfigUtils.getInt(
                config,
                "general",
                "maxStackSize",
                64,
                64,
                999999999,
                "The maximum stack size for new stacks.",
                true
            )

            StackUpConfig.coremodPatchRefinedStorage = ConfigUtils.getBoolean(
                config,
                "modpatches",
                "refinedstorage",
                true,
                "Should Refined Storage be patched to support large stacks? (GUI extraction only; works fine otherwise).",
                true
            )
            StackUpConfig.coremodPatchMantle = ConfigUtils.getBoolean(
                config,
                "modpatches",
                "mantle",
                true,
                "Should Mantle (Tinkers' Construct, etc.) be patched to support large stacks?",
                true
            )
            StackUpConfig.coremodPatchIc2 = ConfigUtils.getBoolean(
                config,
                "modpatches",
                "industrialcraft2",
                true,
                "Should IndustrialCraft 2 be patched to support large stacks?",
                true
            )
            StackUpConfig.coremodPatchAppliedEnergistics2 = ConfigUtils.getBoolean(
                config,
                "modpatches",
                "appliedenergistics2",
                true,
                "Should Applied Energistics 2 be patched to support large stacks?",
                true
            )
            StackUpConfig.coremodPatchActuallyAdditions = ConfigUtils.getBoolean(
                config,
                "modpatches",
                "actuallyadditions",
                true,
                "Should Actually Additions be patched to support large stacks?",
                true
            )
            StackUpConfig.compatChiselsBits = ConfigUtils.getBoolean(
                config,
                "modpatches",
                "chiselsandbits",
                true,
                "Should Chisels & Bits bits automatically be adjusted by the mod to match the bit bag's stacking size?",
                true
            )
        }

        StackUpConfig.lowestScaleDown = ConfigUtils.getFloat(
            config,
            "client",
            "fontScaleMinimum",
            0.6f,
            0.0f,
            1.0f,
            "Lower bound of the font scale used by StackUp.",
            false
        )
        StackUpConfig.highestScaleDown = ConfigUtils.getFloat(
            config,
            "client",
            "fontScaleMaximum",
            0.6f,
            0.0f,
            1.0f,
            "Upper bound of the font scale used by StackUp.",
            false
        )
        StackUpConfig.scaleTextLinearly = ConfigUtils.getBoolean(
            config,
            "client",
            "fontScaleLinear",
            false,
            "Scale text linearly as opposed to by steps. Useful with SmoothFont.",
            false
        )

        StackUpConfig.equalScaleDown = kotlin.math.abs(StackUpConfig.lowestScaleDown - StackUpConfig.highestScaleDown) <= 0.001f

        if (config.hasChanged()) {
            config.save()
        }
    }

    @SubscribeEvent
    fun onConfigChanged(event: ConfigChangedEvent.OnConfigChangedEvent) {
        if ("stackup" == event.modID) {
            handleConfigChanged(true)
        }
    }

    @Mod.EventHandler
    fun preInit(event: FMLPreInitializationEvent) {
        if (!StackUpConfig.coremodActive) {
            throw RuntimeException("Cannot load StackUp - coremod not present!")
        }

        logger = LogManager.getLogger()

        config = Configuration(event.suggestedConfigurationFile)
        handleConfigChanged(false)

        stackupScriptLocation = File(event.modConfigurationDirectory, "stackup")
        if (StackUpConfig.scriptingActive && !stackupScriptLocation.exists()) {
            stackupScriptLocation.mkdir()
        }

        MinecraftForge.EVENT_BUS.register(this)
        MinecraftForge.EVENT_BUS.register(proxy)

        Items.AIR.setMaxStackSize(maxStackSize)

        TokenProvider.addToken("isBlock") {
            TokenBoolean<Item> { i -> i is ItemBlock || Block.getBlockFromItem(i) != Blocks.AIR }
        }
        TokenProvider.addToken("blockClass") {
            TokenClass<Item>({ i ->
                if (i is ItemBlock) {
                    val b = Block.getBlockFromItem(i)
                    if (b != Blocks.AIR) {
                        ImmutableList.of<Class<*>>(b.javaClass)
                    } else {
                        ImmutableList.of<Class<*>>()
                    }
                } else {
                    ImmutableList.of<Class<*>>()
                }
            }, false)
        }
        TokenProvider.addToken("itemClass") { TokenClass<Item>({ i -> ImmutableList.of<Class<*>>(i.javaClass) }, false) }
        TokenProvider.addToken("id") {
            TokenResourceLocation<Item> { i -> ImmutableList.of(Objects.requireNonNull(i.registryName).toString()) }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onRegisterItems(event: RegistryEvent.Register<Item>) {
        if (hadPostInit) {
            reload(event.registry)
        }
    }

    @Mod.EventHandler
    fun postInit(event: FMLPostInitializationEvent) {
        reload(ForgeRegistries.ITEMS)
        hadPostInit = true
    }

    @Mod.EventHandler
    fun serverStarting(event: FMLServerStartingEvent) {
        event.registerServerCommand(CommandStackUp())
    }

    companion object {
        const val VERSION: String = "@VERSION@"

        @SidedProxy(
            modId = "stackup",
            clientSide = "pl.asie.stackup.ProxyClient",
            serverSide = "pl.asie.stackup.ProxyCommon"
        )
        @JvmField
        var proxy: ProxyCommon? = null

        @JvmField
        var logger: Logger? = null

        @JvmField
        var maxStackSize: Int = 64

        private lateinit var stackupScriptLocation: File
        private lateinit var config: Configuration

        private val oldStackValues: TObjectIntMap<Item> = TObjectIntHashMap()

        @JvmStatic
        fun getConfig(): Configuration = config

        @JvmStatic
        fun backupStackSize(i: Item) {
            if (!oldStackValues.containsKey(i)) {
                oldStackValues.put(i, i.itemStackLimit)
            }
        }

        @JvmStatic
        fun reload(registry: IForgeRegistry<Item>) {
            for (i in oldStackValues.keySet()) {
                i.setMaxStackSize(oldStackValues.get(i))
            }
            oldStackValues.clear()
            if (StackUpConfig.scriptingActive) {
                ScriptHandler().process(registry, stackupScriptLocation)
            }
        }
    }
}
