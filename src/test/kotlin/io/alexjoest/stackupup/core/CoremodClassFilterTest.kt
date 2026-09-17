package io.alexjoest.stackupup.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CoremodClassFilterTest {
    @Test
    fun shouldSkipUnrelatedRuntimeClasses() {
        assertEquals(true, CoremodClassFilter.shouldSkip("kotlin/jvm/internal/Intrinsics"))
        assertEquals(true, CoremodClassFilter.shouldSkip("java/lang/String"))
        assertEquals(true, CoremodClassFilter.shouldSkip("javax/annotation/Nullable"))
        assertEquals(true, CoremodClassFilter.shouldSkip("sun/misc/Unsafe"))
        assertEquals(true, CoremodClassFilter.shouldSkip("jdk/internal/loader/ClassLoaders"))
    }

    @Test
    fun shouldSkipLoaderInfrastructureClasses() {
        // FML/launchwrapper 属于装载器基础设施：让它们进入 DynamicCompatTransformer 会在
        // FML 错误处理路径上被二次变换。前缀只覆盖基础设施包，不覆盖 net/minecraft/ 游戏类。
        assertEquals(true, CoremodClassFilter.shouldSkip("net/minecraftforge/fml/common/Loader"))
        assertEquals(true, CoremodClassFilter.shouldSkip("net/minecraftforge/fml/relauncher/IFMLLoadingPlugin"))
        assertEquals(true, CoremodClassFilter.shouldSkip("net/minecraft/launchwrapper/LaunchClassLoader"))
        assertEquals(true, CoremodClassFilter.shouldSkip("net/minecraft/launchwrapper/injector/VanillaTweakInjector"))
    }

    @Test
    fun shouldNotSkipGameAndModClasses() {
        assertEquals(false, CoremodClassFilter.shouldSkip("net/minecraft/item/ItemStack"))
        assertEquals(false, CoremodClassFilter.shouldSkip("net/minecraft/tileentity/TileEntityChest"))
        assertEquals(
            false,
            CoremodClassFilter.shouldSkip("com/raoulvdberge/refinedstorage/apiimpl/network/node/NetworkNodeStorageMonitor"),
        )
        assertEquals(false, CoremodClassFilter.shouldSkip("gregtech/api/items/metaitem/MetaItem"))
    }
}
