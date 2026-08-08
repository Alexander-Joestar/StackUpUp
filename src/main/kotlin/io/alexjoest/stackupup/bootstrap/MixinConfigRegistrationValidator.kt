package io.alexjoest.stackupup.bootstrap

import com.google.gson.JsonParser
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

/**
 * Mixin 配置注册双向校验（T14.5 停止条件 3「配置未注册无检测」的收敛实现）。
 *
 * 目标：让「JSON 有类但 loader 不注册 / loader 注册但 JSON 或类缺失 / 类存在但未登记」在启动期可定位，
 * 而不是静默失效。校验方向：
 * - 正向（运行时）：loader 注册的每个配置必须有对应 JSON 资源，且 JSON 列出的每个 mixin 类必须存在并带 @Mixin 注解；
 * - 反向-配置级（仅 dev/test 可枚举）：资源目录中实际存在的 JSON 必须被 loader 注册（jar 内无法枚举目录，运行时不做）；
 * - 反向-类级（测试枚举源码）：mixin 包内带 @Mixin 的类必须登记在某个 JSON。
 *
 * 关键约束：@Mixin 是 CLASS retention（mixinbooter-10.7.jar 内嵌 Mixin 的 javap 证据），运行时反射不可见，
 * 必须读类字节用 ASM 检查——此时注解落在 ClassNode.invisibleAnnotations。
 *
 * 本对象只做校验与日志，不持有状态、不改变装载决策；「核心配置 fail-fast 抛异常」由 [requireCoreConfigValid] 提供；
 * optional 层（late）只 ERROR 记录不中止——MixinBooter 的 late loader 循环对抛出的异常整体中断
 * （LoadControllerMixin 字节码/sources 证据），late 层抛异常会连带中止全部剩余 late 配置装载。
 */
object MixinConfigRegistrationValidator {
    private val logger: Logger = LogManager.getLogger("stackupup.mixin.config")

    private const val MIXIN_ANNOTATION_DESCRIPTOR: String = "Lorg/spongepowered/asm/mixin/Mixin;"

    /** 单个校验问题；toString 保持单行机器可读格式（config=...: ...）。 */
    data class Problem(val config: String, val message: String) {
        override fun toString(): String = "config=$config: $message"
    }

    /**
     * 校验单个配置：JSON 资源存在，且其中登记的每个 mixin 类都存在并带 @Mixin 注解。
     *
     * @param configName JSON 资源名（与 loader 注册名一致，如 mixins.stackupup.early.json）。
     */
    fun validateConfig(configName: String, classLoader: ClassLoader): List<Problem> {
        val jsonText = classLoader.getResourceAsStream(configName)?.use { input ->
            String(input.readBytes(), Charsets.UTF_8)
        } ?: return listOf(Problem(configName, "JSON resource missing - loader registered a config without backing resource"))
        return validateJson(configName, jsonText, classLoader)
    }

    /** 批量校验（late loader 启动期使用）。 */
    fun validateConfigs(configNames: Collection<String>, classLoader: ClassLoader): List<Problem> = configNames.flatMap { validateConfig(it, classLoader) }

    /**
     * 校验给定 JSON 文本中登记的 mixin 类（测试可注入合成 JSON 构造未注册场景）。
     */
    fun validateJson(configName: String, jsonText: String, classLoader: ClassLoader): List<Problem> {
        // 兼容 classpath 上的 gson 2.8.0（Forge 自带）与显式依赖 2.8.6：统一走 JsonParser().parse 实例 API。
        val json = runCatching { JsonParser().parse(jsonText).asJsonObject }.getOrNull()
            ?: return listOf(Problem(configName, "JSON is not a valid object"))
        val packageName = runCatching { json.getAsJsonPrimitive("package").asString }.getOrNull()
            ?: return listOf(Problem(configName, "JSON has no 'package' key"))
        val classNames = buildList {
            for (key in listOf("mixins", "client", "server")) {
                if (!json.has(key)) {
                    continue
                }
                val array = runCatching { json.getAsJsonArray(key) }.getOrNull() ?: continue
                for (element in array) {
                    if (element.isJsonPrimitive) {
                        add(element.asString)
                    }
                }
            }
        }
        return buildList {
            for (name in classNames) {
                val binaryName = if (packageName.isEmpty()) name else "$packageName.$name"
                if (!hasClassBytes(binaryName, classLoader)) {
                    add(Problem(configName, "mixin class '$binaryName' listed in JSON but class bytes not found on classpath"))
                } else if (!hasMixinAnnotation(binaryName, classLoader)) {
                    add(Problem(configName, "mixin class '$binaryName' exists but has no @Mixin annotation (checked via class bytes, CLASS retention)"))
                }
            }
        }
    }

    /**
     * 核心（early）配置门（T14.5 停止条件 1）：配置无效时按 ERROR 记录每个问题并抛异常 fail-fast，
     * 不再让核心配置静默不装载或静默失效。
     */
    fun requireCoreConfigValid(configName: String, classLoader: ClassLoader) {
        val problems = validateConfig(configName, classLoader)
        if (problems.isEmpty()) {
            return
        }
        logProblems(problems)
        throw IllegalStateException(
            "StackUpUp core mixin config '$configName' is invalid: ${problems.joinToString("; ")}",
        )
    }

    /**
     * 反向-配置级核对：给定实际存在的配置文件集合，返回其中未被 loader 注册的。
     * jar 内无法枚举资源目录，该方向只在 dev/test 用文件系统枚举执行；打包运行时不调用。
     */
    fun findUnregisteredConfigFiles(configFiles: Collection<String>, registeredConfigs: Collection<String>): List<String> =
        configFiles.filterNot { it in registeredConfigs }.sorted()

    /** 按 ERROR 输出每个问题（结构化单行，机器可读）。 */
    fun logProblems(problems: List<Problem>) {
        problems.forEach { logger.error("mixin-config-registration: {}", it) }
    }

    private fun hasClassBytes(binaryName: String, classLoader: ClassLoader): Boolean =
        classLoader.getResourceAsStream("${binaryName.replace('.', '/')}.class")?.use { true } ?: false

    private fun hasMixinAnnotation(binaryName: String, classLoader: ClassLoader): Boolean {
        val bytes = classLoader.getResourceAsStream("${binaryName.replace('.', '/')}.class")?.use { it.readBytes() }
            ?: return false
        val classNode = ClassNode()
        ClassReader(bytes).accept(classNode, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        val annotations = classNode.visibleAnnotations.orEmpty() + classNode.invisibleAnnotations.orEmpty()
        return annotations.any { it.desc == MIXIN_ANNOTATION_DESCRIPTOR }
    }
}
