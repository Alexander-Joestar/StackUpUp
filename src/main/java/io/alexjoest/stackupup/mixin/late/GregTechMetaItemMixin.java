package io.alexjoest.stackupup.mixin.late;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * GregTech CEu 2.8.7-beta `MetaItem.getItemStackLimit(ItemStack)I`（mods-under-test/GregTech/
 * src/main/java/gregtech/api/items/metaitem/MetaItem.java:323-330）直呼面归一与结果缓存增强。
 *
 * 事实：MetaItem 是抽象 Item 子类（:107），`getItemStackLimit` 按 meta 区分——meta 无对应
 * MetaValueItem 时返回 64（:327），否则返回 `MetaValueItem.getMaxStackSize`（per-meta 可覆盖，
 * 默认 `maxStackSize = 64` :781，电池 8/工具 1 等显式覆盖）。early `ItemMixin` 只改写基类
 * `Item.getItemStackLimit` 的方法体，对 MetaItem 的覆写（独立字节码）不生效；`ItemStackMixin`
 * 覆盖 `getMaxStackSize()` 调用点层。本 mixin 补直呼面：`item.getItemStackLimit(stack)` 直接调用
 * 返回与 ItemStack 层一致的动态上限。
 *
 * 逻辑同 `ItemMixin` 且额外先查内容键缓存短路（`lookupResolvedItemLimit`——ItemMixin 本身无此步骤）：
 * 规则命中返回解析值并写缓存；未命中时
 * `applyDynamicStackLimit` 返回 original（= MetaItem 自身 per-meta 值），per-meta 语义无损
 * （电池 8/工具 1 等显式覆盖原样保留）。基线解析期间 `shouldBypassDynamicItemRules()` 为真，
 * 直接返回 original 且不写缓存，无递归、无双重应用。
 *
 * 机器/总线写入路径已由 ForgeItemHandlerLimitMixin 覆盖，本 mixin 不动。@Pseudo + require=0：
 * GT 未加载时目标类不在编译类路径，静默跳过。
 */
@Pseudo
@Mixin(targets = "gregtech.api.items.metaitem.MetaItem", remap = false)
abstract class GregTechMetaItemMixin {
    @ModifyReturnValue(
        method = "getItemStackLimit(Lnet/minecraft/item/ItemStack;)I",
        remap = false,
        require = 0,
        at = @At("RETURN")
    )
    private int stackupup$applyRules(int original, ItemStack stack) {
        if (StackLimitHooks.shouldBypassDynamicItemRules()) {
            return original;
        }
        Integer cached = StackLimitHooks.lookupResolvedItemLimit(stack);
        if (cached != null) {
            return cached;
        }
        int resolved = StackLimitHooks.applyDynamicStackLimit(stack, original);
        return StackLimitHooks.cacheResolvedItemLimit(stack, resolved);
    }
}
