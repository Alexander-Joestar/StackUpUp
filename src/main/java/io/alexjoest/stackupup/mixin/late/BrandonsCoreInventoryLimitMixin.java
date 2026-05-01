package io.alexjoest.stackupup.mixin.late;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.brandon3055.brandonscore.blocks.TileInventoryBase", remap = false)
abstract class BrandonsCoreInventoryLimitMixin {
    @Unique
    private static final int VANILLA_STACK_LIMIT = 64;

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
        this.stackLimit = StackLimitHooks.getCompatibilityStackSize();
    }

    @ModifyReturnValue(method = "getInventoryStackLimit", at = @At("RETURN"), require = 0)
    private int stackupup$expandDefaultInventoryLimit(int original) {
        return original == VANILLA_STACK_LIMIT ? StackLimitHooks.getCompatibilityStackSize() : original;
    }
}
