# StackUpUp 堆叠上限重构 Implementation Plan

> **已过时，仅供历史参考。** 禁止把本文作为当前实现计划执行；当前入口见 `docs/agent/START_HERE.md`。本文保留早期堆叠上限重构思路，但不代表当前代码或文档路线。
> Unknown `IItemHandler` 动态扩大已禁用；不要恢复 remainder-system。
> 本文中的 `RuleMatchContext`、`CompiledRule.matches(RuleMatchContext)`、字段缓存上下文复制层均为旧设计；当前实现必须使用 `StackContext`，`RuleField` matcher/cache 直接读取 `StackContext`。
> 当前昂贵/可选上下文以 `RuleField.contextProviders` -> `RuntimeContextRequirements` provider plan 为准。
> `RuleContextRequirement` 仅保留旧兼容和诊断查询。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 1.12.2 环境下重构 StackUpUp 的堆叠上限内核，优先打通 `metadata`、GregTech `gt.metaitem.*`、矿物辞典匹配，并接入 `MixinBooter`。

**Architecture:** 先新增独立的规则内核与 `StackLimitService`，把堆叠上限查询统一到 `ItemStack` 粒度；再用 `MixinBooter` 接管固定目标补丁，仅保留极小的动态 ASM 兼容层。规则文件升级为 DSL v2，规则解析、执行、缓存、运行时重载全部独立封装。

**Tech Stack:** Kotlin 2.3、Forge 1.12.2、RetroFuturaGradle、MixinBooter、JUnit 5、Minecraft 1.12.2 矿物辞典

---

## 计划文件结构

**Create:**

- `src/test/kotlin/io/alexjoest/stackupup/rules/DslTokenizerTest.kt`
- `src/test/kotlin/io/alexjoest/stackupup/rules/DslParserTest.kt`
- `src/test/kotlin/io/alexjoest/stackupup/limit/StackLimitServiceTest.kt`
- `src/test/kotlin/io/alexjoest/stackupup/limit/OreDictIndexTest.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/ast/RuleAst.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/ast/ConditionAst.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/parse/DslToken.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/parse/DslTokenizer.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/parse/DslParser.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/compile/CompiledRule.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/compile/RuleCompiler.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/compile/RuleSnapshot.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/model/RuleAction.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/model/RuleMatchContext.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/io/DslRuleSource.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/io/RuleLoadResult.kt`
- `src/main/kotlin/io/alexjoest/stackupup/limit/StackIdentity.kt`
- `src/main/kotlin/io/alexjoest/stackupup/limit/StackLimitService.kt`
- `src/main/kotlin/io/alexjoest/stackupup/limit/OreDictIndex.kt`
- `src/main/kotlin/io/alexjoest/stackupup/limit/VanillaStackLimitView.kt`
- `src/main/kotlin/io/alexjoest/stackupup/bootstrap/StackUpLateMixins.kt`
- `src/main/kotlin/io/alexjoest/stackupup/mixin/early/ItemMixin.kt`
- `src/main/kotlin/io/alexjoest/stackupup/mixin/early/ItemStackMixin.kt`
- `src/main/kotlin/io/alexjoest/stackupup/mixin/early/PacketBufferMixin.kt`
- `src/main/kotlin/io/alexjoest/stackupup/mixin/early/NetHandlerPlayServerMixin.kt`
- `src/main/kotlin/io/alexjoest/stackupup/mixin/late/PacketUtilMixin.kt`
- `src/main/kotlin/io/alexjoest/stackupup/mixin/late/InventoryHelperMixin.kt`
- `src/main/kotlin/io/alexjoest/stackupup/mixin/late/RenderItemMixin.kt`
- `src/main/kotlin/io/alexjoest/stackupup/mixin/late/RenderEntityItemMixin.kt`
- `src/main/kotlin/io/alexjoest/stackupup/mixin/late/ServerRecipeBookHelperMixin.kt`
- `src/main/resources/mixins.stackupup.early.json`
- `src/main/resources/mixins.stackup.late.json`
- `docs/DSL-v2-迁移说明.md`
- `docs/DSL-v2-规则示例.md`

**Modify:**

