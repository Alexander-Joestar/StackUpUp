package io.alexjoest.stackupup.mixin.early;

import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.PacketThreadUtil;
import net.minecraft.network.play.client.CPacketCreativeInventoryAction;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayServer.class)
abstract class NetHandlerPlayServerMixin {
    @Shadow public EntityPlayerMP player;

    @Shadow private int itemDropThreshold;

    @Inject(
        method = "processCreativeInventoryAction(Lnet/minecraft/network/play/client/CPacketCreativeInventoryAction;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void stackupup$useDynamicCreativeValidation(CPacketCreativeInventoryAction packetIn, CallbackInfo ci) {
        // 原版这里把创造模式包体数量硬限制在 64。
        // 仅改常量会退化成“受全局兼容上限约束”，仍然无法接受规则抬高后的真实物品上限。
        PacketThreadUtil.checkThreadAndEnqueue(packetIn, (NetHandlerPlayServer) (Object) this, this.player.getServerWorld());

        if (!this.player.interactionManager.isCreative()) {
            ci.cancel();
            return;
        }

        boolean isDropAction = packetIn.getSlotId() < 0;
        ItemStack stack = packetIn.getStack();
        this.stackupup$sanitizeBlockEntityTag(stack);

        boolean isValidStack = StackLimitHooks.isValidCreativeStackPacket(stack);

        if (this.stackupup$tryApplyCreativeSlotUpdate(packetIn.getSlotId(), stack, isValidStack)) {
            ci.cancel();
            return;
        }

        if (isDropAction) {
            this.stackupup$tryDropCreativeStack(stack, isValidStack);
        }

        ci.cancel();
    }

    private boolean stackupup$tryApplyCreativeSlotUpdate(int slotId, ItemStack stack, boolean isValidStack) {
        boolean isInventorySlot = slotId >= 1 && slotId <= 45;
        if (!isInventorySlot || !isValidStack) {
            return false;
        }

        this.player.inventoryContainer.putStackInSlot(slotId, stack.isEmpty() ? ItemStack.EMPTY : stack);
        this.player.inventoryContainer.setCanCraft(this.player, true);
        return true;
    }

    private void stackupup$tryDropCreativeStack(ItemStack stack, boolean isValidStack) {
        if (!isValidStack || this.itemDropThreshold >= 200) {
            return;
        }

        this.itemDropThreshold += 20;
        EntityItem entityItem = this.player.dropItem(stack, true);
        if (entityItem != null) {
            entityItem.setAgeToCreativeDespawnTime();
        }
    }

    private void stackupup$sanitizeBlockEntityTag(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTagCompound() || !stack.getTagCompound().hasKey("BlockEntityTag", 10)) {
            return;
        }

        NBTTagCompound blockEntityTag = stack.getTagCompound().getCompoundTag("BlockEntityTag");
        if (!blockEntityTag.hasKey("x") || !blockEntityTag.hasKey("y") || !blockEntityTag.hasKey("z")) {
            return;
        }

        BlockPos pos = new BlockPos(
            blockEntityTag.getInteger("x"),
            blockEntityTag.getInteger("y"),
            blockEntityTag.getInteger("z")
        );
        TileEntity tileEntity = this.player.world.getTileEntity(pos);
        if (tileEntity == null) {
            return;
        }

        NBTTagCompound sanitizedTag = tileEntity.writeToNBT(new NBTTagCompound());
        sanitizedTag.removeTag("x");
        sanitizedTag.removeTag("y");
        sanitizedTag.removeTag("z");
        stack.setTagInfo("BlockEntityTag", sanitizedTag);
    }
}
