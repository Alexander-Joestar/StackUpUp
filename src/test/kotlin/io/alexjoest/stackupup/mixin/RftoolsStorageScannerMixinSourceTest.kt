package io.alexjoest.stackupup.mixin

import io.alexjoest.stackupup.bootstrap.StackUpUpMixinConnector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * RFTools 存储扫描仪限值参数注入的登记与源结构检查（结构检查，非行为验证）。
 *
 * 放开接口 default 方法后扫描仪输入槽可以超过 64，而 `update()` 把余量写回槽位时用硬编码 64 作为
 * `InventoryHelper.setInventorySlotContents(stackLimit, index, stack)` 的限值参数，会就地截断
 * （`stack.setCount(stackLimit)`），把余量丢掉。本 mixin 用 `@ModifyArg` 把该限值换成 receiver 自己的
 * `getInventoryStackLimit()`；该 getter 已经承载扩展开关与容量黑名单，所以这里不再叠加开关判断。
 *
 * **这些断言只能验证「声明形状」，验证不了运行时是否真的命中。** 本类的检查对象是源文件文本与编译产物
 * 常量池：它能证明 `func_73660_a`/`update` 两个名字都写进了注解、`require = 1` 没有退回 0，却无法证明
 * 生产环境里目标方法真的叫 `func_73660_a`、也无法证明调用点在 jar 里真的落在 `update` 内。Mixin 的名字
 * 匹配发生在类加载期，JVM 单元测试碰不到那条路径；上一个 bug 正是「结构检查全绿、运行时一次都没生效」。
 * 真实验证只有两条路：实机放入 >64 的物品观察下一帧是否被截断，或对生产 jar 做
 * `javap -p -c mcjty.rftools.blocks.storagemonitor.StorageScannerTileEntity`（本轮已做，见
 * `RftoolsStorageScannerLimitMixin` 的类注释）。
 */
class RftoolsStorageScannerMixinSourceTest {
    @Test
    fun storageScannerMixin_shouldReplaceTheHardcodedLimitArgumentOnly() {
        val configPath = Paths.get("src/main/resources", CAPACITY_CONFIG)
        assertTrue(Files.isRegularFile(configPath), "capacity late 配置必须存在：$CAPACITY_CONFIG")

        val config = String(Files.readAllBytes(configPath), Charsets.UTF_8)
        assertTrue(config.contains("\"package\": \"$PACKAGE\""), "package 必须指向 rftools mixin 包")
        assertTrue(config.contains("\"refmap\": \"\${refmap}\""), "必须声明 refmap 占位符")
        assertTrue(config.contains("\"required\": false"), "可选 mod 配置必须 required=false")
        assertEquals(
            listOf(MIXIN_CLASS),
            jsonStringArray(config, "mixins"),
            "capacity 配置只允许登记这一个 mixin",
        )

        val sourceRelative = "src/main/java/" + PACKAGE.replace('.', '/') + "/$MIXIN_CLASS.java"
        assertTrue(Files.isRegularFile(Paths.get(sourceRelative)), "mixin 源必须存在于 package 对应路径：$sourceRelative")
        val mixin = String(Files.readAllBytes(Paths.get(sourceRelative)), Charsets.UTF_8)

        assertTrue(mixin.contains("@Pseudo"), "mod 未加载时目标类不在编译类路径，必须 Pseudo 跳过")
        assertTrue(
            mixin.contains("@Mixin(targets = \"$TARGET_CLASS\", remap = false)"),
            "目标必须是 StorageScannerTileEntity，且不做成员映射重写",
        )
        assertEquals(1, Regex("targets = ").findAll(mixin).count(), "只允许一个 @Mixin 目标")
        assertTrue(mixin.contains("@ModifyArg"), "改调用参数只能用 @ModifyArg")
        assertFalse(mixin.contains("@Redirect"), "仓库禁止新增 @Redirect")
        assertFalse(mixin.contains("@Overwrite"), "不得使用 @Overwrite")
        assertTrue(mixin.contains("index = 0"), "要改的是第一个参数（stackLimit），不是 index")
        assertTrue(
            mixin.contains("Lmcjty/lib/container/InventoryHelper;setInventorySlotContents(IILnet/minecraft/item/ItemStack;)V"),
            "注入点必须是 InventoryHelper.setInventorySlotContents(int stackLimit, int index, ItemStack)",
        )
        assertTrue(
            mixin.contains("method = {\"func_73660_a\", \"update\"}"),
            "生产环境声明名是 SRG func_73660_a（ITickable#update 覆写被 reobf），dev 是 MCP update；" +
                "remap=false 不走 refmap，必须两个都声明",
        )
        // 负向断言只看代码、不看注释：类注释里为了记录历史会提到旧的 MCP-only 形状与 require 0。
        val mixinCode = mixin.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        assertFalse(
            Regex("method = \"update\"").containsMatchIn(mixinCode),
            "只声明 MCP 名会在生产环境静默不命中——本轮实机丢物的根因",
        )
        assertTrue(mixinCode.contains("require = 1"), "两个名字至少命中一个；一个都命中不了必须报错，不得静默跳过")
        assertFalse(mixinCode.contains("require = 0"), "不得把 require 退回 0（回到静默跳过）")
        assertTrue(
            mixin.contains("((IInventory) (Object) this).getInventoryStackLimit()"),
            "限值必须来自 receiver 自己的 getter（该 getter 已承载扩展开关与黑名单）",
        )
        assertFalse(mixin.contains("import kotlin."), "mixin handler 必须保持纯 Java")
        assertFalse(mixin.contains("StackUpUpConfig"), "不得在 mixin 里直接读业务配置，扩展开关由 getter 承担")

        // 字节码结构检查：编译产物必须真实携带注解与注入点描述符（常量池 UTF8）。
        val mixinClass = Class.forName("$PACKAGE.$MIXIN_CLASS")
        val classBytes = requireNotNull(mixinClass.getResourceAsStream("$MIXIN_CLASS.class")) {
            "无法读取 $MIXIN_CLASS.class"
        }.use { it.readBytes() }

        assertTrue(classBytes.containsAscii("Lorg/spongepowered/asm/mixin/Mixin;"), "编译产物应携带 @Mixin 注解")
        assertTrue(
            classBytes.containsAscii("Lorg/spongepowered/asm/mixin/injection/ModifyArg;"),
            "编译产物应携带 @ModifyArg 注入器",
        )
        assertTrue(classBytes.containsAscii(TARGET_CLASS), "编译产物应携带目标类名")
        assertTrue(classBytes.containsAscii("mcjty/lib/container/InventoryHelper"), "编译产物应携带注入点 owner")
        assertTrue(classBytes.containsAscii("func_73660_a"), "编译产物常量池应携带 SRG 方法名（生产环境真实声明名）")
        assertTrue(classBytes.containsAscii("update"), "编译产物常量池应同时携带 MCP 方法名（dev 环境声明名）")
        assertTrue(classBytes.containsAscii("getInventoryStackLimit"), "编译产物应携带 receiver getter 调用")
        assertFalse(classBytes.containsAscii("kotlin/jvm/internal"), "编译产物不得携带 Kotlin 运行期入口（纯 Java 结构检查）")
    }

