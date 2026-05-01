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

class CommandStackUpUp : CommandBase() {
    private val subcommands = arrayOf("reload", "edit", "state")

    override fun getName(): String = StackUpUpIds.MOD_ID

    override fun getAliases(): MutableList<String> = mutableListOf()

    override fun getUsage(sender: ICommandSender): String = StackUpUpIds.COMMAND_USAGE_KEY

    @Throws(CommandException::class)
    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<out String>) {
        when (args.getOrNull(0)) {
            "reload" -> emitReloadFeedback(sender)
            "edit" -> openRulesFile(sender)
            "state" -> handleStateCommand(sender, args.drop(1))
            else -> throw WrongUsageException(getUsage(sender))
        }
    }

    override fun getTabCompletions(
        server: MinecraftServer,
        sender: ICommandSender,
        args: Array<out String>,
        @Nullable targetPos: BlockPos?,
    ): MutableList<String> = when (args.size) {
        1 -> getListOfStringsMatchingLastWord(args, *subcommands)
        2 -> if (args[0] == "state") getListOfStringsMatchingLastWord(args, "get", "set") else mutableListOf()
        3 -> if (args[0] == "state" && args[1] == "set") getListOfStringsMatchingLastWord(args, "true", "false") else mutableListOf()
        else -> mutableListOf()
    }

    private fun handleStateCommand(sender: ICommandSender, args: List<String>) {
        when (args.firstOrNull()) {
            "get" -> {
                val name = args.getOrNull(1) ?: throw WrongUsageException(getUsage(sender))
                val value = StackUpUp.getState(name)
                sender.reply(
                    if (value) StackUpUpIds.COMMAND_STATE_GET_KEY else StackUpUpIds.COMMAND_STATE_MISSING_KEY,
                    name,
                    value,
                )
            }
            "set" -> {
                val name = args.getOrNull(1) ?: throw WrongUsageException(getUsage(sender))
                val value = parseStateBoolean(args.getOrNull(2) ?: throw WrongUsageException(getUsage(sender)))
                StackUpUp.setState(name, value)
                sender.reply(StackUpUpIds.COMMAND_STATE_SET_KEY, name, value)
            }
            else -> throw WrongUsageException(getUsage(sender))
        }
    }

    private fun parseStateBoolean(value: String): Boolean = when (value.lowercase()) {
        "true", "1", "yes", "on" -> true
        "false", "0", "no", "off" -> false
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

        if (!Desktop.isDesktopSupported()) {
            sender.replyUnsupportedOpen()
            return
        }

        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.OPEN)) {
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

    private fun ICommandSender.reply(key: String, vararg args: Any) {
        sendMessage(TextComponentTranslation(key, *args))
    }

    private fun ICommandSender.replyUnsupportedOpen() {
        reply(StackUpUpIds.COMMAND_EDIT_UNSUPPORTED_KEY)
    }
}
