# AGENTS.md

## 适用范围与优先级

本文件是本项目唯一的根级项目与 GPT 代理规范，适用于仓库内所有调查、计划、文档和实现任务。用户当前要求优先；其后依次适用本文件、更深层目录中的
`AGENTS.md`（如存在）以及项目既有约定。本文件不得放宽用户要求，也不得把计划、推测或历史记录写成当前实现。

任务点名的交接材料、源码、测试和外部资料只能作为证据使用；证据不足必须明确写出未知，不得按类名、注释或经验补全事实。

## 任务输入、输出与状态

每项任务开始前明确记录：

- **目标**：用一句话写出可验收的完成条件，并区分调查、计划、文档和实现。
- **允许修改**：列出精确文件路径；未列出的路径一律不可写。
- **禁止事项**：列出不能做的实现、命令、依赖和外部操作；文档任务不得夹带生产实现。
- **基线证据**：注明已读规范、源码位置、测试和外部资料；第三方没有源码时写 `无源码不可判定`，并列出缺失的 jar。
- **安全不变量**：写明容量守恒、数据不丢失、Fail Fast 等本任务必须保持的底线。
- **验证方式**：列出要运行的命令、检查的文件和通过判据；不能运行时说明原因，不得用推测代替验证。
- **代理分工与租约**：声明作者、独立复核者、允许修改的文件租约和交接边界。

任务输出必须包含以下栏目，不得用空白项、占位文字或未完成的省略号掩盖缺口：

1. **状态**：只能使用 `PASS`、`FAIL`、`BLOCKED`、`UNKNOWN`、`PARTIAL` 之一，并说明理由。
2. **实际变更**：逐项列出真实修改的路径和内容；未修改的文件不得写成已修改。
3. **证据**：给出源码路径与行号、测试结果或外部来源；不足时写 `无源码不可判定` 或 `UNKNOWN`。
4. **验证**：列出实际执行的命令及结果，区分通过、失败和未执行。
5. **剩余风险**：说明阻塞、未知和未覆盖场景，以及下一步所需输入；不得把风险改写成通过。
6. **复核**：列出独立复核者及其结论。作者不得自审；未完成独立复核不得宣称 `PASS`。

状态含义固定如下：

- `PASS`：范围内交付完整，证据和验证满足要求，并已由非作者复核。
- `FAIL`：已执行，但验收条件或安全不变量未满足。
- `BLOCKED`：因权限、依赖、环境或缺失输入无法继续，且已明确阻塞点。
- `UNKNOWN`：现有证据不足以判定，尤其是第三方缺少源码时；不得包装成成功。
- `PARTIAL`：只完成了可明确交付的部分，仍有明确未完成项；不得当作 `PASS`。

## 代理分工

- **Luna** 默认负责调查、文档、小任务和普通复核。
- **Terra** 只用于跨模块冲突、容量或数据安全底线、高风险裁决；不得把普通工作默认升级给 Terra。
- 代理层级可伸缩，不强制三级。若采用多层协作，主代理维护全局目标和最终边界，协调代理拆分任务并复核，执行代理只修改精确租约范围。任何层级都必须如实报告失败和未知，不得互相包庇。

## 文件租约与保护既有改动

- 编辑前声明精确路径和允许范围；一个文件同时只允许一个作者持有写租约。
- 编辑前先读取目标文件并检查现有工作区改动；只做最小、可审阅的修改。
- 只改租约内文件，不因格式化、重命名、自动导入或生成物波及其他路径。
- 既有未提交改动属于受保护状态，不得覆盖、清除、回滚、重建或用等价手段绕过保护。
- 禁止使用 `git restore`、`jj restore`、`git reset`、`git checkout`、`git rebase` 等方式清理现场。
- 未经用户明确要求，不提交、推送、删除文件或修改外部共享状态。
- 文档任务默认只改任务点名文件；经明确授权的文档收口可迁移、重写或删除任务清单列出的 `docs` 文档，并可对 `README.md`、
  `README.en.md`、`START_HERE.md` 和 `AGENTS.md` 做链接、路径、状态整合；不得借此改生产说明正文、源码、测试、构建配置或其他未授权文件。

## 项目概览

