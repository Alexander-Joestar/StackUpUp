package io.alexjoest.stackupup.mixin.late.brandonscore;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.brandon3055.brandonscore.blocks.TileInventoryBase", remap = false)
abstract class BrandonsCoreInventoryLimitMixin {

    // TileInventoryBase 字段：protected int stackLimit = 64;
    @Shadow(remap = false)
    private int stackLimit;

        /**
     * 构造期把 stackLimit 字段扩到全局兼容上限，这样：
     * - getInventoryStackLimit() 直接返回扩展值
     * - setInventorySlotContents 里的截断检查
     *   stack.count > getInventoryStackLimit()
     *   自动用扩展值做边界，不会错误截断。
     */
    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void stackupup$expandStackLimitOnInit(CallbackInfo ci) {
        if (StackLimitHooks.shouldBypassInventoryStackLimit(this)) {
            return;
        }
        this.stackLimit = StackLimitHooks.getInventoryCompatibilityStackSize(this, this.stackLimit);
    }

    /**
     * {@code getInventoryStackLimit()} is a {@code net.minecraft.inventory.IInventory} member, so in
     * the reobfuscated release jar it is declared as {@code func_70297_j_()I} and the MCP name only
     * exists in the dev environment (SRG&rarr;MCP deobfuscation). {@code remap = false} bypasses the
     * refmap, so both names must be declared or the hook silently never runs in production.
     * {@code require = 0} is kept on purpose: the two names always cover both environments, and a mod
     * version that stops declaring the getter here (inheriting it instead) should degrade to "no extra
     * capacity", not to a startup crash.
     */
    @ModifyReturnValue(method = {"func_70297_j_()I", "getInventoryStackLimit()I"}, at = @At("RETURN"), require = 0)
    private int stackupup$expandDefaultInventoryLimit(int original) {
        if (StackLimitHooks.shouldBypassInventoryStackLimit(this)) {
            return original;
        }
        return StackLimitHooks.getInventoryCompatibilityStackSize(original);
    }
}
