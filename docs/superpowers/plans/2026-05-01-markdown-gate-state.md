# StackUpUp Markdown Gate 与 State 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把规则系统升级为单文件 Markdown 容器，支持 `# state` 持久化区、`# rules` gate 区、`state("xxx")` 读取和无括号 gate 表达式，同时保持热路径只读编译快照。

**Architecture:** 采用“区域扫描器 + 区域内轻量语义解析 + 编译后快照”的结构。`state` 只负责持久化布尔值，`rules` 只负责 gate 层级与 DSL 规则正文，二者通过 reload 时的签名和状态表联动。运行时不解析 Markdown，只消费已编译的 `RuleSnapshot` 和已解析的 gate state。

**Tech Stack:** Kotlin 2.3、Java 8、Forge 1.12.2、JUnit 5、现有 StackUpUp 规则内核、现有 `RuleRuntime` / `RuleRuntimeCoordinator` / `RuleFileLocator` / `RuleSourceLocator`

---

## 计划文件结构

**Create:**

- `src/main/kotlin/io/alexjoest/stackupup/rules/io/MarkdownRuleSource.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/io/MarkdownContainerScanner.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/io/MarkdownGateParser.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/io/MarkdownStateParser.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/io/MarkdownBlockModel.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/io/RuleStateStore.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/io/RuleGateExpression.kt`
- `src/test/kotlin/io/alexjoest/stackupup/rules/io/MarkdownContainerScannerTest.kt`
- `src/test/kotlin/io/alexjoest/stackupup/rules/io/MarkdownGateParserTest.kt`
- `src/test/kotlin/io/alexjoest/stackupup/rules/io/MarkdownStateParserTest.kt`
- `src/test/kotlin/io/alexjoest/stackupup/rules/io/MarkdownRuleSourceTest.kt`
- `src/test/kotlin/io/alexjoest/stackupup/rules/io/RuleStateStoreTest.kt`

**Modify:**

- `src/main/kotlin/io/alexjoest/stackupup/rules/io/DslRuleSource.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/io/RuleConditionalPreprocessor.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/io/RuleGateContext.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/io/RuleReloadPipeline.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/io/RuleFileLocator.kt`
- `src/main/kotlin/io/alexjoest/stackupup/rules/io/RuleSourceLocator.kt`
- `src/main/kotlin/io/alexjoest/stackupup/RuleRuntimeCoordinator.kt`
- `src/main/kotlin/io/alexjoest/stackupup/StackUpUpConfig.kt`
- `src/main/kotlin/io/alexjoest/stackupup/CommandStackUpUp.kt`
- `src/main/kotlin/io/alexjoest/stackupup/StackUpUp.kt`
- `src/main/resources/assets/stackupup/lang/en_us.lang`
- `src/main/resources/assets/stackupup/lang/zh_cn.lang`
- `docs/agent/2026-04-28-rule-markdown-gates.md`
- `docs/DSL-v2-规则示例.md`
- `docs/DSL-v2-迁移说明.md`

---

### Task 1: 建立 Markdown 容器解析骨架

**Files:**

- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/io/MarkdownContainerScanner.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/io/MarkdownBlockModel.kt`
- Create: `src/test/kotlin/io/alexjoest/stackupup/rules/io/MarkdownContainerScannerTest.kt`

**目标**

把单文件 Markdown 按顶层区域切分，识别 `state` 区、`rules` 区，以及规则区中的标题层级和 fenced code block。

**要点**

- 只把 `# state` 和 `# rules` 作为第一版有效区域。
- 其他顶层标题保留为文档，不参与编译。
- 只承认三类语义构件：标题、`-` state 项、fenced code block。
- 语义行不允许同行注释。
- 其他文本原样保留，供写回时继续使用。

**验收**

- 能从一个 `.su.md` 文件中提取出 state 区和 rules 区。
- 能识别标题深度的嵌套关系。
- 能识别代码块语言名，只将 `stackupup` / `su` 视为规则块。

**验证**

- 先写 scanner 的失败测试。
- 再实现最小扫描逻辑。
- 运行相关测试直到通过。

---

### Task 2: 实现 gate 表达式解析与求值

**Files:**

- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/io/MarkdownGateParser.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/io/RuleGateExpression.kt`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/rules/io/RuleGateContext.kt`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/rules/io/RuleConditionalPreprocessor.kt`
- Create: `src/test/kotlin/io/alexjoest/stackupup/rules/io/MarkdownGateParserTest.kt`

**目标**

把 gate 限定成无括号的纯布尔表达式，支持函数调用组合与 `&&` / `||` / `!`。

**第一版函数**

- `state("name")`
- `modLoaded("modid")`

**要点**

- gate 里不能出现裸变量。
- 不支持括号，降低解析复杂度。
- `state("name")` 只读 observable bool。
- `modLoaded("modid")` 读取当前已加载模组。
- 解析失败时返回可诊断错误，不要在热路径抛异常。

**验收**

- `state("phase1") && modLoaded("storagenetwork")` 可解析。
- `!state("phase1") || modLoaded("enderio")` 可解析。
- `phase1` 这种裸变量会被拒绝。
- 带括号的表达式会被拒绝。

**验证**

- 先写 gate 解析失败测试。
- 再补纯布尔求值。
- 最后接回 preprocessor。

---

### Task 3: 实现 state 区解析与持久化存储

**Files:**

- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/io/MarkdownStateParser.kt`
- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/io/RuleStateStore.kt`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/RuleRuntimeCoordinator.kt`
- Create: `src/test/kotlin/io/alexjoest/stackupup/rules/io/MarkdownStateParserTest.kt`
- Create: `src/test/kotlin/io/alexjoest/stackupup/rules/io/RuleStateStoreTest.kt`

