// 开发自动验收（dev automation）：参数管道、-D 注入、透传任务、矩阵任务与服务端文件模板。
// 行为与重构前根脚本对应段落一致；E1 旧名前缀 fallback 由 Properties.kt 管道提供。

import java.util.Locale

plugins {
    // 需要 MinecraftExtension.extraRunJvmArguments 做 -D 注入；RFG 已由 minecraft 插件应用，
    // 此处声明同一插件仅为取得编译期类型，实际应用按插件 id 去重。
    id("com.gtnewhorizons.retrofuturagradle")
}

// ── 参数管道 ──────────────────────────────────────────────────────────────────────────

// 自动验收默认值集中：逐字保留自重构前根脚本（属性默认值不因模块化而改变）。
object AutoTestDefaults {
    const val ORE: String = "ingotSteel"
    const val ITEM: String = ""
    const val META: String = "11305"
    const val COUNT: String = "128"
    const val WORLD_FOLDER: String = "stackupup_dev_autotest"
    const val WORLD_NAME: String = "StackUpUp 自动测试"
    const val SERVER_PORT: String = "0"
    const val MATRIX: String = "false"
}

// 推导结果：与重构前根脚本的逐参数推导等价。
data class AutoTestRequest(
    val enabled: Boolean,
    val mode: String?,
    val ore: String,
    val rule: String,
    val item: String,
    val meta: String,
    val count: String,
    val worldFolder: String,
    val worldName: String,
    val serverPort: String,
    val matrix: String,
)

val autoTestRequestedTasks = setOf(taskRunClientAutoTest, taskRunServerAutoTest)

// 推导函数：任务名命中优先于 -P 参数；E1 fallback 顺序为 新名 → 旧名 → 默认值。
fun Project.deriveAutoTestRequest(): AutoTestRequest {
    val requestedByTask = gradle.startParameter.taskNames.any { it in autoTestRequestedTasks }
    val mode =
        when {
            gradle.startParameter.taskNames.any { it == taskRunServerAutoTest } -> "server"
            gradle.startParameter.taskNames.any { it == taskRunClientAutoTest } -> "client"
            else -> autoTestMode
        }
    val ore = autoTestOre ?: AutoTestDefaults.ORE
    return AutoTestRequest(
        enabled = requestedByTask || autoTestEnabled,
        mode = mode,
        ore = ore,
        rule = autoTestRule ?: "ore = $ore -> 1024",
        item = autoTestItem ?: AutoTestDefaults.ITEM,
        meta = autoTestMeta ?: AutoTestDefaults.META,
        count = autoTestCount ?: AutoTestDefaults.COUNT,
        worldFolder = autoTestWorldFolder ?: AutoTestDefaults.WORLD_FOLDER,
        worldName = autoTestWorldName ?: AutoTestDefaults.WORLD_NAME,
        serverPort = autoTestServerPort ?: AutoTestDefaults.SERVER_PORT,
        matrix = autoTestMatrix ?: AutoTestDefaults.MATRIX,
    )
}

val autoTestRequest: AutoTestRequest = deriveAutoTestRequest()

// ── 13 个 -D 键注入（键名与顺序与重构前一致；enabled/autoShutdown/failFast/clearInventoryBeforeGive 恒为 true） ──

val autoTestJvmArgs: List<String> =
    buildList {
        add("-Dstackupup.dev.autoTest.enabled=true")
        add("-Dstackupup.dev.autoTest.mode=${autoTestRequest.mode ?: "client"}")
        add("-Dstackupup.dev.autoTest.ore=${autoTestRequest.ore}")
        add("-Dstackupup.dev.autoTest.rule=${autoTestRequest.rule}")
        add("-Dstackupup.dev.autoTest.item=${autoTestRequest.item}")
        add("-Dstackupup.dev.autoTest.meta=${autoTestRequest.meta}")
        add("-Dstackupup.dev.autoTest.count=${autoTestRequest.count}")
        add("-Dstackupup.dev.autoTest.worldFolder=${autoTestRequest.worldFolder}")
        add("-Dstackupup.dev.autoTest.worldName=${autoTestRequest.worldName}")
        add("-Dstackupup.dev.autoTest.matrix=${autoTestRequest.matrix}")
        add("-Dstackupup.dev.autoTest.autoShutdown=true")
        add("-Dstackupup.dev.autoTest.failFast=true")
        add("-Dstackupup.dev.autoTest.clearInventoryBeforeGive=true")
    }

if (autoTestRequest.enabled) {
    minecraft {
        extraRunJvmArguments.addAll(autoTestJvmArgs)
    }
}

// ── 透传任务 ──────────────────────────────────────────────────────────────────────────

tasks.register(taskRunClientAutoTest) {
    group = "modded minecraft"
    description = "以开发自动验收模式运行客户端。"
    dependsOn(taskRunClient)
}

tasks.register(taskRunServerAutoTest) {
    group = "modded minecraft"
    description = "以开发自动验收模式运行服务端。"
    dependsOn(taskRunServer)
}

// E3：自动验收服务端文件模板集中。输出必须与集中前逐字节一致
// （由 prepareAutoTestServerFiles 的生成物 md5 对比校验）。
// 注意：trimIndent 内部用 LF 连接、末尾追加 System.lineSeparator()，换行风格必须保留原语义。
val autoTestEulaTemplate =
    """
    # 自动生成：仅用于本地开发自动验收
    eula=true
    """.trimIndent() + System.lineSeparator()
val autoTestServerPropertiesStripPrefixes = listOf("online-mode=", "server-port=", "level-name=")

fun autoTestServerPropertiesHeaderLines(
    serverPort: String,
    levelName: String,
): List<String> =
    listOf(
        "online-mode=false",
        "server-port=$serverPort",
        "level-name=$levelName",
    )

