package io.alexjoest.stackupup.mixin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * late mixin 的 SRG / MCP 双名覆盖审计（结构检查，非行为验证）。
 *
 * 背景：`src/main/java/io/alexjoest/stackupup/mixin/late` 包下的 mixin 全部是 `remap = false`，Mixin 不查
 * refmap，注解里写的名字必须就是 class 文件里的真名。目标类是 mod 类时，只要注入目标（或 `@At` 的 INVOKE
 * 目标）是 Minecraft 成员，真名就有两种形态：reobf 过的生产 jar 里是 SRG 名（`func_xxx`），dev 环境经
 * SRG→MCP 反混淆后是 MCP 名。两边都得声明。只写 MCP 名 + `require = 0` 时生产环境静默不命中——
 * `RftoolsStorageScannerLimitMixin` 就这样失效了整个发布周期（输入槽放 >64 在下一帧被截断回 64，丢物）。
 *
 * **局限：本类只能验证声明形状，验证不了运行时是否真的命中。** 它读的是源文件文本与编译产物常量池，能证明
 * 两个名字都写进了注解、`func_` 形态的调用点目标都在，但无法证明生产环境里目标方法真的叫那个名字，也无法
 * 证明 `@At` 真的落在那条指令上——名字匹配发生在类加载期，JVM 单元测试碰不到。第三方 mod 一旦改变混淆形态
 * （未 reobf 的 dev-style jar、或换了映射），这里依旧全绿而运行时全不命中。真实验证只有两条路：实机行为
 * （放 >64 观察下一帧是否被截断/掉落），或对生产 jar 做 `javap -p -c <目标类>` 核对声明名与调用名。
 * 本轮已对 `rftools-1.12-7.73`、`EnderIO-CEu-5.5.1`、`ae2-uel-v0.56.7`、`endercore-0.5.83`、
 * `nuclearcraft-2o.9.7`、`IC2Classic-1.5.15`、`MysticalAgriculture-1.7.5` 做过该核对。
 */
class LateMixinSrgNameCoverageTest {

    @Test
    fun srgRewrittenTargets_shouldDeclareSrgAndMcpNames() {
        val expectations = listOf(
            // 生产 jar 声明 func_73660_a()V（覆写 net.minecraft.util.ITickable#update）
            DualName("rftools/RftoolsStorageScannerLimitMixin.java", "update", "func_73660_a"),
            // TileInventoryBase implements IInventory
            DualName("brandonscore/BrandonsCoreInventoryLimitMixin.java", "getInventoryStackLimit()I", "func_70297_j_()I"),
            // SimpleInventory implements INBTInventory extends IInventory
            DualName("cyclopscore/SimpleInventoryMixin.java", "getInventoryStackLimit()I", "func_70297_j_()I"),
            // ContainerAdapter implements IInventory
            DualName("ae2supergiant/ContainerAdapterMixin.java", "getInventoryStackLimit()I", "func_70297_j_()I"),
        )

        expectations.forEach { expected ->
            val code = stripBlockComments(lateMixinSource(expected.path))
            assertTrue(code.contains(expected.srg), "${expected.path} 必须声明 SRG 名 ${expected.srg}：生产环境真名")
            assertTrue(code.contains(expected.mcp), "${expected.path} 必须保留 MCP 名 ${expected.mcp}：dev 环境真名")
        }
    }

    @Test
    fun mcCallSiteTargets_shouldDeclareSrgAndMcpForms() {
        val noDrop = stripBlockComments(lateMixinSource("enderio/EnderIOInventoryNoDropMixin.java"))
        // javap -c EnderIO-CEu-5.5.1.jar 实证：contents.shrink → func_190918_g，spawnAsEntity → func_180635_a
        assertTrue(noDrop.contains("Lnet/minecraft/item/ItemStack;func_190918_g(I)V"), "缩栈调用点必须带 SRG 名")
        assertTrue(noDrop.contains("Lnet/minecraft/item/ItemStack;shrink(I)V"), "缩栈调用点必须保留 MCP 名")
        assertTrue(noDrop.contains("Lnet/minecraft/block/Block;func_180635_a("), "掉落调用点必须带 SRG 名")
        assertTrue(noDrop.contains("Lnet/minecraft/block/Block;spawnAsEntity("), "掉落调用点必须保留 MCP 名")

        val aeWriter = stripBlockComments(lateMixinSource("ae2/AppEngInternalAEInventoryWriterMixin.java"))
        // javap -c ae2-uel-v0.56.7.jar 实证：insertItem 与 extractItem 都以 func_190917_f 调 grow
        assertEquals(
            2,
            Regex("Lnet/minecraft/item/ItemStack;func_190917_f\\(I\\)V").findAll(aeWriter).count(),
            "insertItem 与 extractItem 两处都要覆盖 SRG 名",
        )
        assertEquals(
            2,
            Regex("Lnet/minecraft/item/ItemStack;grow\\(I\\)V").findAll(aeWriter).count(),
            "insertItem 与 extractItem 两处都要保留 MCP 名",
        )
    }