**目标**

把 `# state` 区解析成 observable bool 表，并支持写回同一个 Markdown 文件时保留注释行。
同时建立变更通知入口：state 写入后只标记 dirty，不直接在写入点触发 reload；统一在服务端 tick 或显式刷新点做 gate 复算和合并 reload。

**要点**

- state 只接受 `name = true/false` 这一类声明。
- `- name = true` 这种 Markdown 列表风格也可以接受。
- state 持久化仍在 Markdown 文件内，不默认拆 companion `.state` 文件。
- 写回时保留非语义注释行。
- 未知 state key 发 warning，但不打断整个 reload。

**验收**

- 读取 state 区能得到完整布尔表。
- 写回后非语义注释仍存在。
- 写回后规则区正文不被破坏。
- 同一文件可以同时保存多个 state 值和规则块。

**验证**

- 先写 state 解析测试。
- 再写写回保留注释的测试。
- 最后把 coordinator 接入新的 state store。

---

### Task 4: 把 Markdown 容器接入规则加载链

**Files:**

- Create: `src/main/kotlin/io/alexjoest/stackupup/rules/io/MarkdownRuleSource.kt`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/rules/io/DslRuleSource.kt`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/rules/io/RuleReloadPipeline.kt`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/rules/io/RuleFileLocator.kt`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/rules/io/RuleSourceLocator.kt`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/RuleRuntimeCoordinator.kt`
- Create: `src/test/kotlin/io/alexjoest/stackupup/rules/io/MarkdownRuleSourceTest.kt`

**目标**

让规则加载链优先识别 `.su.md` 容器文件，并在不破坏现有 `.su` 规则文件的前提下完成迁移。
gate 结果变化只在依赖受影响时复算，且如果复算结果没变则复用现有 snapshot，不触发完整 reload。

**要点**

- 允许多个 Markdown 规则文件并存。
- 允许保留旧 `.su` 规则文件作为兼容输入。
- rules 区只编译启用块。
- if 块注释保留为普通 Markdown 文本，不进入 DSL 解析。
- 如果 gate 状态不变，复用已有 snapshot。

**验收**

- `.su.md` 可以被加载并编译成 `RuleSnapshot`。
- 旧 `.su` 文件仍可加载。
- 多个文件的启用块能稳定合并。
- 文件变化和 gate 变化会触发正确的重载。

**验证**

- 先写容器加载失败测试。
- 再实现最小接入。
- 最后做多文件合并回归。

---

### Task 5: 暴露外部 gate 控制接口

**Files:**

- Modify: `src/main/kotlin/io/alexjoest/stackupup/CommandStackUpUp.kt`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/StackUpUp.kt`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/config/ConfigGui.kt`
- Modify: `src/main/resources/assets/stackupup/lang/en_us.lang`
- Modify: `src/main/resources/assets/stackupup/lang/zh_cn.lang`
- Create: `src/test/kotlin/io/alexjoest/stackupup/config/ConfigGuiSourceTest.kt`

**目标**

提供外部命令或脚本驱动的 gate 开关入口，但不允许外部直接注入规则。

**要点**

- `state("xxx")` 读取的是可持久化布尔值。
- 外部只改 state，不改 rule block。
- 配置 GUI 里避免出现 `instance` 这类 Kotlin singleton 泄漏项。
- 命令侧提供最小可用的 get/set/toggle/reload 能力。

**验收**

- 外部可读写 gate state。
- GUI 中只显示有效配置项。
- `instance` 不再出现在玩家可见配置里。

**验证**

- 先写 GUI 泄漏回归测试。
- 再补命令和状态联动。
- 最后跑完整测试。

---

### Task 6: 更新文档与示例

**Files:**

- Modify: `docs/agent/2026-04-28-rule-markdown-gates.md`
- Modify: `docs/DSL-v2-规则示例.md`
- Modify: `docs/DSL-v2-迁移说明.md`
- Modify: `docs/agent/remainder-system.md`（如有必要补充与 gate 无关的说明则跳过）
- Create: `docs/superpowers/specs/2026-05-01-markdown-gate-state-design.md`（已完成，保持同步）

**目标**

把对外格式、语义边界、state 持久化和 gate 约束写给作者和维护者看。

**要点**

- 中文文档优先。
- 明确说明 gate 不支持括号。
- 明确说明 `#`、`-`、fenced code block 只有这些构件有语义。
- 明确说明其余 Markdown 全部作为注释保留。
- 明确说明规则块内部仍可使用 DSL 自己的注释。

**验收**

- 文档能够直接指导作者写出第一版规则文件。
- 文档与实现一致，没有留空洞或矛盾。

**验证**

- 逐段对照设计文档和最终实现。
- 检查示例是否能反映真实语法。

---

### Task 7: 最终回归与兼容性清理

**Files:**

- Modify: `src/test/kotlin/io/alexjoest/stackupup/**`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/rules/io/**`
- Modify: `src/main/kotlin/io/alexjoest/stackupup/config/**`

**目标**

在合并前补齐最容易漏掉的兼容回归，确保 Markdown gate/state 机制不破坏现有 remainder、late mixin、配置 GUI 和规则编译行为。

**要点**

- 现有 remainder 热路径测试必须保持绿色。
- Ender IO / Refined Storage / CyclopsCore 的现有兼容测试必须保持绿色。
- 配置 GUI 中不得再次出现 `instance` 脏项。
- Markdown 容器新增逻辑不得影响旧 `.su` 的加载。

**验收**

- `./gradlew test` 通过。
- 关键规则加载链回归通过。
- 关键配置回归通过。

**验证**

- 先跑局部测试。
- 再跑完整测试。
- 只在完整测试通过后收尾。
> **已过时**
