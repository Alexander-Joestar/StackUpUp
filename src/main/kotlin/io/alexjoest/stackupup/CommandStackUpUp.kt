package io.alexjoest.stackupup

import io.alexjoest.stackupup.rules.io.RuleFeedback
import net.minecraft.command.CommandBase
import net.minecraft.command.CommandException
import net.minecraft.command.ICommandSender
import net.minecraft.command.WrongUsageException
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentTranslation
import java.awt.Desktop
import java.io.IOException
import javax.annotation.Nullable

class CommandStackUpUp internal constructor(
    private val stateAccess: StateAccess = StackUpUpStateAccess,
) : CommandBase() {
    private val subcommands = arrayOf(SUBCOMMAND_RELOAD, SUBCOMMAND_EDIT, SUBCOMMAND_STATE)

    constructor() : this(StackUpUpStateAccess)

    override fun getName(): String = StackUpUpIds.MOD_ID

    override fun getAliases(): MutableList<String> = mutableListOf()

    override fun getUsage(sender: ICommandSender): String = StackUpUpIds.COMMAND_USAGE_KEY

    @Throws(CommandException::class)
    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<out String>) {
        executeCommand(sender, args)
    }

    internal fun executeCommand(sender: ICommandSender, args: Array<out String>) {
        when (args.getOrNull(0)) {
            SUBCOMMAND_RELOAD -> emitReloadFeedback(sender)
            SUBCOMMAND_EDIT -> openRulesFile(sender)
            SUBCOMMAND_STATE -> handleStateCommand(sender, args)
            else -> throw WrongUsageException(getUsage(sender))
        }
    }

    override fun getTabCompletions(
        server: MinecraftServer,
        sender: ICommandSender,
        args: Array<out String>,
        @Nullable targetPos: BlockPos?,
    ): MutableList<String> = tabCompletions(args)

    internal fun tabCompletions(args: Array<out String>): MutableList<String> = when (args.size) {
        1 -> getListOfStringsMatchingLastWord(args, *subcommands)
        2 -> if (args[0] == SUBCOMMAND_STATE) getListOfStringsMatchingLastWord(args, STATE_ACTION_GET, STATE_ACTION_SET) else mutableListOf()
        3 -> if (args[0] == SUBCOMMAND_STATE && args[1] == STATE_ACTION_SET) {
            getListOfStringsMatchingLastWord(args, STATE_VALUE_TRUE, STATE_VALUE_FALSE)
        } else {
            mutableListOf()
        }
        else -> mutableListOf()
    }

    private fun handleStateCommand(sender: ICommandSender, args: Array<out String>) {
        when (args.getOrNull(1)) {
            STATE_ACTION_GET -> {
                val name = args.getOrNull(2) ?: throw WrongUsageException(getUsage(sender))
                val value = stateAccess.getState(name)
                sender.reply(
                    if (value) StackUpUpIds.COMMAND_STATE_GET_KEY else StackUpUpIds.COMMAND_STATE_MISSING_KEY,
                    name,
                    value,
                )
            }
            STATE_ACTION_SET -> {
                val name = args.getOrNull(2) ?: throw WrongUsageException(getUsage(sender))
                val value = parseStateBoolean(args.getOrNull(3) ?: throw WrongUsageException(getUsage(sender)))
                stateAccess.setState(name, value)
                sender.reply(StackUpUpIds.COMMAND_STATE_SET_KEY, name, value)
            }
            else -> throw WrongUsageException(getUsage(sender))
        }
    }

    private fun parseStateBoolean(value: String): Boolean = when (value.lowercase()) {
        STATE_VALUE_TRUE, "1", "yes", "on" -> true
        STATE_VALUE_FALSE, "0", "no", "off" -> false
        else -> throw WrongUsageException(StackUpUpIds.COMMAND_USAGE_KEY)
    }

    private fun emitReloadFeedback(sender: ICommandSender) {
        val report = StackUpUp.reload()
        sender.reply(StackUpUpIds.COMMAND_RELOAD_SUCCESS_KEY)
        RuleFeedback.emitReloadErrors(report, sender::sendMessage)
        RuleFeedback.emitWarnings(report, sender::sendMessage)
    }

    private fun openRulesFile(sender: ICommandSender) {
        val file = RuleRuntimeCoordinator.getRulesFile()
        if (!file.exists()) {
            sender.reply(StackUpUpIds.COMMAND_EDIT_MISSING_KEY, file.absolutePath)
            return
        }

        val desktop = openCapableDesktop()
        if (desktop == null) {
            sender.replyUnsupportedOpen()
            return
        }

        try {
            // 这里故意使用 OPEN，而不是 EDIT。
            // OPEN 会交给系统文件关联，尽量遵循用户自己的桌面默认行为，不强行指定编辑器。
            desktop.open(file)
            sender.reply(StackUpUpIds.COMMAND_EDIT_SUCCESS_KEY, file.absolutePath)
        } catch (e: IOException) {
            sender.reply(StackUpUpIds.COMMAND_EDIT_FAILED_KEY, e.message ?: "unknown")
        }
    }

    private fun openCapableDesktop(): Desktop? {
        if (!Desktop.isDesktopSupported()) {
            return null
        }

        val desktop = Desktop.getDesktop()
        return if (desktop.isSupported(Desktop.Action.OPEN)) desktop else null
    }

    private fun ICommandSender.reply(key: String, vararg args: Any) {
        sendMessage(TextComponentTranslation(key, *args))
    }

    private fun ICommandSender.replyUnsupportedOpen() {
        reply(StackUpUpIds.COMMAND_EDIT_UNSUPPORTED_KEY)
    }

    private companion object {
        private const val SUBCOMMAND_RELOAD = "reload"
        private const val SUBCOMMAND_EDIT = "edit"
        private const val SUBCOMMAND_STATE = "state"
        private const val STATE_ACTION_GET = "get"
        private const val STATE_ACTION_SET = "set"
        private const val STATE_VALUE_TRUE = "true"
        private const val STATE_VALUE_FALSE = "false"
    }

    internal interface StateAccess {
        fun getState(name: String): Boolean

        fun setState(name: String, value: Boolean)
    }

    private object StackUpUpStateAccess : StateAccess {
        override fun getState(name: String): Boolean = StackUpUp.getState(name)

        override fun setState(name: String, value: Boolean) {
            StackUpUp.setState(name, value)
        }
    }
}