    @Test
    fun mcpOnlyTargets_shouldOnlyAppearForJarVerifiedNonOverrides() {
        // 「覆写 MC 成员 → 必须补 SRG 名」的方法名（映射取自 MCP stable 39 的 mcp-srg.srg）。
        // 刻意不含 getMaxStackSize：1.12.2 只有 ItemStack#getMaxStackSize（func_77976_d），它不是
        // TileEntity / Slot 的后代，mod 类里同名的 getMaxStackSize() 不是覆写（endercore
        // InventorySlot#getMaxStackSize 即此类），reobf 不会重命名。
        val mcpToSrg = mapOf(
            "update" to "func_73660_a",
            "setInventorySlotContents" to "func_70299_a",
            "getInventoryStackLimit" to "func_70297_j_",
            "getSlotStackLimit" to "func_75219_a",
        )
        // jar 实证的例外：这些 mixin 的 MCP-only 形状是正确的，补 SRG 名反而是噪声。
        // AbstractInventoryMachineEntity 并未 implements IInventory（只有内部 InventoryWrapper 实现），
        // 它的 setInventorySlotContents / getInventoryStackLimit 不是接口覆写，生产 jar 里就叫 MCP 名；
        // endercore 的 InventorySlot#getMaxStackSize 同理。javap -p 在生产 jar 上直接看到 MCP 声明。
        val jarVerifiedMcpOnly = setOf(
            "enderio/EnderIOInventoryNoDropMixin.java",
            "enderio/EnderIOMachineInventoryLimitMixin.java",
            "enderio/EnderIOSlottedInventoryLimitMixin.java",
            "enderio/EnderIOInventorySlotLimitMixin.java",
        )

        lateMixinSources()
            .filterNot { jarVerifiedMcpOnly.contains(it) }
            .forEach { relative ->
                val code = stripBlockComments(lateMixinSource(relative))
                injectionMethodNames(code).forEach { declared ->
                    mcpToSrg.forEach { (mcp, srg) ->
                        if (declared.contains(mcp) && !declared.contains(srg)) {
                            assertTrue(
                                code.contains(srg),
                                "$relative 用 MCP 名注入（$declared），生产环境真名是 $srg，必须两个都声明",
                            )
                        }
                    }
                }
            }
    }

    @Test
    fun callSiteTargetsOnMinecraftMembers_shouldCarrySrgVariants() {
        val sources = lateMixinSources()
        // 目录/文件枚举必须真的覆盖到 late mixin，否则下面的逐文件断言会空转成假绿。
        assertTrue(sources.size >= 30, "late mixin 源文件应至少 30 个，实际 ${sources.size}：枚举可能已失效")

        sources.forEach { relative ->
            val code = stripBlockComments(lateMixinSource(relative))
            val mcTargets = Regex("target\\s*=\\s*\"(Lnet/minecraft/[^\"]+)\"")
                .findAll(code)
                .map { it.groupValues[1] }
                .toList()
            if (mcTargets.isNotEmpty()) {
                assertTrue(
                    code.contains("func_"),
                    "$relative 的 @At 目标是 Minecraft 成员，mod 类里的该调用在生产环境是 SRG 名，" +
                        "必须同时给出 SRG 形态：$mcTargets",
                )
            }
        }
    }

    /** 只保留代码：类注释里为了记录历史会引用旧注解文本，不能让它们参与结构断言。 */
    private fun stripBlockComments(source: String): String = source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")

    private fun injectionMethodNames(source: String): List<String> = Regex("method\\s*=\\s*(\\{[^}]*}|\"[^\"]*\")")
        .findAll(source)
        .flatMap { match -> Regex("\"([^\"]+)\"").findAll(match.groupValues[1]).map { it.groupValues[1] } }
        .filterNot { it.startsWith("L") }
        .filterNot { it.contains('/') }
        .toList()

    private fun lateMixinSource(relativePath: String): String = String(
        Files.readAllBytes(Paths.get(LATE_ROOT, relativePath)),
        Charsets.UTF_8,
    )

    private fun lateMixinSources(): List<String> {
        val mixinRoot = Paths.get(LATE_ROOT)
        assertTrue(Files.isDirectory(mixinRoot), "late mixin 目录必须存在：$LATE_ROOT")
        return Files.walk(mixinRoot).use { stream ->
            stream
                .filter(Files::isRegularFile)
                .filter { it.toString().endsWith(".java") }
                .map { mixinRoot.relativize(it).toString().replace('\\', '/') }
                .sorted()
                .collect(java.util.stream.Collectors.toList())
        }
    }

    private data class DualName(val path: String, val mcp: String, val srg: String)

    private companion object {
        const val LATE_ROOT = "src/main/java/io/alexjoest/stackupup/mixin/late"
    }
}
