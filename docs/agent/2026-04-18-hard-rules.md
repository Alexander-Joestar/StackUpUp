# 领域硬门槛

> 状态：PARTIAL（文末「已知限制」与「计划中的任务」所列未实现项未闭合，未完成独立复核，不得记为通过）。

> 通用协作规范见根级 `AGENTS.md`；本文件只记 Mixin 引导、容量安全、开发自动验收和规则内核的项目特有门槛。“当前实现”只表示能在现有源码中定位到的行为；“历史机制”表示已删除或仅用于解释背景的实现，不得当作当前事实；“已知限制”表示证据不足或仍需运行验证；“计划中的任务”不属于当前实现。

## Mixin early path：纯 Java 与注入边界
**当前实现**

- `StackUpUpCore` 是 Forge coremod 入口，但 `getASMTransformerClass()` 返回空数组；它只负责 coremod 生命周期和冲突状态，不执行兼容性字节码改写。
- `StackUpUpMixinConnector` 通过 Sponge Mixin `IMixinConnector` 注册 early 配置及按模组和开关选择的 late 配置；当前兼容注入由显式 Mixin 完成。
- early/late Mixin 类分别位于 `src/main/java/.../mixin/early/` 与 `src/main/java/.../mixin/late/`，目标由 Mixin 配置 JSON 和 `@Mixin` 声明确定，不通过未知类扫描推断目标。
- 当前不存在 `MixinConfigValidator`；配置装载边界是 connector、模块表和 Mixin 配置本身，不能把已删除 validator 描述成当前校验入口。

**门槛**

- `src/main/java/.../mixin/` 的兼容性 Mixin handler 必须保持纯 Java：不得引入 `kotlin.collections`、`kotlin.sequences`、`kotlin.text`、`kotlin.io`、`kotlin.ranges` 及 Kotlin 函数运行时，不得使用 lambda、方法引用、`use {}` 或会生成 `WhenMappings`/`NoWhenBranchMatchedException` 的 `enum + when`；用显式循环、JDK 集合和朴素条件。
- 目标方法签名必须由源码或字节码确认；重载注入必须用完整 descriptor。仅凭类名、接口关系、方法名或字节码中的 `64` 不能证明真实写入容量或目标语义位置。
- 能由 Mixin 表达的返回值修改、表达式结果修改或原调用包裹必须使用相应 Mixin 注入；当前兼容性注入不保留窄范围 ASM、dynamic ASM 或 `FixedCompatTargets` 路径。
- 每次修改 early Mixin、late Mixin 或 connector 后，按改动范围检查对应 Mixin 配置、源码护栏和 `MixinBooterIntegrationTest`；未执行的运行验证不得记为通过。

**历史机制（已删除，以下不是当前规则）**

- 旧版本曾使用 dynamic ASM 链路（包括 `DynamicCompatTransformer`、`CompatibilityLimitPatch`、`DynamicCompatMethodProbe`、`ClassHierarchyRepository`、`FixedCompatTargets` 等类）按类层级、方法名或字节码模式推断兼容目标；`MixinConfigValidator` 也曾作为配置校验概念出现。
- 这些机制已从当前源码删除，不能作为当前数据源、跳过表、测试目标或扩展入口；`FixedCompatTargets` 也不再是当前 Mixin 目标登记或避让表。保留其历史安全教训：方法名或 `BIPUSH 64` 命中不能替代完整 descriptor、常量语义位置和真实写入路径证据。
- 因此不再维护 dynamic ASM 的 early path、窄 ASM 补丁、固定目标表、probe/classifier/patch planner 或对应 bytecode test；当前新增兼容目标必须进入明确的 early/late Mixin 配置并核对实际写入路径。

## Mixin 目标与容量安全边界

- 已登记的 early/late Mixin 目标必须有明确目标类、方法和加载配置；目标名单以 Mixin 配置 JSON、`@Mixin` 声明和 connector 模块表为准。
- Mixin 配置包含类名、connector 模块表或注入命中本身都不能代替对实际写入方法的核对；第三方 jar 没有可核对源码或字节码写入路径时，结论必须写“无源码不可判定”，并列出缺失的 jar。
- 未知 `IItemHandler` 不得作为快速扩容的理由，也不得用 remainder-system 修复吞物品。

## 容量广告、Forge wrapper 与 remainder
**当前实现**