本项目是 Minecraft **1.12.2**、Forge **14.23.5.2847** 的堆叠上限模组：文本 DSL 规则决定每个 `ItemStack` 的动态上限，Mixin
与少量窄范围 ASM 使原版和第三方库存路径承载该上限。构建使用 Gradle 9.4、RetroFuturaGradle、Java 8 toolchain、Kotlin
2.3.0；运行时使用 Forgelin-Continuous。Kotlin coremod 加载入口 `StackUpUpCore` 同时承担 `IFMLLoadingPlugin` 与
`IEarlyMixinLoader` 职责；Java core/early transformer 与全部 Mixin 位于 `src/main/java`。

### 目录职责

- `src/main/kotlin`：业务逻辑、规则内核、运行态、配置、兼容层和开发自动化的 Kotlin 实现，并包含 Kotlin coremod 加载入口
  `StackUpUpCore`。
- `src/main/java`：Java core/early transformer 与全部 Mixin；其中 core/early transformer 必须遵守纯 Java 约束。
- `src/test/kotlin`：JUnit 5 测试；`gregtech/` 下的最小手写 stub 用于测试，不以反射代替 stub。
- `dev/`：只在自动化验收开启时加载的服务端/客户端驱动、探针和桥接逻辑。
- `config/`：运行配置及 `MixinToggles` 等开关；规则文件的实际加载顺序由规则管线决定。
- `local-dev-mods/` 与 `run/mods/`：本地依赖和 FML 扫描目录，语义不同，必须按下文规则使用。

## 构建、测试、运行与 Workspace Bridge

工具选择遵循“轻量文本工具 → 本地命令行 → IDE 语义工具”的顺序：

- 简单文本读取、搜索、替换优先使用 `Read`、`Glob`、`Grep`、`Edit`；普通搜索和简单替换不得调用 IDE MCP。
- Scoop 已提供 `rg`、`fd`、`bat`、`ast-grep`、`difft`、`tokei` 等本地工具，适合搜索、文件定位、带上下文查看、结构匹配、差异和统计；Windows shell 的复杂命令可使用 `pwsh`。
- 需要 IDE 语义、Usages、符号解析、跨文件重命名或改签名、检查、格式化、Gradle 同步、构建或运行配置时，优先使用地址为
  `http://127.0.0.1:63441/api/v1` 的 Workspace Agent Bridge。依赖/SDK 查询以工具返回的真实来源为准，跨文件变更先确认 Usages。
- Bridge 不可用时必须记录失败原因，再采用安全的替代方案；不得默默假设 Bridge 可用或任务已完成。

Windows 的 Gradle 入口是 `.\gradlew.bat`；常用任务包括 `test`、`test --tests "*StackLimitServiceTest"`、`spotlessCheck`、
`spotlessApply`、`runClient`、`runServer` 和 `runServerAutoTestMatrix`。Gradle 生命周期任务优先通过 Bridge/IDE 生命周期能力执行，
不要在 shell 中裸跑长任务。`runClient`、`runServer` 和自动验收默认不自动开启；自动验收可用 `run*AutoTest` 任务或
`-PstackupupDevAutoTest=true` 开启。参数链使用 `-PstackupupDevAutoTest*` 到 `-Dstackupup.dev.autoTest.*`；旧的 `stackup*` 前缀只作
fallback，不得扩散。修改 mapping 相关的 `gradle.properties` 后运行 `setupDecompWorkspace`。文档或计划引用验证项时，必须说明实际运行的
是 Gradle 测试、自动验收任务还是静态检查；未执行不得写成已通过。

## 规则数据流与 DSL 边界

规则运行链保持单向、不可变快照和原子替换：

```text
.su / .su.md
  → RuleSourceLocator.resolveLoadOrder()    文件顺序
  → RuleReloadPipeline.loadDslRules()      Markdown/DSL 分流、解析、编译、合并
  → RuleSnapshot                           不可变结果 + RuntimeContextRequirements
  → RuleRuntime.replaceRuntime()            原子替换全局运行态
  → StackLimitService.resolve(StackContext) 热路径求值与缓存
  → StackLimitHooks.*                       Mixin/ASM 的唯一调用面
```

