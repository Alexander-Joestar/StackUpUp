package pl.asie.stackup

import net.minecraft.command.CommandBase
import net.minecraft.command.CommandException
import net.minecraft.command.ICommandSender
import net.minecraft.command.WrongUsageException
import net.minecraft.server.MinecraftServer
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.fml.common.registry.ForgeRegistries

class CommandStackUp : CommandBase() {
    override fun getName(): String = "stackup"

    override fun getUsage(sender: ICommandSender): String = "/stackup [reload]"

    @Throws(CommandException::class)
    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<out String>) {
        if (args.size >= 1 && args[0] == "reload") {
            StackUp.reload(ForgeRegistries.ITEMS)
            sender.sendMessage(TextComponentString("Reloaded!"))
        } else {
            throw WrongUsageException("/stackup [reload]")
        }
    }
}