- `SlotLimitMixin` 在 `inventory.getInventoryStackLimit() > 0` 时把动态上限 clamp 到该值；非正值不走该 clamp，不能据此默认广告容量已被限制。`SlotItemHandlerMixin` 的 `getItemStackLimit` 现由 `StackLimitHooks.resolveItemHandlerSlotLimit` 按 `min(slotLimit, itemLimit)` 收敛（无 `Math.max` 分支），其 `getSlotStackLimit` 的独立动态上限注入已移除（T3 收敛，决策记录 §3.5）。
- 1.12.2 Forge 的 `EntityEquipmentInvWrapper` 源码显示：`insertItem` 先取得 `getStackLimit(slot, stack)`，该方法计算 `min(getSlotLimit(slot), stack.getMaxStackSize())`；真实写入只取可接受数量，并在超量时返回剩余 `ItemStack`，因此不能写成“该 wrapper 无余量必吞”。`getSlotLimit` 本体仍区分护甲槽 `1` 与手部槽 `64`；任何改动都必须核对 mixin 后的真实写入路径。
- `resolveInventoryClampLimit` 仍有两个 `InventoryPlayerAddResourceMixin` 调用方：`canMergeStacks` 和 `addResource`。不得按“没有调用方”删除；改动前要逐一处理这两个场景。

**不可协商的门槛**

- 审查一个目标时必须同时核对广告方法（`getInventoryStackLimit`、`getSlotLimit` 或 slot 方法）和实际写入方法（如 `insertItem`、`setInventorySlotContents` 或其 delegate 的 clamp）；不能根据接口实现、类名、包装器返回值或“主动表态”猜测其背后库存能写入多少。
- 真实写入审计必须分开记录 offered、实际落库、原调用返回的 remainder、目标类和 slot；不能把模拟调用与真实调用混为一条证据。
- 不复活 remainder-system：`simulate` 的结果不能替代真实写入证据，禁止在真实写入后重算余量、回填、重试、补偿，或用事后余量改变业务结果。
- `EntityEquipmentInvWrapper`、Forge 转发 wrapper 或任何第三方包装器若要调整容量，必须分别覆盖空槽/已有堆叠、`simulate`/真实写入和 remainder；不能从包装器自身返回值推导未核对的 delegate 容量。

**已知限制**

- `ForgeItemHandlerLimitMixin` 当前只改写 `ItemStackHandler`/`EntityEquipmentInvWrapper` 两个自洽目标的 `getSlotLimit`（T3 收敛，决策记录 §3.5），但这两个目标仍存在未闭合组合：`WrapperCapacityDiagnosticTest` 直接构造 wrapper/backing inventory fixture，仅覆盖该调用路径，未覆盖 early Mixin 应用后的组合路径；因此 wrapper、early Mixin 与 backing inventory 组合的安全性尚未验证，不能据此记为容量守恒通过。
- `Ae2ItemHandlerInsertLimiter.insertCapped` 在未知 handler 分支对模拟/真实插入分片，并重建返回 remainder；方案 A（决策记录 §3.8）后 `AppEngAdaptorItemHandlerMixin` 已不含任何注入、`insertCapped` 不再处于生产热路径，仅由测试护栏直接调用。这不改变“不新增或扩展 remainder-system”的门槛，也不构成通用安全证明；在独立审计完成前必须标为已知限制。

## ResourceLocation、DSL 字面量与资源重载
**当前实现**

- 1.12.2 `ResourceLocation.splitObjectName` 按第一个冒号分隔 namespace，冒号之后整体作为 path；path 可以含多个冒号。
- item 字面量的唯一 metadata 写法是 `@整数` / `@*`（`ItemLiteralSyntax`）；旧 `:meta` 简写已移除，多冒号字面量保持原值（`RuleCompilerTest` 覆盖 `@meta`，`RuleLiteralMatcherTest` 覆盖多冒号 path）。

**门槛与限制**

- DSL 字面量不得按冒号数量一概判非法，也不得未经 grammar、源码和测试证据把第三段一概解释为 meta。任何解析或校验修改都必须先说明实际 grammar，再补对应 tokenizer/compiler 行为验证。
- Forge 的真实资源重载链是 `SimpleReloadableResourceManager.reloadResources` → reload listeners → `LanguageManager.onResourceManagerReload` → `LanguageMap.replaceWith`。`replaceWith` 会替换全局语言表。
- 当前 `RuleMessages.syncLanguage` 由客户端 tick 按语言代码变化触发，并非资源管理器 reload listener。没有实际客户端验证前，不得宣称 F3+T 后本地化仍然正确；F3+T 必须沿真实 Forge 资源重载链验证，而不是只依据某个局部 `replaceWith` 或注入调用。

## 静态 Mixin handler
**当前实现**

- 静态目标方法的示例 `InventoryHelperMixin`、`PacketUtilMixin` 使用 Java `private static` handler；`EarlyMixinBytecodeSafetyTest` 检查 `Companion` 字段和相关字节码依赖。

