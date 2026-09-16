package io.alexjoest.stackupup

import io.alexjoest.stackupup.rules.RuleMessageKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class LocalizedMessagesTest {
    @Test
    fun `双语言键集合一致且无重复`() {
        LocalizedMessages.initialize()
        val english = LocalizedMessages.tableFor("en_us")
        val chinese = LocalizedMessages.tableFor("zh_cn")
        assertEquals(english.size, chinese.size)
        assertEquals(english.keys, chinese.keys)
        // 结构检查：当前两份 lang 文件的已知键总数，防止任一侧漏键。
        assertEquals(86, english.size)
    }

    @Test
    fun `统一模型键在两个语言表中都存在`() {
        LocalizedMessages.initialize()
        val english = LocalizedMessages.tableFor("en_us")
        val chinese = LocalizedMessages.tableFor("zh_cn")
        for (key in RuleMessageKey.entries) {
            assertTrue(key.translationKey in english, "en_us missing ${key.translationKey}")
            assertTrue(key.translationKey in chinese, "zh_cn missing ${key.translationKey}")
        }
    }

    @Test
    fun `表内键都属于统一模型或config命名空间`() {
        LocalizedMessages.initialize()
        val modelKeys = RuleMessageKey.entries.mapTo(mutableSetOf()) { it.translationKey }
        for (key in LocalizedMessages.tableFor("en_us").keys) {
            assertTrue(key in modelKeys || key.startsWith("config.stackupup."), "unexpected key $key")
        }
    }

    @Test
    fun `缺少语言文件时异常可定位`() {
        val exception = assertThrows(IllegalStateException::class.java) {
            LocalizedMessages.initializeWithProvider { null }
        }
        assertTrue(exception.message.orEmpty().contains("en_us.lang"))
    }

    @Test
    fun `重复键时异常带键名和文件`() {
        val exception = assertThrows(IllegalStateException::class.java) {
            LocalizedMessages.initializeWithProvider { resourcePath ->
                if (resourcePath.contains("en_us")) {
                    ByteArrayInputStream("a.b=1\na.b=2\n".toByteArray())
                } else {
                    null
                }
            }
        }
        assertTrue(exception.message.orEmpty().contains("a.b"))
        assertTrue(exception.message.orEmpty().contains("en_us.lang"))
    }

    @Test
    fun `坏格式行异常带行号和内容`() {
        val exception = assertThrows(IllegalStateException::class.java) {
            LocalizedMessages.initializeWithProvider { resourcePath ->
                if (resourcePath.contains("en_us")) {
                    ByteArrayInputStream("key.one=1\nbroken line without separator\n".toByteArray())
                } else {
                    null
                }
            }
        }
        assertTrue(exception.message.orEmpty().contains("broken line without separator"))
    }

    @Test
    fun `双语言键集合不一致时异常列出差异`() {
        val exception = assertThrows(IllegalStateException::class.java) {
            LocalizedMessages.initializeWithProvider { resourcePath ->
                val content = if (resourcePath.contains("zh_cn")) "a.b=1\nx.y=2\n" else "a.b=1\n"
                ByteArrayInputStream(content.toByteArray())
            }
        }
        assertTrue(exception.message.orEmpty().contains("x.y"))
    }

    @Test
    fun `统一模型缺键时异常列出缺键`() {
        val exception = assertThrows(IllegalStateException::class.java) {
            LocalizedMessages.initializeWithProvider { resourcePath ->
                ByteArrayInputStream("config.stackupup.title=Config\n".toByteArray())
            }
        }
        assertTrue(exception.message.orEmpty().contains("message.stackupup.rule_error.unsupported_field"))
    }

    @Test
    fun `格式化默认英文并可切换到中文`() {
        LocalizedMessages.initialize()
        LocalizedMessages.setLanguage("en_us")
        try {
            assertEquals(
                "Unsupported field: mystery",
                LocalizedMessages.format(RuleMessageKey.UNSUPPORTED_FIELD, "mystery"),
            )
            LocalizedMessages.setLanguage("zh_cn")
            assertEquals(
                "不支持的字段：mystery",
                LocalizedMessages.format(RuleMessageKey.UNSUPPORTED_FIELD, "mystery"),
            )
        } finally {
            LocalizedMessages.setLanguage("en_us")
        }
    }

    @Test
    fun `未知语言回落默认英文表`() {
        LocalizedMessages.initialize()
        LocalizedMessages.setLanguage("de_de")
        try {
            assertEquals(
                "Unsupported field: mystery",
                LocalizedMessages.format(RuleMessageKey.UNSUPPORTED_FIELD, "mystery"),
            )
        } finally {
            LocalizedMessages.setLanguage("en_us")
        }
    }

    @Test
    fun `未知字符串键不得回落裸键`() {
        LocalizedMessages.initialize()
        val exception = assertThrows(IllegalArgumentException::class.java) {
            LocalizedMessages.format("some.unknown.key")
        }
        assertTrue(exception.message.orEmpty().contains("some.unknown.key"))
    }

    @Test
    fun `格式化不依赖全局LanguageMap`() {
        // 纯 JUnit 环境（无 Minecraft 启动、无客户端事件）下直接格式化，
        // 证明服务端路径只依赖自有不可变译表。
        LocalizedMessages.initialize()
        assertEquals(
            "[pack.su] Line 7 failed to load: broken",
            LocalizedMessages.format(RuleMessageKey.LOAD_FAILED_WITH_SOURCE, "pack.su", 7, "broken"),
        )
    }
}
