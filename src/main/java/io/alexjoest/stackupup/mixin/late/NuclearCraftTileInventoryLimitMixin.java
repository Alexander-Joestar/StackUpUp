package io.alexjoest.stackupup.mixin.late;

import io.alexjoest.stackupup.StackLimitHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * NuclearCraft 1.12.2 库存容量单一事实源收敛（修正记录 §3.9）。
 *
 * NC 的 `nc.tile.inventory.ITileInventory` 以接口 default 提供 `getInventoryStackLimit()=64`
 * （mods-under-test/NuclearCraft/src/main/java/nc/tile/inventory/ITileInventory.java:95-97），
 * 全部夹取点动态读它：`setInventorySlotContents` 写前截断（:78-80）、
 * `nc.tile.internal.inventory.ItemHandler#getSlotLimit`（:157-159）与 vanilla GUI Slot 跟随。
 *
 * 为什么不直接 patch 接口 default：Mixin 0.8.7（mixinbooter 11.13 内嵌）对「类 mixin 注入接口」在
 * 应用期抛 InvalidMixinException（SubType.Standard.validateTarget 的 target type mismatch），
 * 而「接口 mixin + 注入器」形态在 Java 8 toolchain 下会把 handler 转成 private+synthetic 并合并进
 * v52 接口，JVM 拒绝（private 接口方法需 class file >= 53）——字节码证据见 §3.9。
 *
 * 因此采用「具体实现类链上的公共基类」最小方案：四个直接实现 ITileInventory 且未覆写
 * getInventoryStackLimit 的抽象基类各合并一个具体覆写。深层显式覆写（TileDistributor*、
 * TilePassiveAbstract、TileDummy、fission 单元等）与 TileBin（vanilla IInventory）保持原语义。
 * 方法体只替换「默认 64」这一广告值，不触碰写入副作用（守恒审计只观察，不修正）。
 */
@Pseudo
@Mixin(
    targets = {
        "nc.tile.inventory.TileInventory",
        "nc.tile.fluid.TileFluidInventory",
        "nc.tile.energy.TileEnergyInventory",
        "nc.tile.energyFluid.TileEnergyFluidInventory"
    },
    remap = false
)
abstract class NuclearCraftTileInventoryLimitMixin {
    public int getInventoryStackLimit() {
        return StackLimitHooks.getCompatibilityStackSize();
    }
}