**门槛**

- 目标方法为静态方法时，handler 必须使用 Java `private static`；禁止 Kotlin `companion object + @JvmStatic`。
- 不要把实例目标方法的普通 handler 与静态目标方法混写成同一约束，也不要把已有局部迁移描述成全量迁移。
- 改动后执行 `EarlyMixinBytecodeSafetyTest`；静态源码检查不能替代生成 class 的检查。

## MixinExtras 选择
**当前实现**

- MixinBooter 11.17 在运行时提供 MixinExtras（内嵌 Cleanroom fork），源码侧不额外塞独立 runtime jar（`gradle/libs.versions.toml:20-23`、`build.gradle.kts:49-66`）。
- 包裹原调用的现有样例使用 `@WrapOperation`，例如 `ContainerMixin`、`RenderItemMixin`、`ItemGridHandlerMixin` 和 `ItemGridHandlerPortableMixin`。
- 只修改表达式结果的现有样例使用 `@ModifyExpressionValue`，例如 `CommandGiveMixin`。

**门槛**

- 包裹原调用并决定是否调用原逻辑：优先 `@WrapOperation`。
- 只改一个表达式的结果：优先 `@ModifyExpressionValue`；只改方法返回值：使用合适的 `@ModifyReturnValue`。
- 不为新目标继续写 `@Redirect`，除非语义确实无法由上述注入表达，并在变更证据中说明原因；IDE 对 pseudo target 的误报先修类路径和索引，不得直接回退到 ASM。
- 重载方法必须写完整 descriptor，例如 `getInventoryStackLimit()I` 与 `getInventoryStackLimit(I)I` 不得混淆。
- `src/main` 已无 `@Redirect`；各 Mixin 源码测试断言不得回退到 `@Redirect`/`@Overwrite`。

## Late loader 与本地 jar 语义
**当前实现**

- 对已登记的 late 模块，`StackUpUpMixinConnector`（`shouldQueue`，StackUpUpMixinConnector.kt:93-113）按配置、目标 mod 是否存在（`ModDiscoverer.isModPresent`）和 `MixinToggles` 决定是否排队；未登记 config 记 ERROR 并 `return false`（:95-102），不再无条件入队。
- `run/mods/*.jar`、`local-dev-mods/*.jar` 和带 `.jar.disable` 后缀的开发依赖由 Gradle 分别准备；带 `ContainedDeps` 的 jar 需要 FML 目录扫描才能展开内嵌依赖。

**门槛**

- `run/mods/*.jar` 进入编译/索引并由 FML 扫描，不能再额外放入运行时 classpath，否则会 duplicate mods。
- `local-dev-mods/*.jar` 才能作为普通本地开发模组额外进入运行时 classpath；若带 `ContainedDeps`，改走 `run/mods` 目录扫描，不走 classpath。
- `run/mods/*.jar.disable` 表示停用运行但保留开发期编译索引；准备阶段可去掉 `.disable` 供索引或扫描使用，但不得因此把它当成额外 runtime classpath 来源。
- late 模块清单只负责 Mixin 配置装载条件；它不替代 Mixin 配置中的目标核对，也不应引入已删除的 dynamic ASM 跳过表。
- 改动 late Mixin connector、Mixin 配置或本地依赖语义后，执行 `MixinBooterIntegrationTest`，并检查实际启动是否出现重复装载或缺失内嵌依赖。

**已知限制**

- 当前 Gradle 脚本会收集并准备 `.jar.disable`，但 `compileOnlyLocalDevModFiles` 只筛选普通 `.jar`，生成目录也未由这段配置直接接入 compile-only。故“停用运行但保留编译索引”目前是应保持的语义门槛，不是已由现行 classpath wiring 证明的实现事实；未完成 Gradle/IDE classpath 验证前不得写成通过。

## Dev 自动化的失败与 skip 规则
**当前实现**

- 新运行时属性前缀是 `stackupup.dev.autoTest.*`，旧 `stackup.dev.autoTest.*` 仅作读取 fallback；Gradle 主入口是 `-PstackupupDevAutoTest*`，旧前缀仅作 fallback。
- `DevCompatProbeSupport.hasClass`（DevCompatProbeSupport.kt:10-15）只捕获 `ClassNotFoundException` 返回 `false`；其他 `Throwable`（含链接错误）向上传播，由 `evaluateProbeAvailability`（同文件 :59-63）记为 failed，不再被误记为 missing。因此默认 `DevCompatProbe.isAvailable` 经 `hasClass` 的路径同样区分“类缺失 = skip”与“可用性检查异常 = failure”。
- `DevCompatProbeRunner.run`（DevCompatProbeRunner.kt:27-39,73-74）在装载可用探针前先算 `unknownProbeFailures`：显式请求但未登记的 probe ID 会产生 `unknown_probe_id: <id>` 的失败条目，并与探针 failures 合并返回，不再被静默过滤；探针执行异常经 `runCatching` 记为 `执行异常` failure（同文件 :56-59）。
- GT 内建矩阵在 `gregtech` 未加载且全部目标 unresolved 时允许专项 skip；部分 unresolved 始终失败，已加载 `gregtech` 时全部 unresolved 也失败。

