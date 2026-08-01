// 属性管道核心：本仓库全部 gradle.properties / -P 参数统一经本文件访问。
// 用法：convention 插件脚本与根脚本直接消费下方扩展属性（如 modVersion、useMixins），
// 访问器工厂保持 private，不对外暴露。
// 属性键名与 gradle.properties 保持一致；键名改动须同步 gradle.properties 与消费点。
//
// E1：自动验收属性旧名前缀映射（stackupupDevAutoTest* ↔ stackupDevAutoTest*）集中在本文件
// 作为管道底层参数：消费契约要求新旧前缀长期并存，这里只集中映射关系，不删除旧名、不改默认值，
// fallback 顺序固定为 新名 → 旧名 → 默认值。

import org.gradle.api.GradleException
import org.gradle.api.Project
import kotlin.properties.ReadOnlyProperty

// ── 项目身份与构建开关（键名 = gradle.properties 键名） ─────────────────────────────────

val Project.modVersion: String by required("mod_version")
val Project.mavenGroup: String by required("maven_group")
val Project.modId: String by required("mod_id")
val Project.archivesBaseName: String by required("archives_base_name")
val Project.forgelinContinuousVersion: String by required("forgelin_continuous_version")

val Project.useAccessTransformer: Boolean by flag("use_access_transformer")
val Project.useMixins: Boolean by flag("use_mixins")
val Project.useCoremod: Boolean by flag("use_coremod")
val Project.useAssetmover: Boolean by flag("use_assetmover")
val Project.includeMod: Boolean by flag("include_mod")
val Project.coremodPluginClassName: String by requiredIf("coremod_plugin_class_name") { useCoremod }

// ── E1：自动验收参数（旧名前缀兼容，原始字符串，无默认值） ────────────────────────────────
// 默认值由 autoTest.gradle.kts 的 AutoTestDefaults 集中；空串视为“已显式提供”，
// 矩阵任务的有意空 Rule 依赖该语义，不得过滤空白。

val Project.autoTestMode: String? by compatible("stackupupDevAutoTestMode")
val Project.autoTestOre: String? by compatible("stackupupDevAutoTestOre")
val Project.autoTestRule: String? by compatible("stackupupDevAutoTestRule")
val Project.autoTestItem: String? by compatible("stackupupDevAutoTestItem")
val Project.autoTestMeta: String? by compatible("stackupupDevAutoTestMeta")
val Project.autoTestCount: String? by compatible("stackupupDevAutoTestCount")
val Project.autoTestWorldFolder: String? by compatible("stackupupDevAutoTestWorldFolder")
val Project.autoTestWorldName: String? by compatible("stackupupDevAutoTestWorldName")
val Project.autoTestServerPort: String? by compatible("stackupupDevAutoTestServerPort")
val Project.autoTestMatrix: String? by compatible("stackupupDevAutoTestMatrix")
val Project.autoTestEnabled: Boolean by compatibleFlag("stackupupDevAutoTest")

// ── E2：常用运行任务名集中，避免字符串散落（自根脚本迁移，字符串逐字保留） ─────────────────

val taskRunClient = "runClient"
val taskRunServer = "runServer"
val taskRunClientAutoTest = "runClientAutoTest"
val taskRunServerAutoTest = "runServerAutoTest"
val taskRunObfClient = "runObfClient"
val taskRunObfServer = "runObfServer"
val taskRunServerAutoTestMatrix = "runServerAutoTestMatrix"

// ── E1 旧名映射表（管道底层参数，不删除旧名） ────────────────────────────────────────────

val autoTestPropertyLegacyNames: Map<String, String> =
    mapOf(
        "stackupupDevAutoTest" to "stackupDevAutoTest",
        "stackupupDevAutoTestMode" to "stackupDevAutoTestMode",
        "stackupupDevAutoTestOre" to "stackupDevAutoTestOre",
        "stackupupDevAutoTestRule" to "stackupDevAutoTestRule",
        "stackupupDevAutoTestItem" to "stackupDevAutoTestItem",
        "stackupupDevAutoTestMeta" to "stackupDevAutoTestMeta",
        "stackupupDevAutoTestCount" to "stackupDevAutoTestCount",
        "stackupupDevAutoTestWorldFolder" to "stackupDevAutoTestWorldFolder",
        "stackupupDevAutoTestWorldName" to "stackupDevAutoTestWorldName",
        "stackupupDevAutoTestServerPort" to "stackupDevAutoTestServerPort",
        "stackupupDevAutoTestMatrix" to "stackupDevAutoTestMatrix",
    )