val prepareAutoTestServerFiles =
    tasks.register("prepareAutoTestServerFiles") {
        group = "build setup"
        description = "为服务端自动验收准备无交互启动所需的 EULA 与基础配置。"
        doLast {
            val runDirectory = file("run")
            if (!runDirectory.exists()) {
                runDirectory.mkdirs()
            }

            val eulaFile = runDirectory.resolve("eula.txt")
            eulaFile.writeText(autoTestEulaTemplate, Charsets.UTF_8)

            val serverPropertiesFile = runDirectory.resolve("server.properties")
            val existingLines =
                if (serverPropertiesFile.exists()) {
                    serverPropertiesFile.readLines(Charsets.UTF_8)
                } else {
                    emptyList()
                }

            val filteredLines =
                existingLines.filterNot { line -> autoTestServerPropertiesStripPrefixes.any(line::startsWith) }
            val finalLines =
                buildList {
                    addAll(
                        autoTestServerPropertiesHeaderLines(
                            autoTestServerPort ?: AutoTestDefaults.SERVER_PORT,
                            autoTestWorldFolder ?: AutoTestDefaults.WORLD_FOLDER,
                        ),
                    )
                    addAll(filteredLines)
                }
            serverPropertiesFile.writeText(
                finalLines.joinToString(separator = System.lineSeparator(), postfix = System.lineSeparator()),
                Charsets.UTF_8,
            )
        }
    }

if (autoTestRequest.enabled && autoTestRequest.mode == "server") {
    tasks.named(taskRunServer).configure {
        dependsOn(prepareAutoTestServerFiles)
    }
}

// ── 服务端自动验收任务工厂与矩阵（数据表驱动） ─────────────────────────────────────────────

val isWindows: Boolean = System.getProperty("os.name").lowercase(Locale.ROOT).contains("windows")

fun gradlew(vararg args: String): List<String> =
    if (isWindows) {
        listOf("cmd", "/c", ".\\gradlew.bat", *args)
    } else {
        listOf("./gradlew", *args)
    }

data class AutoTestTaskSpec(
    val taskName: String,
    val description: String,
    val properties: List<Pair<String, String>>,
)

// 矩阵任务数据表：任务名与描述逐字保留；Matrix 的空 Rule 为有意空值，不得改为默认；
// -P 键拼装顺序（Matrix/Rule/WorldFolder/WorldName 或 Ore/Rule/... 或 Item/Meta/Rule/...）保留。
val serverAutoTestTaskSpecs: List<AutoTestTaskSpec> =
    listOf(
        AutoTestTaskSpec(
            taskName = taskRunServerAutoTestMatrix,
            description = "在单次服务端启动中运行核心自动验收矩阵。",
            properties =
                listOf(
                    "Matrix" to "true",
                    "Rule" to "",
                    "WorldFolder" to "stackupup_dev_autotest_matrix",
                    "WorldName" to "StackUpUp 自动测试 Matrix",
                ),
        ),
        AutoTestTaskSpec(
            taskName = "runServerAutoTestIngotSteel",
            description = "运行 IngotSteel 服务端自动验收样例。",
            properties =
                listOf(
                    "Ore" to "ingotSteel",
                    "Rule" to "ore = ingotSteel -> 1024",
                    "WorldFolder" to "stackupup_dev_autotest_ingotsteel",
                    "WorldName" to "StackUpUp 自动测试 IngotSteel",
                ),
        ),
        AutoTestTaskSpec(
            taskName = "runServerAutoTestPlateSteel",
            description = "运行 PlateSteel 服务端自动验收样例。",
            properties =
                listOf(
                    "Ore" to "plateSteel",
                    "Rule" to "ore = plateSteel -> 1024",
                    "WorldFolder" to "stackupup_dev_autotest_platesteel",
                    "WorldName" to "StackUpUp 自动测试 PlateSteel",
                ),
        ),
        AutoTestTaskSpec(
            taskName = "runServerAutoTestDustSteel",
            description = "运行 DustSteel 服务端自动验收样例。",
            properties =
                listOf(
                    "Ore" to "dustSteel",
                    "Rule" to "ore = dustSteel -> 1024",
                    "WorldFolder" to "stackupup_dev_autotest_duststeel",
                    "WorldName" to "StackUpUp 自动测试 DustSteel",
                ),
        ),
        AutoTestTaskSpec(
            taskName = "runServerAutoTestVacuumTube",
            description = "运行 VacuumTube 服务端自动验收样例。",
            properties =
                listOf(
                    "Item" to "gregtech:meta_item_1",
                    "Meta" to "516",
                    "Rule" to "item = gregtech:meta_item_1 && meta = 516 -> 512",
                    "WorldFolder" to "stackupup_dev_autotest_vacuumtube",
                    "WorldName" to "StackUpUp 自动测试 VacuumTube",
                ),
        ),
    )

fun registerServerAutoTestTask(
    name: String,
    descriptionText: String,
    vararg properties: Pair<String, String>,
) {
    tasks.register<Exec>(name) {
        group = "modded minecraft"
        description = descriptionText
        workingDir = project.projectDir
        commandLine(
            gradlew(
                "--no-daemon",
                taskRunServerAutoTest,
                "-PstackupupDevAutoTest=true",
                "-PstackupupDevAutoTestMode=server",
                "-PstackupupDevAutoTestServerPort=0",
                "-PstackupupDevAutoTestCount=128",
                *properties.map { (key, value) -> "-PstackupupDevAutoTest$key=$value" }.toTypedArray(),
                "--stacktrace",
            ),
        )
    }
}

serverAutoTestTaskSpecs.forEach { spec ->
    registerServerAutoTestTask(spec.taskName, spec.description, *spec.properties.toTypedArray())
}