    @Test
    fun rftoolsCapacityMixin_shouldBeRegisteredInCapacityTableOnly() {
        val connector = StackUpUpMixinConnector()

        assertTrue(
            connector.capacityModules.any { it.config == CAPACITY_CONFIG && it.modId == MOD_ID },
            "capacity 配置必须以 modid=$MOD_ID 登记在 connector capacity 模块表",
        )
        assertFalse(
            (connector.modules + connector.classicModules).any { it.config == CAPACITY_CONFIG },
            "不得登记为 ordinary late 模块（容量 lane 之外）",
        )
        assertFalse(
            jsonStringArray(
                String(Files.readAllBytes(Paths.get("src", "main", "resources", "mixins.stackupup.baseline.early.json")), Charsets.UTF_8),
                "mixins",
            ).contains(MIXIN_CLASS),
            "baseline.early 不得登记该 mixin",
        )
        assertTrue(
            connector.shouldQueue(CAPACITY_CONFIG, { it == MOD_ID }, connector.capacityModules),
            "modid=$MOD_ID 存在时配置应入队",
        )
        assertFalse(
            connector.shouldQueue(CAPACITY_CONFIG, { false }, connector.capacityModules),
            "modid 缺失时配置应跳过",
        )
    }

    private fun jsonStringArray(json: String, key: String): List<String> = Regex("\"$key\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
        .find(json)
        ?.groupValues
        ?.get(1)
        ?.let { body -> Regex("\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList() }
        .orEmpty()

    private fun ByteArray.containsAscii(value: String): Boolean {
        if (isEmpty()) {
            return false
        }

        val target = value.encodeToByteArray()
        val lastIndex = size - target.size
        if (lastIndex < 0) {
            return false
        }

        for (index in 0..lastIndex) {
            var matches = true
            for (offset in target.indices) {
                if (this[index + offset] != target[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) {
                return true
            }
        }
        return false
    }

    private companion object {
        const val CAPACITY_CONFIG = "mixins.stackupup.capacity.late.rftools.json"
        const val PACKAGE = "io.alexjoest.stackupup.mixin.late.rftools"
        const val MIXIN_CLASS = "RftoolsStorageScannerLimitMixin"
        const val MOD_ID = "rftools"
        const val TARGET_CLASS = "mcjty.rftools.blocks.storagemonitor.StorageScannerTileEntity"
    }
}
