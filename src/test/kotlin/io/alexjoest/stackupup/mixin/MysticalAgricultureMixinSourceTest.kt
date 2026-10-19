package io.alexjoest.stackupup.mixin

import io.alexjoest.stackupup.bootstrap.StackUpUpMixinConnector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class MysticalAgricultureMixinSourceTest {
    @Test
    fun essenceReprocessorMixin_shouldPatchOnlyTheAbstractBaseGetter() {
        val configPath = Paths.get("src/main/resources", CAPACITY_CONFIG)
        assertTrue(Files.isRegularFile(configPath), "capacity late 配置必须存在：$CAPACITY_CONFIG")

        val config = String(Files.readAllBytes(configPath), Charsets.UTF_8)
        assertTrue(config.contains("\"package\": \"$PACKAGE\""), "package 必须指向 mysticalagriculture mixin 包")
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

        // 唯一目标：abstract 基类。transformer 现在也改写抽象类的具体 getter（两条路径落到同一份
        // 字节码，mixin 退化为 passthrough），6 个具体子类都没有自己的 getter，因此只打基类，
        // 不逐个子类重复登记。
        assertTrue(mixin.contains("targets = \"$TARGET_CLASS\""), "目标必须是 abstract 的 TileEssenceReprocessor")
        assertEquals(1, Regex("targets = ").findAll(mixin).count(), "只允许一个 @Mixin 目标；不得逐个子类登记")
        assertTrue(mixin.contains("@Pseudo"), "mod 未加载时目标类不在编译类路径，必须 Pseudo 跳过")
        assertTrue(mixin.contains("remap = false"), "mod 类成员不做映射重写")
        assertEquals(
            listOf("func_70297_j_()I", "getInventoryStackLimit()I"),
            annotationStringArray(mixin, "method"),
            "运行期 SRG 名与 dev 反混淆名都要登记，顺序保持稳定",
        )
        assertTrue(mixin.contains("@ModifyReturnValue"), "getter 体是 bipush 64; ireturn 立即数，只能用 @ModifyReturnValue")
        assertTrue(
            mixin.contains("StackLimitHooks.shouldBypassInventoryStackLimit(this)"),
            "必须走 fail-closed 容量 gate（黑名单 bypass）",
        )
        assertTrue(mixin.contains("StackLimitHooks.getInventoryCompatibilityStackSize(original)"), "64 应替换为兼容堆叠上限")
        assertTrue(mixin.contains("require = 0"), "目标缺失时必须静默跳过")
        assertFalse(mixin.contains("@Redirect"), "不得新增 @Redirect")
        assertFalse(mixin.contains("@Overwrite"), "不得使用 @Overwrite")
        // 纯 Java 约束：源文件不得引入 Kotlin 依赖（编译产物侧在下面按常量池再查一次）
        assertFalse(mixin.contains("import kotlin."), "mixin handler 必须保持纯 Java")
        assertFalse(mixin.contains("kotlin.collections"), "mixin handler 不得依赖 Kotlin 集合")
        assertFalse(mixin.contains("kotlin.ranges"), "mixin handler 不得依赖 Kotlin 范围")
        assertFalse(mixin.contains("kotlin.text"), "mixin handler 不得依赖 Kotlin 文本")

        // 字节码结构检查：编译产物必须真实携带注解与目标方法名（常量池 UTF8），防止源文件与编译产物脱节。
        val mixinClass = Class.forName("$PACKAGE.$MIXIN_CLASS")
        val classBytes = requireNotNull(mixinClass.getResourceAsStream("$MIXIN_CLASS.class")) {
            "无法读取 $MIXIN_CLASS.class"
        }.use { it.readBytes() }

        assertTrue(classBytes.containsAscii("Lorg/spongepowered/asm/mixin/Mixin;"), "编译产物应携带 @Mixin 注解")
        assertTrue(
            classBytes.containsAscii("Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;"),
            "编译产物应携带 @ModifyReturnValue 注入器",
        )
        assertTrue(classBytes.containsAscii("func_70297_j_()I"), "编译产物应携带 SRG 目标选择器")
        assertTrue(classBytes.containsAscii("getInventoryStackLimit()I"), "编译产物应携带反混淆目标选择器")
        assertTrue(classBytes.containsAscii(TARGET_CLASS), "编译产物应携带目标类名")
        assertTrue(classBytes.containsAscii("io/alexjoest/stackupup/StackLimitHooks"), "编译产物应引用 StackLimitHooks")
        assertFalse(classBytes.containsAscii("kotlin/jvm/internal"), "编译产物不得携带 Kotlin 运行期入口（纯 Java 结构检查）")
    }

    @Test
    fun mysticalAgricultureCapacityMixin_shouldBeRegisteredInCapacityTableOnly() {
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
                String(Files.readAllBytes(Paths.get("src/main/resources", "mixins.stackupup.baseline.early.json")), Charsets.UTF_8),
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

    private fun jsonStringArray(json: String, key: String): List<String> = quotedStrings(
        Regex("\"$key\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL).find(json)?.groupValues?.get(1),
    )

    /** Java 注解里的数组是 `method = {"a", "b"}`（属性名不加引号），不是 JSON 的 `"method": [...]`。 */
    private fun annotationStringArray(source: String, key: String): List<String> = quotedStrings(
        Regex("\\b$key\\s*=\\s*\\{(.*?)}", RegexOption.DOT_MATCHES_ALL).find(source)?.groupValues?.get(1),
    )

    private fun quotedStrings(body: String?): List<String> {
        if (body == null) {
            return emptyList()
        }
        return Regex("\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
    }

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
        const val CAPACITY_CONFIG = "mixins.stackupup.capacity.late.mysticalagriculture.json"
        const val PACKAGE = "io.alexjoest.stackupup.mixin.late.mysticalagriculture"
        const val MIXIN_CLASS = "MysticalAgricultureInventoryLimitMixin"
        const val MOD_ID = "mysticalagriculture"
        const val TARGET_CLASS = "com.blakebr0.mysticalagriculture.tileentity.reprocessor.TileEssenceReprocessor"
    }
}
