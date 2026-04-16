package pl.asie.stackup.mixin.early

import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.renderer.RenderItem
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Redirect
import pl.asie.stackup.client.StackUpClientHelpers

@Mixin(RenderItem::class)
abstract class RenderItemMixin {
    @Redirect(
        method = ["renderItemOverlayIntoGUI", "func_180453_a"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I"
        )
    )
    private fun drawLargeCount(fr: FontRenderer, text: String, x: Float, y: Float, color: Int): Int {
        return StackUpClientHelpers.drawItemCountWithShadow(fr, text, x, y, color)
    }
}