- `build.gradle.kts`
- `gradle.properties`
- `src/main/kotlin/io/alexjoest/stackupup/StackUpUpCore.kt`
- `src/main/kotlin/io/alexjoest/stackupup/StackUpUp.kt`
- `src/main/kotlin/io/alexjoest/stackupup/StackUpUpConfig.kt`
- `src/main/kotlin/io/alexjoest/stackupup/CommandStackUpUpUp.kt`
- `src/main/kotlin/io/alexjoest/stackupup/core/DynamicCompatTransformer.kt`
- `src/main/kotlin/io/alexjoest/stackupup/core/ClassHierarchyTracker.kt`
- `README.md`

### Task 1: 补齐测试基础与规则内核骨架

**Files:**

- Modify: `build.gradle.kts`
- Modify: `gradle.properties`
- Create: `src/test/kotlin/io/alexjoest/stackupup/rules/DslTokenizerTest.kt`
- Create: `src/test/kotlin/io/alexjoest/stackupup/rules/DslParserTest.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/ast/RuleAst.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/ast/ConditionAst.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/parse/DslToken.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/parse/DslTokenizer.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/parse/DslParser.kt`
- Test: `src/test/kotlin/io/alexjoest/stackupup/rules/DslTokenizerTest.kt`
- Test: `src/test/kotlin/io/alexjoest/stackupup/rules/DslParserTest.kt`

- [ ] **Step 1: 写失败的 DSL 分词测试**

```kotlin
package io.alexjoest.stackupup.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.alexjoest.stackupup.rules.parse.DslTokenType
import io.alexjoest.stackupup.rules.parse.DslTokenizer

class DslTokenizerTest {
    @Test
    fun `应当识别 item 与 metadata 规则`() {
        val tokens = DslTokenizer.tokenize("item = gregtech:gt.metaitem.01 && meta = 11305 -> 512")
        assertEquals(
            listOf(
                DslTokenType.IDENTIFIER,
                DslTokenType.EQUALS,
                DslTokenType.IDENTIFIER,
                DslTokenType.AND_AND,
                DslTokenType.IDENTIFIER,
                DslTokenType.EQUALS,
                DslTokenType.NUMBER,
                DslTokenType.ARROW,
                DslTokenType.NUMBER,
                DslTokenType.EOF
            ),
            tokens.map { it.type }
        )
    }
}
```

- [ ] **Step 2: 写失败的 DSL 解析测试**

```kotlin
package io.alexjoest.stackupup.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.alexjoest.stackupup.rules.parse.DslParser

class DslParserTest {
    @Test
    fun `应当解析链式比较`() {
        val rule = DslParser.parseLine("2 < size < 64 -> 1024")
        assertEquals(1024, rule.action.value)
        assertEquals("size", rule.condition.debugFields().single())
    }

    @Test
    fun `应当解析 in 列表`() {
        val rule = DslParser.parseLine("item in [minecraft:egg, minecraft:snowball] -> 128")
        assertEquals(128, rule.action.value)
        assertEquals(2, rule.condition.debugLiteralCount())
    }
}
```

- [ ] **Step 3: 为 Gradle 增加测试依赖与 Mixin 开关**

```kotlin
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

同时把 `gradle.properties` 中的：

```properties
use_mixins = false
```

改成：

```properties
use_mixins = true
```

- [ ] **Step 4: 写最小 AST 与分词器骨架**

```kotlin
package io.alexjoest.stackupup.rules.ast

sealed interface ConditionAst {
    fun debugFields(): List<String>
    fun debugLiteralCount(): Int
}

data class FieldComparisonAst(
    val field: String,
    val operator: String,
    val literal: String
) : ConditionAst {
    override fun debugFields(): List<String> = listOf(field)
    override fun debugLiteralCount(): Int = 1
}

data class RuleActionAst(
    val operator: String,
    val value: Int
)

data class RuleAst(
    val condition: ConditionAst,
    val action: RuleActionAst
)
```

```kotlin
package io.alexjoest.stackupup.rules.parse

