package io.alexjoest.stackupup.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class LateMixinConfigCoverageTest {
    private val modules = listOf(
        CompatModule("ae2", "ae2"),
        CompatModule("brandonsCore", "brandonscore"),
        CompatModule("actuallyAdditions", "actuallyadditions"),
        CompatModule("cyclopsCore", "cyclopscore"),
        CompatModule("enderIo", "enderio"),
        CompatModule("ic2", "ic2"),
        CompatModule("mantle", "mantle"),
        CompatModule("refinedStorage", "refinedstorage"),
    )

    @Test
    fun `late mixin modules should have config fields and localization`() {
        val config = read("src/main/kotlin/io/alexjoest/stackupup/StackUpUpConfig.kt")
        val loader = read("src/main/kotlin/io/alexjoest/stackupup/bootstrap/StackUpUpLateMixinLoader.kt")
        val enUs = read("src/main/resources/assets/stackupup/lang/en_us.lang")
        val zhCn = read("src/main/resources/assets/stackupup/lang/zh_cn.lang")

        for (module in modules) {
            assertTrue(config.contains("var ${module.fieldName}: Boolean"), "missing config field: ${module.fieldName}")
            assertTrue(loader.contains("\"${module.fieldName}\""), "missing late loader key: ${module.fieldName}")
            assertTrue(
                enUs.contains("config.stackupup.compatibility.${module.langName}.name="),
                "missing en_us localization: ${module.langName}",
            )
            assertTrue(
                zhCn.contains("config.stackupup.compatibility.${module.langName}.name="),
                "missing zh_cn localization: ${module.langName}",
            )
        }
    }

    private fun read(path: String): String = String(Files.readAllBytes(Paths.get(path)), Charsets.UTF_8)

    private data class CompatModule(val fieldName: String, val langName: String)
}
