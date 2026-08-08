package io.alexjoest.stackupup.rules

import io.alexjoest.stackupup.LocalizedMessages
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.TextComponentTranslation

/**
 * 统一的可本地化消息载体。
 *
 * 规则层、重载报告和聊天提示都只传递 key + args，
 * 到最终边界再决定是落成日志字符串还是聊天组件。
 */
data class LocalizedMessage(val translationKey: String, val args: List<Any> = emptyList()) {
    // 格式化走自有不可变译表（统一入口），不依赖全局 LanguageMap；缺键 fail-fast。
    fun format(): String = LocalizedMessages.format(translationKey, *mapArgs(::formatArgument))

    fun toTextComponent(): ITextComponent = TextComponentTranslation(translationKey, *mapArgs(::componentArgument))

    private fun mapArgs(transform: (Any) -> Any): Array<Any> = args.map(transform).toTypedArray()

    private fun formatArgument(value: Any): Any = if (value is LocalizedMessage) value.format() else value

    private fun componentArgument(value: Any): Any = if (value is LocalizedMessage) value.toTextComponent() else value
}

class LocalizedRuleException(val messageData: LocalizedMessage) : IllegalArgumentException(messageData.format())