enum class DslTokenType {
    IDENTIFIER,
    NUMBER,
    EQUALS,
    NOT_EQUALS,
    GREATER,
    GREATER_EQUALS,
    LESS,
    LESS_EQUALS,
    AND_AND,
    OR_OR,
    IN,
    ARROW,
    PLUS_EQUALS,
    MINUS_EQUALS,
    STAR_EQUALS,
    SLASH_EQUALS,
    LEFT_BRACKET,
    RIGHT_BRACKET,
    COMMA,
    EOF
}
```

- [ ] **Step 5: 跑测试确认红转绿**

Run: `.\gradlew test --tests "io.alexjoest.stackupup.rules.*" --info`  
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 提交**

```bash
git add build.gradle.kts gradle.properties src/main/kotlin/io/alexjoest/stackupup/rules src/test/kotlin/io/alexjoest/stackupup/rules
git commit -m "test: add dsl parser test foundation"
```

### Task 2: 实现 DSL v2 解析器与编译器

**Files:**

- Modify: `src/main/kotlin/io/alexjoest/stackupup/rules/parse/DslParser.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/compile/CompiledRule.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/compile/RuleCompiler.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/model/RuleAction.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/model/RuleMatchContext.kt`
- Test: `src/test/kotlin/io/alexjoest/stackupup/rules/DslParserTest.kt`

- [ ] **Step 1: 写编译器失败测试**

```kotlin
@Test
fun `应当把 item in 列表编译成 or 条件`() {
    val compiled = RuleCompiler.compileLine("item in [minecraft:egg, minecraft:snowball] -> 128", 7)
    val egg = RuleMatchContext(itemId = "minecraft:egg", modId = "minecraft", meta = 0, baseSize = 16, type = "item", oreNames = emptySet())
    val snowball = RuleMatchContext(itemId = "minecraft:snowball", modId = "minecraft", meta = 0, baseSize = 16, type = "item", oreNames = emptySet())
    assertEquals(true, compiled.matches(egg))
    assertEquals(true, compiled.matches(snowball))
}

@Test
fun `应当按顺序保留乘法动作`() {
    val compiled = RuleCompiler.compileLine("ore = ingotSteel *= 2", 9)
    assertEquals("*=", compiled.action.operator)
    assertEquals(2, compiled.action.value)
}
```

- [ ] **Step 2: 扩展解析器支持 &&、||、in、链式比较与动作运算**

```kotlin
private fun parseComparisonChain(): ConditionAst {
    val left = parsePrimary()
    if (match(DslTokenType.LESS) && peekIdentifier("size")) {
        val field = consume(DslTokenType.IDENTIFIER).lexeme
        consume(DslTokenType.LESS)
        val upper = consume(DslTokenType.NUMBER).lexeme
        return AndAst(
            FieldComparisonAst(field, ">", (left as LiteralAst).value),
            FieldComparisonAst(field, "<", upper)
        )
    }
    rewindIfNeeded()
    return parsePrimaryComparison()
}
```

- [ ] **Step 3: 写编译器最小实现**

```kotlin
data class RuleAction(
    val operator: String,
    val value: Int
)

data class CompiledRule(
    val lineNumber: Int,
    val action: RuleAction,
    val predicate: (RuleMatchContext) -> Boolean
) {
    fun matches(context: RuleMatchContext): Boolean = predicate(context)
}
```

```kotlin
object RuleCompiler {
    fun compileLine(line: String, lineNumber: Int): CompiledRule {
        val ast = DslParser.parseLine(line)
        return CompiledRule(
            lineNumber = lineNumber,
            action = RuleAction(ast.action.operator, ast.action.value),
            predicate = compileCondition(ast.condition)
        )
    }
}
```

- [ ] **Step 4: 补中文错误定位**

```kotlin
class DslParseException(
    val lineNumber: Int,
    val sourceLine: String,
    override val message: String
) : RuntimeException("第 $lineNumber 行：$message。原始内容：$sourceLine")
```

- [ ] **Step 5: 跑解析与编译测试**

Run: `.\gradlew test --tests "io.alexjoest.stackupup.rules.DslParserTest" --info`  
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 提交**

```bash
git add src/main/kotlin/io/alexjoest/stackupup/rules src/test/kotlin/io/alexjoest/stackupup/rules
git commit -m "feat: implement dsl v2 parser and compiler"
```

### Task 3: 实现规则快照、矿物辞典索引与 StackLimitService

**Files:**

- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/compile/RuleSnapshot.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/limit/StackIdentity.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/limit/OreDictIndex.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/limit/StackLimitService.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/limit/VanillaStackLimitView.kt`
- Create: `src/test/kotlin/io/alexjoest/stackupup/limit/StackLimitServiceTest.kt`
- Create: `src/test/kotlin/io/alexjoest/stackupup/limit/OreDictIndexTest.kt`

