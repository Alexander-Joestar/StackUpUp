package io.alexjoest.stackupup.mixin.early;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.entity.item.EntityMinecartContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.InventoryMerchant;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.tileentity.TileEntityShulkerBox;

/**
 * 原版 {@code getInventoryStackLimit()I} 安全目标的编译期显式表。
 *
 * <p>登记表（docs/agent/t2b-原版目标表.md §2）与本集合、以及
 * {@link VanillaInventoryLimitMixin} 的 {@code @Mixin} 列表三者必须双向一致，
 * 由登记护栏测试 VanillaInventoryTargetsGuardTest 校验。表内目标全部为自洽写入面：
 * setter 落盘前重读同一上限夹取，或朴素写入不夹取（不截断即不吞物），原生返回值均为 64，
 * 因此替换返回值时不需要运行时 {@code original == 64} 哨兵判断。
 *
 * <p>表外排除（见 t2b §3）：InventoryLargeChest（转发上箱，条件断链）、TileEntityBeacon
 * （主动收紧返回 1）、ContainerEnchantment 匿名子类（override 旁路基类 patch，自洽于 64）。
 */
public final class VanillaInventoryTargets {
    private VanillaInventoryTargets() {
    }

    /** 目标方法完整 descriptor：无参变体，与 {@code getSlotStackLimit()I}、带参重载区分。 */
    public static final String METHOD_DESCRIPTOR = "getInventoryStackLimit()I";

    /** 安全目标集合：全部为自洽写入面，原生返回 64。 */
    public static final Set<Class<?>> TARGETS = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            TileEntityDispenser.class,
            TileEntityChest.class,
            TileEntityFurnace.class,
            TileEntityBrewingStand.class,
            TileEntityHopper.class,
            TileEntityShulkerBox.class,
            EntityMinecartContainer.class,
            InventoryPlayer.class,
            InventoryBasic.class,
            InventoryMerchant.class,
            InventoryCrafting.class,
            InventoryCraftResult.class
        ))
    );

    /** 排除集合：登记为「不得入表」的原版实现者，护栏测试断言其既不在 TARGETS 也不在 mixin 源中。 */
    public static final Set<Class<?>> EXCLUDED = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            net.minecraft.inventory.InventoryLargeChest.class,
            net.minecraft.tileentity.TileEntityBeacon.class
        ))
    );
}
