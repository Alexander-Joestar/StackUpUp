package io.alexjoest.stackupup

import java.awt.Desktop
import java.io.IOException
import javax.annotation.Nullable
import net.minecraft.command.CommandBase
import net.minecraft.command.CommandException
import net.minecraft.command.ICommandSender
import net.minecraft.command.WrongUsageException
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentTranslation
import io.alexjoest.stackupup.rules.io.RuleFeedback

class CommandStackUpUp : CommandBase() {
    private val subcommands = arrayOf("reload", "edit")

    override fun getName(): String = StackUpUpIds.MOD_ID

    override fun getAliases(): MutableList<String> = mutableListOf()

    override fun getUsage(sender: ICommandSender): String = "${StackUpUpIds.COMMAND_LANG_ROOT}.usage"

    @Throws(CommandException::class)
    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<out String>) {
        when (args.singleOrNull()) {
            "reload" -> emitReloadFeedback(sender)
            "edit" -> openRulesFile(sender)
            else -> throw WrongUsageException(getUsage(sender))
        }
    }

    override fun getTabCompletions(
        server: MinecraftServer,
        sender: ICommandSender,
        args: Array<out String>,
        @Nullable targetPos: BlockPos?
    ): MutableList<String> =
        if (args.size == 1) {
            getListOfStringsMatchingLastWord(args, *subcommands)
        } else {
            mutableListOf()
        }

    private fun emitReloadFeedback(sender: ICommandSender) {
        val report = StackUpUp.reload()
        sender.reply("${StackUpUpIds.COMMAND_LANG_ROOT}.reload.success")
        RuleFeedback.emitReloadErrors(report, sender::sendMessage)
        RuleFeedback.emitWarnings(report, sender::sendMessage)
    }

    private fun openRulesFile(sender: ICommandSender) {
        val file = RuleRuntimeCoordinator.getRulesFile()
        if (!file.exists()) {
            sender.reply("${StackUpUpIds.COMMAND_LANG_ROOT}.edit.missing", file.absolutePath)
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
            sender.reply("${StackUpUpIds.COMMAND_LANG_ROOT}.edit.success", file.absolutePath)
        } catch (e: IOException) {
            sender.reply("${StackUpUpIds.COMMAND_LANG_ROOT}.edit.failed", e.message ?: "unknown")
        }
    }

    private fun ICommandSender.reply(key: String, vararg args: Any) {
        sendMessage(TextComponentTranslation(key, *args))
    }

    private fun ICommandSender.replyUnsupportedOpen() {
        reply("${StackUpUpIds.COMMAND_LANG_ROOT}.edit.unsupported")
    }
}