- [ ] **Step 1: 写失败的规则执行测试**

```kotlin
package io.alexjoest.stackupup.limit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.alexjoest.stackupup.rules.compile.RuleCompiler
import io.alexjoest.stackupup.rules.compile.RuleSnapshot

class StackLimitServiceTest {
    @Test
    fun `应当按文件顺序执行规则`() {
        val snapshot = RuleSnapshot(
            version = 1L,
            rules = listOf(
                RuleCompiler.compileLine("ore = ingotSteel -> 512", 1),
                RuleCompiler.compileLine("ore = ingotSteel *= 2", 2)
            )
        )
        val service = StackLimitService(snapshot)
        val result = service.resolve(
            StackIdentity("gregtech:gt.metaitem.01", "gregtech", 11305, "item"),
            baseLimit = 64,
            oreNames = setOf("ingotSteel")
        )
        assertEquals(1024, result)
    }
}
```

- [ ] **Step 2: 写失败的矿物辞典缓存测试**

```kotlin
package io.alexjoest.stackupup.limit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OreDictIndexTest {
    @Test
    fun `同一物品与 metadata 应命中缓存`() {
        val index = OreDictIndex { _, _ -> setOf("ingotSteel") }
        assertEquals(setOf("ingotSteel"), index.getOreNames("gregtech:gt.metaitem.01", 11305))
        assertEquals(1, index.debugCacheSize())
    }
}
```

- [ ] **Step 3: 写最小服务实现**

```kotlin
package io.alexjoest.stackupup.limit

import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import io.alexjoest.stackupup.rules.model.RuleMatchContext

class StackLimitService(
    private val snapshot: RuleSnapshot
) {
    fun resolve(identity: StackIdentity, baseLimit: Int, oreNames: Set<String>): Int {
        val context = RuleMatchContext(
            itemId = identity.itemId,
            modId = identity.modId,
            meta = identity.meta,
            baseSize = baseLimit,
            type = identity.type,
            oreNames = oreNames
        )
        var result = baseLimit
        for (rule in snapshot.rules) {
            if (rule.matches(context)) {
                result = applyAction(result, rule.action.operator, rule.action.value)
            }
        }
        return result.coerceIn(1, Int.MAX_VALUE)
    }
}
```

- [ ] **Step 4: 实现矿物辞典索引**

```kotlin
class OreDictIndex(
    private val loader: (String, Int) -> Set<String>
) {
    private val cache = java.util.concurrent.ConcurrentHashMap<Pair<String, Int>, Set<String>>()

    fun getOreNames(itemId: String, meta: Int): Set<String> {
        return cache.computeIfAbsent(itemId to meta) { loader(itemId, meta) }
    }

    fun debugCacheSize(): Int = cache.size
}
```

- [ ] **Step 5: 跑 limit 测试**

Run: `.\gradlew test --tests "io.alexjoest.stackupup.limit.*" --info`  
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 提交**

```bash
git add src/main/kotlin/io/alexjoest/stackupup/limit src/main/kotlin/io/alexjoest/stackupup/rules/compile src/test/kotlin/io/alexjoest/stackupup/limit
git commit -m "feat: add stack limit runtime service"
```

### Task 4: 接入规则文件加载、配置重载与中文迁移文档

**Files:**

- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/io/DslRuleSource.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/io/RuleLoadResult.kt`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/StackUpUp.kt`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/StackUpUpConfig.kt`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/CommandStackUpUpUp.kt`
- Create: `docs/DSL-v2-迁移说明.md`
- Create: `docs/DSL-v2-规则示例.md`
- Modify: `README.md`

- [ ] **Step 1: 写失败的规则源测试**

```kotlin
@Test
fun `应当忽略注释与空行`() {
    val source = DslRuleSource.fromLines(
        listOf(
            "# 注释",
            "",
            "item = minecraft:egg -> 64"
        )
    )
    val result = source.load()
    assertEquals(1, result.snapshot.rules.size)
}
```

