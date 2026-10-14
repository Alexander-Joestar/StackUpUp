package io.alexjoest.stackupup

import io.alexjoest.stackupup.diagnostic.InspectSelector
import io.alexjoest.stackupup.diagnostic.InventoryInspection
import io.alexjoest.stackupup.rules.RuleMessageKey
import io.alexjoest.stackupup.rules.io.RuleSourceLocator
import io.alexjoest.stackupup.rules.io.RuleStateService
import net.minecraft.command.ICommandSender
import net.minecraft.command.WrongUsageException
import net.minecraft.entity.Entity
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.TextComponentString
import net.minecraft.util.text.TextComponentTranslation
import net.minecraft.world.World
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class CommandStackUpUpTest {
    private lateinit var tempDir: File
    private lateinit var worldDir: File
    private lateinit var worldMarkdownFile: File

    @BeforeEach
    fun setUp() {
        tempDir = createTempDirectory("stackupup-command").toFile()
        worldDir = File(tempDir, "world").apply { mkdirs() }
        worldMarkdownFile = File(File(File(worldDir, "data"), StackUpUpIds.MOD_ID), StackUpUpIds.WORLD_MARKDOWN_RULES_FILE_NAME)
        RuleSourceLocator.setWorldDirectoryForTests(worldDir)
    }

    @AfterEach
    fun tearDown() {
        RuleSourceLocator.setWorldDirectoryForTests(null)
        tempDir.deleteRecursively()
    }

    @Test
    fun commandMetadata_shouldUseStableIdsAndUsageKey() {
        val command = CommandStackUpUp()
        val sender = CapturingCommandSender()

        assertEquals(StackUpUpIds.MOD_ID, command.name)
        assertEquals(emptyList<String>(), command.aliases)
        assertEquals(RuleMessageKey.COMMAND_USAGE.translationKey, command.getUsage(sender))
    }

    @Test
    fun tabCompletions_shouldSuggestSubcommandsStateActionsAndBooleanTokens() {
        val command = CommandStackUpUp()

        assertEquals(listOf("reload"), command.completeArguments(arrayOf("r")))
        assertEquals(listOf("get", "set"), command.completeArguments(arrayOf("state", "")))
        assertEquals(listOf("false"), command.completeArguments(arrayOf("state", "set", "f")))
        assertEquals(listOf("slot", "block"), command.completeArguments(arrayOf("inspect", "")))
        assertEquals(listOf("none", "north"), command.completeArguments(arrayOf("inspect", "block", "0", "64", "0", "n")))
        assertEquals(emptyList<String>(), command.completeArguments(arrayOf("reload", "")))
        assertEquals(emptyList<String>(), command.completeArguments(arrayOf("state", "set", "false", "extra")))
    }

    @Test
    fun execute_shouldRejectMissingUnknownAndIncompleteStateUsage() {
        val command = command()
        val sender = CapturingCommandSender()

        assertWrongUsage(command, sender)
        assertWrongUsage(command, sender, "unknown")
        assertWrongUsage(command, sender, "state")
        assertWrongUsage(command, sender, "state", "get")
        assertWrongUsage(command, sender, "state", "set", "flag")
        assertWrongUsage(command, sender, "state", "set", "flag", "maybe")
        val inspectError = assertThrows(WrongUsageException::class.java) {
            command.executeArguments(sender, arrayOf("inspect", "block", "x", "1", "2", "none"))
        }
        assertEquals(RuleMessageKey.COMMAND_INSPECT_USAGE.translationKey, inspectError.message)
    }

    @Test
    fun inspectCommand_shouldUseRealInspectionAndRefuseInsufficientPermission() {
        val inventory = net.minecraft.inventory.InventoryBasic("inspect-test", false, 1)
        val container = object : net.minecraft.inventory.Container() {
            init {
                addSlotToContainer(net.minecraft.inventory.Slot(inventory, 0, 0, 0))
            }

            override fun canInteractWith(playerIn: net.minecraft.entity.player.EntityPlayer): Boolean = true
        }
        val report = InventoryInspection(emptyList()).inspectContainer(container, 0)
        val sender = CapturingCommandSender()
        val command = CommandStackUpUp(inspect = { _, selector ->
            assertEquals(InspectSelector.OpenContainer(), selector)
            report
        })

        command.executeArguments(sender, arrayOf("inspect"))

        assertEquals(RuleMessageKey.COMMAND_INSPECT_OBJECT.translationKey, (sender.messages[1] as TextComponentTranslation).key)
        assertTrue((sender.messages[1] as TextComponentTranslation).formatArgs[2].toString().startsWith(container.javaClass.name))
        assertEquals(4, command.requiredPermissionLevel)
        val denied = CapturingCommandSender(canUse = false)
        val exception = assertThrows(net.minecraft.command.CommandException::class.java) {
            command.executeArguments(denied, arrayOf("inspect"))
        }
        assertEquals(RuleMessageKey.COMMAND_INSPECT_PERMISSION.translationKey, exception.message)
        assertEquals(0, denied.messages.size)
    }

    @Test
    fun executeStateSet_shouldParseTrueBooleanTokensAndReportStateSet() {
        writeWorldMarkdownState("feature" to false)
        val command = command()
        val sender = CapturingCommandSender()

        command.executeArguments(sender, arrayOf("state", "set", "feature", "yes"))

        assertWorldMarkdownContains("- feature = true")
        sender.assertLastTranslation(RuleMessageKey.COMMAND_STATE_SET.translationKey, "feature", true)
    }

    @Test
    fun executeStateSet_shouldParseFalseBooleanTokensAndKeepCurrentFalseBehavior() {
        writeWorldMarkdownState("feature" to true)
        val command = command()
        val sender = CapturingCommandSender()

        command.executeArguments(sender, arrayOf("state", "set", "feature", "off"))

        assertWorldMarkdownContains("- feature = false")
        sender.assertLastTranslation(RuleMessageKey.COMMAND_STATE_SET.translationKey, "feature", false)
    }

    private fun assertWrongUsage(command: CommandStackUpUp, sender: ICommandSender, vararg args: String) {
        val exception = assertThrows(WrongUsageException::class.java) {
            command.executeArguments(sender, args)
        }
        assertEquals(RuleMessageKey.COMMAND_USAGE.translationKey, exception.message)
    }

    private fun command(): CommandStackUpUp {
        val service = RuleStateService { worldMarkdownFile }
        return CommandStackUpUp(
            getState = { name -> service.getState(name) ?: false },
            setState = service::setState,
        )
    }

    private fun writeWorldMarkdownState(vararg states: Pair<String, Boolean>) {
        worldMarkdownFile.parentFile.mkdirs()
        val stateLines = states.joinToString(System.lineSeparator()) { (name, value) -> "- $name = $value" }
        worldMarkdownFile.writeText(
            """
            # state
            $stateLines

            # rules
            item = minecraft:egg -> 64
            """.trimIndent() + System.lineSeparator(),
            Charsets.UTF_8,
        )
    }

    private fun assertWorldMarkdownContains(text: String) {
        val content = worldMarkdownFile.readText(Charsets.UTF_8)
        kotlin.test.assertTrue(content.contains(text), "Expected world markdown to contain '$text' but was:\n$content")
    }

    private class CapturingCommandSender(private val canUse: Boolean = true) : ICommandSender {
        val messages = mutableListOf<ITextComponent>()

        override fun getName(): String = "test"

        override fun getDisplayName(): ITextComponent = TextComponentString(name)

        override fun sendMessage(component: ITextComponent) {
            messages.add(component)
        }

        override fun canUseCommand(permLevel: Int, commandName: String): Boolean = canUse

        override fun getPosition(): BlockPos = BlockPos.ORIGIN

        override fun getPositionVector(): Vec3d = Vec3d.ZERO

        override fun getEntityWorld(): World = throw UnsupportedOperationException("World is unused by these command tests")

        override fun getCommandSenderEntity(): Entity? = null

        override fun sendCommandFeedback(): Boolean = true

        override fun getServer(): MinecraftServer? = null

        fun assertLastTranslation(key: String, vararg args: Any) {
            val message = messages.last() as TextComponentTranslation
            assertEquals(key, message.key)
            assertArrayEquals(args, message.formatArgs)
        }
    }
}
