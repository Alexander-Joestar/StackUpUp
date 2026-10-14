package io.alexjoest.stackupup.mixin.early;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.items.wrapper.EntityEquipmentInvWrapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = EntityEquipmentInvWrapper.class, remap = false)
abstract class EntityEquipmentInventoryLimitMixin {
    @Shadow @Final protected EntityLivingBase entity;

    @ModifyReturnValue(method = "getSlotLimit(I)I", at = @At("RETURN"))
    private int stackupup$resolveEntityCapacity(int original, int slot) {
        return StackLimitHooks.getEquipmentInventoryStackSize(
            (EntityEquipmentInvWrapper) (Object) this, this.entity, original
        );
    }
}
