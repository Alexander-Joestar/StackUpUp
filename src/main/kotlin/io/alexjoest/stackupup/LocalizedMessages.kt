package io.alexjoest.stackupup

import io.alexjoest.stackupup.rules.RuleMessageKey
import java.io.InputStream

/**
 * 自有不可变译表（T8.1）。
 *
 * 显式初始化点 [initialize]（由 `StackUpUp.preInit` 在客户端与服务端调用）加载
 * `en_us`/`zh_cn` 两个 lang 文件为不可变 map；缺文件、重复键、坏格式行、
 * 双语言键集合不一致、统一键模型缺键、表外键均 fail-fast，异常带文件与键名。
 *
 * [format] 是自有键的统一格式化入口，缺键绝不回落裸 key；
 * 全局 `LanguageMap` 只由 [io.alexjoest.stackupup.rules.RuleMessages.syncLanguage]
 * 作为客户端组件渲染的注入桥接使用，服务端路径不依赖它，也不依赖任何客户端事件。
 *
 * 兼容说明：规则层与既有测试可能在 preInit 之前首次格式化，[format] 内的幂等
 * [ensureInitialized] 兜底保证不会撞上未初始化状态；生产环境的加载时机仍由
 * preInit 显式控制。
 */
internal object LocalizedMessages {
    const val DEFAULT_LANGUAGE_CODE: String = "en_us"

    private val supportedLanguageCodes = listOf(DEFAULT_LANGUAGE_CODE, "zh_cn")

    @Volatile
    private var tables: Map<String, Map<String, String>>? = null

    @Volatile
    private var selectedLanguageCode: String = DEFAULT_LANGUAGE_CODE

    /**
     * 显式初始化入口，幂等：已加载时直接复用不可变表。
     * 客户端与服务端都在 `StackUpUp.preInit` 调用，不依赖客户端事件。
     */
    @Synchronized
    fun initialize() {
        initializeWithProvider(::openClasspathResource)
    }

    /**
     * 可注入资源提供者的初始化，供测试构造缺失/重复/坏格式场景。
     * 不做已初始化短路，保证测试无论执行顺序都能拿到确定结果。
     */
    @Synchronized
    internal fun initializeWithProvider(openResource: (String) -> InputStream?) {
        val loaded = buildMap(supportedLanguageCodes.size) {
            for (languageCode in supportedLanguageCodes) {
                put(languageCode, loadLanguageTable(languageCode, openResource))
            }
        }
        validateKeySets(loaded)
        tables = loaded
    }

    /**
     * 切换 [format] 使用的语言；未支持的语言代码（如第三方语言）在 [format] 时回落 en_us。
     */
    @Synchronized
    fun setLanguage(languageCode: String) {
        ensureInitialized()
        selectedLanguageCode = languageCode.lowercase()
    }

    /**
     * 统一格式化入口（枚举键）。模板格式与旧 `I18n.translateToLocalFormatted` 一致（`String.format`）。
     */
    fun format(key: RuleMessageKey, vararg args: Any): String {
        val table = activeTable()
        val template = table[key.translationKey]
            ?: throw IllegalStateException(
                "Missing translation for key '${key.translationKey}' in language '$selectedLanguageCode'",
            )
        return String.format(template, *args)
    }

    /**
     * 统一格式化入口（字符串键）。非自有键直接 fail-fast，不回落裸 key。
     */
    fun format(translationKey: String, vararg args: Any): String {
        val key = RuleMessageKey.fromTranslationKey(translationKey)
            ?: throw IllegalArgumentException("Not an owned translation key: '$translationKey'")
        return format(key, *args)
    }

    /**
     * 返回指定语言的不可变表，供测试与桥接读取。
     */
    internal fun tableFor(languageCode: String): Map<String, String> {
        ensureInitialized()
        return tables?.getValue(languageCode.lowercase())
            ?: throw IllegalStateException("LocalizedMessages is not initialized; call LocalizedMessages.initialize() first")
    }

    @Synchronized
    private fun ensureInitialized() {
        if (tables == null) {
            initializeWithProvider(::openClasspathResource)
        }
    }

    private fun activeTable(): Map<String, String> {
        ensureInitialized()
        val activeTables = tables
            ?: throw IllegalStateException("LocalizedMessages is not initialized; call LocalizedMessages.initialize() first")
        return activeTables[selectedLanguageCode] ?: activeTables.getValue(DEFAULT_LANGUAGE_CODE)
    }

    private fun loadLanguageTable(languageCode: String, openResource: (String) -> InputStream?): Map<String, String> {
        val resourcePath = "/assets/${StackUpUpIds.MOD_ID}/lang/$languageCode.lang"
        val stream = openResource(resourcePath)
            ?: throw IllegalStateException("Missing language resource: $resourcePath")
        stream.use { return parseLanguageLines(languageCode, it) }
    }

    internal fun parseLanguageLines(languageCode: String, input: InputStream): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            var lineNumber = 0
            for (line in reader.lineSequence()) {
                lineNumber++
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue
                }
                val separator = trimmed.indexOf('=')
                if (separator < 0) {
                    throw IllegalStateException("Bad translation line in $languageCode.lang line $lineNumber: '$trimmed'")
                }
                val key = trimmed.substring(0, separator).trim()
                val value = trimmed.substring(separator + 1).trim()
                if (key.isEmpty()) {
                    throw IllegalStateException("Empty translation key in $languageCode.lang line $lineNumber")
                }
                if (result.put(key, value) != null) {
                    throw IllegalStateException("Duplicate translation key '$key' in $languageCode.lang line $lineNumber")
                }
            }
        }
        return result
    }

    private fun validateKeySets(tables: Map<String, Map<String, String>>) {
        val english = tables.getValue(DEFAULT_LANGUAGE_CODE)
        val chinese = tables.getValue("zh_cn")

        val onlyEnglish = english.keys - chinese.keys
        val onlyChinese = chinese.keys - english.keys
        if (onlyEnglish.isNotEmpty() || onlyChinese.isNotEmpty()) {
            throw IllegalStateException(
                "Language key sets differ: only in en_us=$onlyEnglish, only in zh_cn=$onlyChinese",
            )
        }

        val modelKeys = RuleMessageKey.entries.mapTo(mutableSetOf()) { it.translationKey }
        val missingFromFiles = modelKeys - english.keys
        if (missingFromFiles.isNotEmpty()) {
            throw IllegalStateException("Owned message keys missing from lang files: $missingFromFiles")
        }

        // 命名空间规则：文件键必须属于统一模型，或属于 C 类 config.*（@Config.LangKey 留在注解上）。
        val foreignKeys = english.keys - modelKeys
        val unexpected = foreignKeys.filterNot { it.startsWith("${StackUpUpIds.CONFIG_LANG_ROOT}.") }
        if (unexpected.isNotEmpty()) {
            throw IllegalStateException("Keys in lang files outside owned model: $unexpected")
        }
    }

    private fun openClasspathResource(resourcePath: String): InputStream? = LocalizedMessages::class.java.getResourceAsStream(resourcePath)
}
