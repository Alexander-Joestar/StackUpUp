package pl.asie.stackup.mixin.early

import net.minecraft.network.NetHandlerPlayServer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.Constant
import org.spongepowered.asm.mixin.injection.ModifyConstant
import pl.asie.stackup.StackUpHelpers

@Mixin(NetHandlerPlayServer::class)
abstract class NetHandlerPlayServerMixin {
    @ModifyConstant(
        method = ["processCreativeInventoryAction", "func_147344_a"],
        constant = [Constant(intValue = 64)]
    )
    private fun replaceCreativeLimit(original: Int): Int = StackUpHelpers.getMaxStackSize()
}