// ── 访问器工厂（仅本文件使用，其余脚本只消费上方扩展属性） ─────────────────────────────────

// 必填字符串：缺失或空白即 Fail Fast。
@Suppress("UnstableApiUsage")
private fun required(key: String? = null): ReadOnlyProperty<Project, String> =
    ReadOnlyProperty { project, property ->
        val name = key ?: property.name
        project.providers.gradleProperty(name).filter { it.isNotBlank() }.orNull
            ?: throw GradleException("Required property \"$name\" is missing or blank.")
    }

// 可选字符串：缺失或空白时回落到默认值。
@Suppress("UnstableApiUsage")
private fun optional(key: String? = null, default: String = ""): ReadOnlyProperty<Project, String> =
    ReadOnlyProperty { project, property ->
        project.providers.gradleProperty(key ?: property.name).filter { it.isNotBlank() }.orNull ?: default
    }

// 必选开关：满足条件时缺失即 Fail Fast；不满足条件时返回空串。
@Suppress("UnstableApiUsage")
private fun requiredIf(key: String? = null, predicate: Project.() -> Boolean): ReadOnlyProperty<Project, String> =
    ReadOnlyProperty { project, property ->
        val name = key ?: property.name
        if (project.predicate()) {
            project.providers.gradleProperty(name).filter { it.isNotBlank() }.orNull
                ?: throw GradleException("Required property \"$name\" is missing or blank.")
        } else {
            project.providers.gradleProperty(name).getOrElse("")
        }
    }

// 布尔开关：严格解析（toBooleanStrictOrNull），非法值 Fail Fast。
@Suppress("UnstableApiUsage")
private fun flag(key: String? = null, default: Boolean = false): ReadOnlyProperty<Project, Boolean> =
    ReadOnlyProperty { project, property ->
        val name = key ?: property.name
        val raw = project.providers.gradleProperty(name).orNull
        when {
            raw == null -> default
            else -> raw.toBooleanStrictOrNull()
                ?: throw GradleException("Boolean property \"$name\" must be \"true\" or \"false\", but was \"$raw\".")
        }
    }

// E1 兼容读取：新名 → 旧名 → null（空串原样返回，不视为缺失）。
@Suppress("UnstableApiUsage")
private fun compatible(key: String? = null): ReadOnlyProperty<Project, String?> =
    ReadOnlyProperty { project, property ->
        project.compatibleGradleProperty(key ?: property.name)
    }

// E1 兼容开关：新名 → 旧名 → 默认值，严格解析。
@Suppress("UnstableApiUsage")
private fun compatibleFlag(key: String? = null, default: Boolean = false): ReadOnlyProperty<Project, Boolean> =
    ReadOnlyProperty { project, property ->
        val name = key ?: property.name
        val raw = project.compatibleGradleProperty(name)
        when {
            raw == null -> default
            else -> raw.toBooleanStrictOrNull()
                ?: throw GradleException("Boolean property \"$name\" must be \"true\" or \"false\", but was \"$raw\".")
        }
    }

// 环境变量优先的可选字符串（当前构建未使用，保留为管道通用能力）。
@Suppress("UnstableApiUsage")
private fun envOrOptional(envName: String, key: String? = null, default: String = ""): ReadOnlyProperty<Project, String> =
    ReadOnlyProperty { project, property ->
        project.providers.environmentVariable(envName).orNull
            ?: project.providers.gradleProperty(key ?: property.name).orNull
            ?: default
    }

// 环境变量优先的布尔开关（当前构建未使用，保留为管道通用能力）。
@Suppress("UnstableApiUsage")
private fun envOrFlag(envName: String, key: String? = null, default: Boolean = false): ReadOnlyProperty<Project, Boolean> =
    ReadOnlyProperty { project, property ->
        val name = key ?: property.name
        val raw = project.providers.environmentVariable(envName).orNull
            ?: project.providers.gradleProperty(name).orNull
        when {
            raw == null -> default
            else -> raw.toBooleanStrictOrNull()
                ?: throw GradleException("Boolean property \"$name\" (or environment variable \"$envName\") must be \"true\" or \"false\", but was \"$raw\".")
        }
    }

// E1 底层函数：新名前缀优先，旧名前缀兜底。
@Suppress("UnstableApiUsage")
private fun Project.compatibleGradleProperty(propertyName: String): String? =
    providers.gradleProperty(propertyName).orNull
        ?: autoTestPropertyLegacyNames[propertyName]?.let { providers.gradleProperty(it).orNull }
