package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.LocalizedMessages
import io.alexjoest.stackupup.StackUpUpIds
import net.minecraft.util.text.translation.LanguageMap

/**
 * 消息定义与格式化辅助。
 *
 * 自有译表 [LocalizedMessages] 是格式化权威（不可变 map，fail-fast）；
 * 本对象只保留消息载体构造与客户端 `LanguageMap` 注入桥接。
 * 桥接只为客户端组件渲染（`TextComponentTranslation` / `I18n`）服务，
 * 服务端路径不依赖该全局状态，也不依赖客户端事件。
 */
internal object RuleMessages {
    private var loadedLanguageCode: String = ""

    // 这里只保留“消息定义与格式化辅助”职责。
    // 玩家可见文本应尽量传递 LocalizedMessage，到客户端边界再转组件。
    fun message(key: RuleMessageKey, vararg args: Any): LocalizedMessage = LocalizedMessage(key.translationKey, args.toList())

    fun exception(key: RuleMessageKey, vararg args: Any): LocalizedRuleException = LocalizedRuleException(message(key, *args))

    fun syncLanguage(languageCode: String) {
        val normalizedCode = languageCode.lowercase()
        LocalizedMessages.setLanguage(normalizedCode)
        if (normalizedCode == loadedLanguageCode) {
            return
        }

        injectLanguage("en_us")
        if (normalizedCode != "en_us") {
            injectLanguage(normalizedCode)
        }
        loadedLanguageCode = normalizedCode
    }

    // 桥接注入：只针对自有语言文件；第三方语言代码对应资源不存在时静默跳过，
    // 此时 [LocalizedMessages.format] 会按设计回落 en_us，不属于“吞缺失”。
    private fun injectLanguage(languageCode: String) {
        RuleMessages::class.java
            .getResourceAsStream("/assets/${StackUpUpIds.MOD_ID}/lang/$languageCode.lang")
            ?.use(LanguageMap::inject)
    }
}