`RuleRuntimeCoordinator` 是 `reload`、`lastReport`、`rulesFile` 和示例同步的唯一协调层；不要把这些职责散回 `StackUpUp`、命令层或
proxy。`StackUpUp` 只负责 Forge 生命周期编排。

规则按顺序覆盖：后命中的规则在前一条结果上继续执行 set、加、减、乘、除。加载顺序为 `<save>/data/stackupup/main.su.md` →
`config/stackupup/*.su.md` → 仅当 `main.su` 缺失时读取旧 `stackupup-rules.su` → `<save>/data/stackupup/world.su` →
`config/stackupup/*.su` → `user.su`。

- `parse/` 维持 `DslTokenizer` → `DslTokenCursor` → `DslParser`，解析器只产出 AST；`compile/` 由 `RuleCompiler`、
  `RuleConditionCompiler` 产出 `CompiledRule`。
- `io/DslRuleSource` 是单行、单文件和多文件规则输入的唯一入口；字面量通配及 `item@meta` 语法糖由
  `field/RuleLiteralMatcherCompiler` 负责。
- `RuleField`、`ComparisonOperator`、`RuleStepKind` 是强类型枚举；字段名、运算符和动作类型禁止裸字符串分发。`RuleField`
  保持静态自描述，不引入动态注册表。
- matcher 和缓存键提取器直接读取 `StackContext`，不增加中间上下文复制层。昂贵或可选上下文由 `RuleField.contextProviders`
  聚合为 `RuntimeContextRequirements` provider plan，`StackContextResolver` 只执行已编译 plan，不按字段名分支。
- `RuleContextRequirement` 仅作旧兼容和诊断投影。`RuleStateService` 的三态必须保持：store 不可用为 `null`，key 缺失为
  `false`，`setState` 只反映底层是否真正改写文件。`reload` 不隐式刷新示例文件，`syncExampleFiles()` 是显式初始化动作。
- 1.12.2 `ResourceLocation` 的 path 可以含多个冒号。DSL 字面量解析不得一概拒绝多冒号，也不得未经实际
  grammar、源码和测试证据把第三段一概解释为 meta。

## 容量不变量、转发 wrapper 与 remainder 禁令

核心不变量是： **对外广告容量不得大于真实写入容量**。容量判断必须依据目标对象的实际写入路径，不得依据类名、`== 64`
哨兵或未知实现的主动表态。

- `SlotLimitMixin` 仅在 `inventory.getInventoryStackLimit() > 0` 时取
  `min(dynamicLimit, inventory.getInventoryStackLimit())`；非正值路径的安全性当前未被保证。
- `SlotItemHandlerMixin` 仅在原值恰为 `64` 时保持原值；其他值会进入 `Math.max(original, dynamic)`，这是当前已知限制。
- 未知 `IItemHandler` 不动态扩容；不得通过未知类名或接口实现推断其可承载容量。
- 已知模组用 late Mixin 扩展真实的 `getInventoryStackLimit()` 或 `getSlotLimit()`，槽位广告必须自然跟随真实写入面。
- Forge `EntityEquipmentInvWrapper#insertItem` 会计算上限并返回 `remainder`；`setStackInSlot`/vanilla setter
  是另一条写入路径，不得用前者覆盖后者；不得写成“无余量必吞”，必须以对应版本和真实写入路径的证据判定。
- Forge 或第三方转发 wrapper 不能因为 wrapper 自身返回某个数就擅自扩大；广告值和写入面的数值来源必须一致。
- 禁止在真实写入后重新计算余量、回填、重试、补偿，或以这些动作改变业务结果。守恒审计只能观察和报告，不能修正结果；必须区分
  `simulate` 与真实写入，并对真实写入记录 `offered`、落库量、`remainder`、目标类和 `slot`，产出机器可读报告。
- 机器类兼容先查真实写入容量，再考虑 `Slot#getItemStackLimit` 或 GUI 广告。
- `resolveInventoryClampLimit` 仍有两个调用方；删除或改动前必须逐一定位和处理，不能按“没有调用方”删除。

本地化修改必须先建立同一 Minecraft 客户端经 Forge 资源管理器完整重载链的真实 F3+T 基线，再验证修复；局部
`LanguageMap.replaceWith` 不算完整重载证据，不能只依据某个局部调用推断行为。第三方 jar 读不到源码时统一记录 `无源码不可判定`
，并列出缺失 jar；不得用类名、方法名或我方注释冒充写入路径证据。