- [ ] **Step 2: 在配置中新增 DSL v2 入口**

```kotlin
object StackUpUpConfig {
    @JvmField
    var enableDslRules: Boolean = true

    @JvmField
    var rulesFileName: String = "rules.su"
}
```

- [ ] **Step 3: 在 StackUpUp 中替换旧 ScriptHandler 的主路径**

```kotlin
@JvmStatic
fun reload(registry: IForgeRegistry<Item>) {
    val rulesResult = DslRuleSource.fromFile(File(stackupScriptLocation, StackUpUpConfig.rulesFileName)).load()
    RuleRuntime.replaceSnapshot(rulesResult.snapshot)
    if (rulesResult.errors.isNotEmpty()) {
        for (error in rulesResult.errors) {
            requireNotNull(logger).error(error)
        }
    }
}
```

- [ ] **Step 4: 更新命令重载与中文文档**

```text
/stackup reload
```

命令说明与 README 中必须新增：

```text
1. 旧脚本格式已废弃。
2. 新规则文件默认为 config/stackup/rules.su。
3. 1.12.2 仅支持矿物辞典，不支持标签。
```

- [ ] **Step 5: 跑规则与命令相关测试**

Run: `.\gradlew test --tests "io.alexjoest.stackupup.rules.*" --tests "io.alexjoest.stackupup.limit.*" --info`  
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 提交**

```bash
git add src/main/kotlin/io/alexjoest/stackupup/StackUpUp.kt src/main/kotlin/io/alexjoest/stackupup/StackUpUpConfig.kt src/main/kotlin/io/alexjoest/stackupup/CommandStackUpUpUp.kt src/main/kotlin/io/alexjoest/stackupup/rules/io README.md docs/DSL-v2-迁移说明.md docs/DSL-v2-规则示例.md
git commit -m "feat: wire dsl rules into runtime reload"
```

### Task 5: 接入 MixinBooter 并迁移固定目标补丁

**Files:**

- Modify: `build.gradle.kts`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/StackUpUpCore.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/bootstrap/StackUpLateMixins.kt`
- Create: `src/main/resources/mixins.stackupup.early.json`
- Create: `src/main/resources/mixins.stackup.late.json`
- Create: `src/main/kotlin/io/alexjoest/stackupup/mixin/early/ItemMixin.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/mixin/early/ItemStackMixin.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/mixin/early/PacketBufferMixin.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/mixin/early/NetHandlerPlayServerMixin.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/mixin/late/PacketUtilMixin.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/mixin/late/InventoryHelperMixin.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/mixin/late/RenderItemMixin.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/mixin/late/RenderEntityItemMixin.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/mixin/late/ServerRecipeBookHelperMixin.kt`

- [ ] **Step 1: 写引导层失败测试或最小启动断言**

```kotlin
@Test
fun `mixin 配置文件名应保持稳定`() {
    assertEquals(listOf("mixins.stackupup.early.json"), StackUpUpCore().mixinConfigsForTest())
}
```

- [ ] **Step 2: 让 coremod 同时实现 IEarlyMixinLoader**

```kotlin
class StackUpUpCore : IFMLLoadingPlugin, IEarlyMixinLoader {
    override fun getASMTransformerClass(): Array<String> = arrayOf("io.alexjoest.stackupup.core.DynamicCompatTransformer")

    override fun getMixinConfigs(): List<String> = listOf("mixins.stackupup.early.json")
}
```

- [ ] **Step 3: 增加 Late Mixin Loader**

```kotlin
package io.alexjoest.stackupup.bootstrap

import zone.rong.mixinbooter.ILateMixinLoader