**门槛**

- 只有“明确缺失”可以 skip。链接异常、类加载异常、可用性检查异常、探针执行异常必须记为失败并保留摘要；不得把任意 `Throwable` 伪装成未加载。
- `failFast` 只决定失败后是否中止/抛错，不得把失败改写成 skip 或 pass。自动化日志必须区分通过、跳过和失败。
- 探针必须按当前 `RuleRuntime.limitService().contextRequirements()` 解析上下文；不能使用过时的默认需求替代正式路径。
- 改动 Mixin connector、Mixin 配置或自动化参数层后，至少执行 `EarlyMixinBytecodeSafetyTest`、`MixinBooterIntegrationTest` 和 `runServerAutoTest`。未执行的项目必须标为未验证；`StackUpUpCore` 仅作 Forge 入口时不适用已删除 dynamic ASM 的 bytecode test。
- `dev/` 不新增只包一层调用的薄文件；可复用且有独立行为的单元才保留独立类型。

**已知限制**

- `failFast` 覆盖服务端驱动的失败入口：单场景规则注入失败、目标未解析、验证失败、边界探针失败，矩阵规则注入失败、矩阵 unresolved failures 与兼容探针 failures 均经 `handleFailure`（DevAutomationServerDriver.kt:290-296）或 :179 的矩阵分支统一处理（failFast 抛出、否则记录并按 `autoShutdown` 停服）。唯一不走该门的是客户端驱动：规则注入失败与目标缺失只调用 `controller.abort()`（DevAutomationClientDriver.kt:133-137、149-158），不抛异常也无进程退出码，故不能写成两端统一 fail-fast。

## 规则内核边界
**当前实现**

- `RuleField`、`ComparisonOperator`、`RuleStepKind` 是规则字段、比较运算符和动作类型的强类型枚举。
- `DslParser` 只产 AST；`DslTokenCursor` 管理游标状态；字面量 matcher 编译集中在 `RuleLiteralMatcherCompiler`；`DslRuleSource` 是单行、单文件和多文件 DSL 输入的统一入口。
- `RuleField.contextProviders` 聚合为 `RuntimeContextRequirements` provider plan，`StackContextResolver` 只执行已编译 plan；`RuleContextRequirement` 仅保留兼容/诊断投影。
- `RuleRuntimeCoordinator` 统一协调规则 reload、报告和运行态发布；`syncExampleFiles()` 是显式动作，`reload` 不隐式刷新示例文件。

**门槛**

- 字段名、运算符、动作类型禁止新增裸字符串分发；新增字段先进入强类型 enum，并由字段自身声明 matcher、缓存键和上下文 provider。
- 不引入动态字段注册表，也不恢复中间上下文复制层；matcher 和缓存键提取器直接读取 `StackContext`。
- `StackContextResolver` 不按字段名增加硬编码分支；昂贵或可选上下文必须进入 provider plan。
- 不把 `RuleContextRequirement` 当作新字段主扩展点，也不重新引入 `needsXxx` 布尔散点。
- 解析、编译、运行时发布和规则来源保持职责分离；新增入口必须先接入现有边界，而不是复制并行 source、parser 或协调器。

**已知限制**

- `RuleContextRequirements`、`RuleSnapshot` 和 `StackLimitService` 当前仍暴露 `needsOreNames`/`needsMaterial` 兼容访问面；它们不是新增字段的扩展点，新增字段仍必须通过 `RuleField.contextProviders` 和 provider plan 接入。

## 计划中的任务（未实现）

- 收紧 Mixin early path 的 Java 字节码边界，并以对应 Mixin bytecode tests 复核。
- 完成 wrapper、backing inventory、AE2 插入路径的容量守恒审计；在此之前不扩大 trusted 范围，也不把现有 remainder 聚合当作通用方案。
- 剩余失败入口（规则注入、目标物品解析、客户端注入）的 fail-fast 统一仍待实现与测试验证。
- 用 Gradle/IDE classpath 证据确认本地 jar 语义，并用真实客户端 Forge 资源重载链验证 F3+T 本地化。
