package io.alexjoest.stackupup

import io.alexjoest.stackupup.rules.io.RuleSourceLocator
import io.alexjoest.stackupup.rules.io.RuleStateService
import net.minecraft.command.CommandResultStats
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
    fun `commandMetadata_shouldUseStableIdsAndUsageKey`() {
        val command = CommandStackUpUp()
        val sender = CapturingCommandSender()

        assertEquals(StackUpUpIds.MOD_ID, command.name)
        assertEquals(emptyList<String>(), command.aliases)
        assertEquals(StackUpUpIds.COMMAND_USAGE_KEY, command.getUsage(sender))
    }

    @Test
    fun `tabCompletions_shouldSuggestSubcommandsStateActionsAndBooleanTokens`() {
        val command = CommandStackUpUp()
        val sender = CapturingCommandSender()

        assertEquals(listOf("reload"), command.tabCompletions(arrayOf("r")))
        assertEquals(listOf("get", "set"), command.tabCompletions(arrayOf("state", "")))
        assertEquals(listOf("false"), command.tabCompletions(arrayOf("state", "set", "f")))
        assertEquals(emptyList<String>(), command.tabCompletions(arrayOf("reload", "")))
        assertEquals(emptyList<String>(), command.tabCompletions(arrayOf("state", "set", "false", "extra")))
    }

    @Test
    fun `execute_shouldRejectMissingUnknownAndIncompleteStateUsage`() {
        val command = command()
        val sender = CapturingCommandSender()

        assertWrongUsage(command, sender)
        assertWrongUsage(command, sender, "unknown")
        assertWrongUsage(command, sender, "state")
        assertWrongUsage(command, sender, "state", "get")
        assertWrongUsage(command, sender, "state", "set", "flag")
        assertWrongUsage(command, sender, "state", "set", "flag", "maybe")
    }

    @Test
    fun `executeStateSet_shouldParseTrueBooleanTokensAndReportStateSet`() {
        writeWorldMarkdownState("feature" to false)
        val command = command()
        val sender = CapturingCommandSender()

        command.executeCommand(sender, arrayOf("state", "set", "feature", "yes"))

        assertWorldMarkdownContains("- feature = true")
        sender.assertLastTranslation(StackUpUpIds.COMMAND_STATE_SET_KEY, "feature", true)
    }

    @Test
    fun `executeStateSet_shouldParseFalseBooleanTokensAndKeepCurrentFalseBehavior`() {
        writeWorldMarkdownState("feature" to true)
        val command = command()
        val sender = CapturingCommandSender()

        command.executeCommand(sender, arrayOf("state", "set", "feature", "off"))

        assertWorldMarkdownContains("- feature = false")
        sender.assertLastTranslation(StackUpUpIds.COMMAND_STATE_SET_KEY, "feature", false)
    }

    private fun assertWrongUsage(command: CommandStackUpUp, sender: ICommandSender, vararg args: String) {
        val exception = assertThrows(WrongUsageException::class.java) {
            command.executeCommand(sender, args)
        }
        assertEquals(StackUpUpIds.COMMAND_USAGE_KEY, exception.message)
    }

    private fun command(): CommandStackUpUp = CommandStackUpUp(StateAccessAdapter(RuleStateService { worldMarkdownFile }))

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

    private class StateAccessAdapter(
        private val service: RuleStateService,
    ) : CommandStackUpUp.StateAccess {
        override fun getState(name: String): Boolean = service.getState(name) ?: false

        override fun setState(name: String, value: Boolean) {
            service.setState(name, value)
        }
    }

    private class CapturingCommandSender : ICommandSender {
        val messages = mutableListOf<ITextComponent>()

        override fun getName(): String = "test"

        override fun getDisplayName(): ITextComponent = TextComponentString(name)

        override fun sendMessage(component: ITextComponent) {
            messages.add(component)
        }

        override fun canUseCommand(permLevel: Int, commandName: String): Boolean = true

        override fun getPosition(): BlockPos = BlockPos.ORIGIN

        override fun getPositionVector(): Vec3d = Vec3d.ZERO

        override fun getEntityWorld(): World = throw UnsupportedOperationException("World is unused by these command tests")

        override fun getCommandSenderEntity(): Entity? = null

        override fun sendCommandFeedback(): Boolean = true

        override fun setCommandStat(type: CommandResultStats.Type, amount: Int) {
        }

        override fun getServer(): MinecraftServer? = null

        fun assertLastTranslation(key: String, vararg args: Any) {
            val message = messages.last() as TextComponentTranslation
            assertEquals(key, message.key)
            assertArrayEquals(args, message.formatArgs)
        }
    }
}