class StackUpLateMixins : ILateMixinLoader {
    override fun getMixinConfigs(): List<String> = listOf("mixins.stackup.late.json")
}
```

- [ ] **Step 4: 迁移 Item 与 ItemStack 补丁**

```kotlin
@Mixin(Item::class)
abstract class ItemMixin {
    @Inject(method = ["getItemStackLimit"], at = [At("RETURN")], cancellable = true)
    private fun stackupup$applyRules(stack: ItemStack, cir: CallbackInfoReturnable<Int>) {
        val item = stack.item.registryName?.toString() ?: return
        val mod = stack.item.registryName?.namespace ?: return
        cir.returnValue = RuleRuntime.limitService().resolve(
            StackIdentity(item, mod, stack.metadata, if (stack.item is ItemBlock) "block" else "item"),
            cir.returnValue,
            RuleRuntime.oreDictIndex().getOreNames(item, stack.metadata)
        )
    }
}
```

- [ ] **Step 5: 迁移网络与渲染固定目标补丁**

Run: `.\gradlew compileKotlin test --info`  
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 提交**

```bash
git add build.gradle.kts src/main/kotlin/io/alexjoest/stackupup/StackUpUpCore.kt src/main/kotlin/io/alexjoest/stackupup/bootstrap src/main/kotlin/io/alexjoest/stackupup/mixin src/main/resources/mixins.stackupup.early.json src/main/resources/mixins.stackup.late.json
git commit -m "feat: add mixinbooter fixed-target patches"
```

### Task 6: 收缩旧 ASM 到动态兼容适配层并做回归验证

**Files:**

- Modify: `src/main/kotlin/io/alexjoest/stackupup/core/DynamicCompatTransformer.kt`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/core/ClassHierarchyTracker.kt`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/StackCompat.kt`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/core/MaxStackConstantPatch.kt`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/core/NetHandlerPlayServerPatch.kt`

- [ ] **Step 1: 写失败的动态适配测试或最小 hook 断言**

```kotlin
@Test
fun `动态适配层只负责返回值转接`() {
    val result = StackCompat.applyDynamicStackLimit("gregtech:gt.metaitem.01", "gregtech", 11305, "item", 64, setOf("ingotSteel"))
    assertEquals(64, result)
}
```

- [ ] **Step 2: 把帮助方法收口到单一入口**

```kotlin
object StackCompat {
    @JvmStatic
    fun applyDynamicStackLimit(
        itemId: String,
        modId: String,
        meta: Int,
        type: String,
        baseLimit: Int,
        oreNames: Set<String>
    ): Int {
        return RuleRuntime.limitService().resolve(
            StackIdentity(itemId, modId, meta, type),
            baseLimit,
            oreNames
        )
    }
}
```

- [ ] **Step 3: 删除已被 Mixin 覆盖的固定目标 ASM 路径**

```kotlin
when {
    transformedName == "net.minecraft.item.ItemStack" -> return basicClass
    transformedName == "net.minecraft.network.PacketBuffer" -> return basicClass
    transformedName == "net.minecraft.network.NetHandlerPlayServer" -> return basicClass
}
```

- [ ] **Step 4: 保留并收窄动态接口/继承补丁**

```kotlin
if (ClassHierarchyTracker.isImplements(transformedName, "net.minecraft.inventory.IInventory")) {
    consumer = consumer.andThen(MaxStackConstantPatch.patchMaxLimit("getInventoryStackLimit", "func_70297_j_"))
}
```

并为 `Item` 子类重写的 `getItemStackLimit(ItemStack)` 增加统一后处理 hook。

- [ ] **Step 5: 跑完整验证**

Run: `.\gradlew test compileKotlin runClient --args "--username Developer" --info`  
Expected: `test` 与 `compileKotlin` 成功；客户端能进入主菜单且无 `Mixin apply failed` / `ClassFormatError`

- [ ] **Step 6: 提交**

```bash
git add src/main/kotlin/io/alexjoest/stackupup/core src/main/kotlin/io/alexjoest/stackupup/StackCompat.kt
git commit -m "refactor: shrink asm to dynamic compatibility layer"
```

## 自检

### 规格覆盖

1. `metadata` 独立堆叠上限：Task 2、Task 3、Task 6
2. GregTech `gt.metaitem` 精确匹配：Task 2、Task 3、Task 6
3. 矿物辞典 `ore = ...`：Task 2、Task 3、Task 4
4. `MixinBooter` 接入：Task 5
5. 精简 ASM：Task 6
6. 中文文档与迁移说明：Task 4

### 占位与一致性检查

1. 所有新增文件路径均已明确。
2. 所有关键命令均给出可执行形式。
3. 规则语法全程使用 `=`、`in`、`&&`、`||`，未混入 `==`。
4. 所有任务围绕同一内核：`CompiledRule`、`RuleSnapshot`、`StackLimitService`。






> **已过时**