## Mixin/ASM 分层、coremod、依赖 jar 与开发自动化

### 核心参考与 DeepWiki 研究规则

- 所有涉及 `Mixin`、`ASM`、`classloading`、`transformer`、`refmap`、`mappings`、`MixinBooter`、`CleanMix` 或 `MixinExtras` 的任务，必须先使用 DeepWiki MCP 做架构、生命周期和调用链调查；优先 `read_wiki_structure`、`read_wiki_contents`，必要时再用 `ask_question`，随后用三个官方仓库及本项目代码、依赖核对事实。
- 固定参考入口及职责：
  - [CleanroomMC/MixinBooter](https://github.com/CleanroomMC/MixinBooter)：Minecraft 1.12.2 旧 Forge 侧的 bootstrap、early/late loading 与兼容桥；
  - [CleanroomMC/CleanMix](https://github.com/CleanroomMC/CleanMix)：`MixinBootstrap/bootstrap`、Mixin 核心、launcher service、transform pipeline、refmap 与 annotation processor（AP）；其中 `MixinBootstrap/bootstrap` 属于 Mixin 核心/launcher 层，不是 MixinBooter 的 Forge 侧 bootstrap/兼容桥；
  - [CleanroomMC/MixinExtras](https://github.com/CleanroomMC/MixinExtras)：扩展注入 API 与版本兼容边界。
    三者职责和依赖边界不同，不得混写为同一个库；具体 API、配置及早/晚加载状态可能随版本变化，必须核对官方 README/源码和本项目实际依赖，不得把 DeepWiki 示例版本当作事实。
- 当前项目在 `build.gradle.kts:434` 的构建配置中使用并锁定 `zone.rong:mixinbooter:10.7`；early/late 接口与加载阶段规则必须以该锁定版本为准。若上游 11.x 已改变或淡化 early/late 机制，升级到该版本时不得把旧规则无条件套用到升级后的版本，必须核对该版本实际 manifest、`MixinConnector`、注册路径和官方源码；不得把未来版本写成当前项目已使用。
- DeepWiki 仅作为架构、生命周期、调用链和设计意图的研究入口；精确 API、版本、配置和行为以官方仓库 README/源码、当前依赖及本地构建产物为准。发现冲突时必须记录冲突，并以本项目实际运行版本和源码为准；不得凭记忆臆测 API。
- 若 DeepWiki 未索引、不可用或结果不完整，必须记录仓库、调用类型/页面、失败原因和缺口，并安全降级到官方 GitHub README/源码、本项目当前依赖及本地构建产物；尤其 `CleanroomMC/MixinExtras` 当前未被 DeepWiki 索引时，仍须将其官方仓库作为一等参考，禁止因失败而跳过核对。
- L2/L3 子代理调查优先使用 DeepWiki；仅在复杂问题需要时扩大到源码搜索或 IDE 语义调查。DeepWiki 失败或不完整时按上一条安全降级，不得把推测写成事实。
- 涉及上述三个仓库的任何变更，任务交接与验证记录必须列明查阅的官方仓库、DeepWiki 页面/问答、失败记录（如有）以及版本依据；缺少版本或运行证据时必须标为 `UNKNOWN`，不得宣称通过。

### Mixin 与 ASM 分层

仅当当前依赖版本仍提供 early/late 接口且目标处于对应生命周期时，优先采用对应的 Mixin；若该版本已废弃或取消
early/late 分层，则按该版本实际注册路径实现。仅当 Mixin 能力不足时才使用窄范围 ASM 兜底。新增兼容按“目标类、方法、签名明确 → late Mixin；原版或 Forge 基础路径 → early Mixin；已被 Mixin 接管 → 核对
`FixedCompatTargets` 跳过表”的顺序判断。

- `mixin/early/` 覆盖 `ItemStack`、`Item`、`Container`、`Slot`、`SlotItemHandler`、玩家库存、`EntityItem` 合并、`PacketBuffer`
  、命令和渲染等固定基础路径。
- `mixin/late/` 由 `StackUpUpLateMixinLoader` 按 `isModPresent` 与 `config/MixinToggles` 排队，每个兼容模组保持独立配置；现有兼容范围包括
  AE2、ActuallyAdditions、BrandonsCore、CyclopsCore、EnderIO、IC2、Mantle、RefinedStorage、StorageNetwork、IntegratedDynamics、LimeLib
  和 ImmersiveEngineering。
- late 模块表不等于 `FixedCompatTargets`；只有需要让动态 ASM 主动避让的固定类才进入跳过表。
- 静态目标方法的 Mixin handler 使用 Java `private static`，不用 Kotlin `companion object + @JvmStatic`。重载方法写完整
  descriptor，例如 `getInventoryStackLimit()I` 与 `getInventoryStackLimit(I)I` 必须区分。
- 当前项目按 MixinBooter `10.7` 的运行时打包关系使用 MixinExtras；升级 MixinBooter 或单独引入 MixinExtras 时，必须核对实际 provider、坐标、
  版本和 shaded/standalone 打包方式；继续保留包裹原调用使用 `@WrapOperation`、修改表达式结果使用 `@ModifyExpressionValue`，不得新增
  `@Redirect`。
- `StackCountCodec` 以魔数字节加 int 扩展数量编码，数量 `≤64` 保持原版单字节。

### coremod early path

`src/main/java` 下的 coremod early path 必须保持纯 Java：

1. 禁用 `kotlin.collections`、`sequences`、`text`、`io`、`ranges`、lambda、方法引用和 `use {}`。
2. 禁止会生成 `WhenMappings` 或 `NoWhenBranchMatchedException` 的 `enum + when`；使用显式循环、JDK 集合和朴素条件。
3. `DynamicCompatTargetProfile` 保存目标类型和候选方法名配置，但 `DynamicCompatMethodProbe` 当前仍独立按方法名扫描，不按
   descriptor 确认签名；补丁 helper 的 owner/name/descriptor 仍在 patch 代码中定义，因此该 profile 不是完整的单一事实源；
   `FixedCompatTargets` 是固定跳过目标的唯一事实源。
4. 先检查当前类是否声明候选上限方法；无命中直接跳过，不做层级分类。命中后再检查是否真正声明目标方法，纯继承类跳过。
5. `ClassHierarchyRepository` 只读父类与接口签名，不建立完整 `ClassNode`；`DynamicCompatMethodProbe`
   当前仅按方法名扫描当前类直接声明的方法，不按 descriptor 确认签名。
6. `transform` 必须容忍 `transformedName` 与 `basicClass` 为空。

### 本地依赖 jar 与 dev 自动化

- `local-dev-mods/`（gitignored）中的不带 `ContainedDeps` 的普通 `*.jar` 进入编译期和运行时 classpath；带 `ContainedDeps` 的
  jar 仅进入编译期 classpath，并复制到 `run/mods/` 由 FML 目录扫描，不能进入运行时 classpath。
- `run/mods/*.jar` 只进入编译期 classpath 与 FML 目录扫描，不进入运行时 classpath，否则会产生 duplicate mods；带
  `ContainedDeps` 的 jar 必须通过目录扫描展开内嵌依赖。对 `.jar.disable` 的停用与 classpath wiring 语义当前尚未被证实。
- `DevAutomationConfig` 读取系统属性；server/client driver 只在自动化开启时注入临时规则并运行兼容探针。探针必须使用当前
  `contextRequirements()` 解析上下文。
- 开发自动化的目标门槛是：可用性检查只把“明确缺失”视为 skip；链接异常必须记为失败。GT 内建矩阵只有在 `gregtech`
  未加载时才允许整组跳过；`dev/` 不新增只包一层调用的薄文件。当前实现仍有异常分类缺口，本文规范不构成已通过证明。
- 反射禁止用于功能、测试和审查。只有通用动态框架、注入式桥接或确实不可达对象的场景可申请例外，并须先完整验证所有入口点、说明不可达原因并保留诊断；开发自动化桥接也不得扩大此例外。

## jj 工作流、格式与最终验证

### 实现、接口与测试规则

- Fail Fast 是实现目标门槛：输入、规则、目标签名和状态不符合约束时尽早失败并留下可定位日志。不得刻意添加兼容、静默回退、吞错或空
  `catch`；核心逻辑必须避免崩溃时也要保留明确诊断。当前实现仍有异常分类缺口，本文规范不构成已通过证明。
- 不得留下占位实现、假最小实现或未验证的“已完成”结论；不得通过隐藏日志、删除证据、修改测试期望或代理互相包庇掩盖问题。
- 面向接口编程，行为优先由接口对接，具体实现限于实现层和独立逻辑层。命名采用 `Xxx` 表示接口、`XxxImpl`
  表示接口实现；新增接口应说明接口简介、每个类/方法/变量首次出现的动机与需求，以及每个成员的作用。
- 大对象构造优先采用 Builder；避免新增会膨胀为超级对象的 `XxxService`、`XxxManager` 或 `XxxController`，逻辑应放在接口实现层和独立逻辑层。
- TDD 只用于代码逻辑验证。行为测试必须调用真实代码完成行为验证，不能用源码 `contains`
  冒充行为验证；静态配置/字节码护栏可做结构检查，但必须标明为结构检查。若功能要删除，只验证一次后连同删除验证测试一起移除，不保留专门验证删除的测试。JUnit
  5 测试方法使用反引号描述式名称。

### jj 纪律

本项目使用 jj 管理 working-copy 快照，禁止用 git 绕过 jj；没有 staging area，改文件即属于当前 change。

```text
jj st
jj diff
jj log
jj describe -m "describe current change"
jj new
jj bookmark set master -r @-
```

用 `jj describe` 替代提交操作；只有用户明确要求时才执行 `jj git push`。不得用 git 或 jj 恢复、清理或绕过既有改动。

### 格式与最终验证

- Kotlin/Java 遵循 Spotless + ktlint 的 `intellij_idea` 风格，缩进 4，最大行宽 160；文件使用 UTF-8、LF 和末尾换行，`.bat` 使用
  CRLF。注释以中文为主，说明动机而不是复述代码。
- 改动后先检查目标文件最终内容与 `jj diff`，再确认没有租约外的新增修改。根据变更范围运行相关 Gradle 测试、`spotlessCheck`
  、运行任务或静态检查，并如实记录未执行项目。
- 改动 coremod、Mixin 或自动化参数后至少覆盖 `CoremodHierarchyBytecodeSafetyTest`、`EarlyMixinBytecodeSafetyTest`、
  `MixinBooterIntegrationTest` 和 `runServerAutoTest`；不得把仅静态检查写成测试通过。

### 现行文档

- `AGENTS.md` 是唯一的代理协作规范；其他文档不取代它。
- [START_HERE](docs/agent/START_HERE.md)
- [硬约束](docs/agent/2026-04-18-hard-rules.md)（hard-rules）：领域门槛。
- [兼容性决策记录](docs/agent/compatibility-decision-record.md)（decision record）：证据与决策。
- [Mixin 生态与注入最佳实践](docs/agent/mixin-%E7%94%9F%E6%80%81%E4%B8%8E%E6%B3%A8%E5%85%A5%E6%9C%80%E4%BD%B3%E5%AE%9E%E8%B7%B5.md)：MixinBooter/CleanMix/MixinExtras/Sponge Mixin、Shadow/Inject/Redirect/WrapOperation 等代理操作手册；现行代理参考。
- [借鉴仓库与重构对照](docs/agent/%E5%80%9F%E9%89%B4%E4%BB%93%E5%BA%93%E4%B8%8E%E9%87%8D%E6%9E%84%E5%AF%B9%E7%85%A7.md)：`C:\dev\mc\_tmp\StackUp` 与 `biggerstacks-Unofficial` 的只读证据对照；现行研究记录。
- [重构任务清单](docs/agent/%E9%87%8D%E6%9E%84%E4%BB%BB%E5%8A%A1%E6%B8%85%E5%8D%95.md)：仅为规划。
- [runServer 自动化回归](docs/runServer-%E8%87%AA%E5%8A%A8%E5%8C%96%E5%9B%9E%E5%BD%92.md)
- [实现与兼容性说明](docs/StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md)
- [DSL v2 规则示例](docs/DSL-v2-%E8%A7%84%E5%88%99%E7%A4%BA%E4%BE%8B.md)
- [CHANGELOG.md](CHANGELOG.md)：发布记录。
