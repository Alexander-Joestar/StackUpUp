package io.alexjoest.stackupup.mixin.late;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.alexjoest.stackupup.StackLimitHooks;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * GregTech CEu 2.8.7-beta `MetaPrefixItem.getItemStackLimit(ItemStack)I`（mods-under-test/GregTech/
 * src/main/java/gregtech/api/items/materialitem/MetaPrefixItem.java:145-147）直呼面归一与结果缓存增强。
 * <p>
 * 事实：MetaPrefixItem 是 StandardMetaItem 的具体子类（:44），覆写 `getItemStackLimit` 按 OrePrefix
 * 返回——prefix 为 null 时 64（:146），否则 `prefix.maxStackSize`（默认 64，plateDense=7 等显式覆盖）。
 * 该覆写有独立字节码，early `ItemMixin`（只改写基类 Item 方法体）与 `GregTechMetaItemMixin`
 * （只改写 MetaItem 方法体）均不覆盖本类，需独立 mixin。
 * <p>
 * 逻辑与 `GregTechMetaItemMixin` 完全同构（含额外的内容键缓存短路）：
 * 规则命中返回解析值并写缓存；未命中返回 original（= 按 OrePrefix 的原始上限），
 * plateDense=7 等显式覆盖语义无损；基线解析期间 bypass 直接返回 original，无递归。
 *
 * @Pseudo + require=0：GT 未加载时目标类不在编译类路径，静默跳过。
 */
@Pseudo
@Mixin(targets = "gregtech.api.items.materialitem.MetaPrefixItem", remap = false)
abstract class GregTechMetaPrefixItemMixin {
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
