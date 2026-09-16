# 领域硬门槛

> 状态：PARTIAL（文末「已知限制」与「计划中的任务」所列未实现项未闭合，未完成独立复核，不得记为通过）。

> 通用协作规范见根级 `AGENTS.md`；本文件只记 coremod、Mixin、容量安全、开发自动验收和规则内核的项目特有门槛。“当前实现”只表示能在现有源码中定位到的行为；“已知限制”表示证据不足或仍需运行验证；“计划中的任务”不属于当前实现。

## Coremod early path：纯 Java 与字节码禁区
**当前实现**

- 动态早期链路是 `DynamicCompatTransformer → CompatibilityLimitPatch → TypeRelationshipResolver → ClassHierarchyRepository`。
- `DynamicCompatTransformer.transform` 对 `basicClass == null` 返回 `null`，对 `transformedName == null` 回退到 `name`；这两个输入约束必须保持。
- `DynamicCompatMethodProbe` 只扫描当前类直接声明的方法名；`ClassHierarchyRepository` 只读取父类和接口签名，不为层级判断构造完整 `ClassNode`。
- early path 中 `NameConverter.toSlashName` / `toDotName` 是 slash/dot 类名归一化的唯一入口；新增代码不得复制字符串替换实现。

**门槛**

- `src/main/java/.../core/` 的 early path 必须保持纯 Java：不得引入 `kotlin.collections`、`kotlin.sequences`、`kotlin.text`、`kotlin.io`、`kotlin.ranges` 及 Kotlin 函数运行时，不得使用 lambda、方法引用、`use {}` 或会生成 `WhenMappings`/`NoWhenBranchMatchedException` 的 `enum + when`；用显式循环、JDK 集合和朴素条件。
- `ClassHierarchyRepository` 不得依赖 `org.objectweb.asm.tree`；层级判定只读父类、接口和必要签名。
- `DynamicCompatMethodProbe` 保持一次按方法名的 profile-aware 扫描：没有候选方法名时直接跳过，不做层级分类；命中后只能确认当前类直接声明了相应方法名，不能据此确认完整 descriptor 或方法内常量的语义位置。
- 每次修改 coremod early path 后，至少检查 `DynamicCompatEarlyPathBytecodeTest` 和 `CoremodHierarchyBytecodeSafetyTest`。源码目录是 Java 不是通过字节码门槛的证明。

**已知限制**

- `CompatibilityLimitPatch.java` 仍以 `Consumer<ClassNode>` 和 `node -> { ... }` 生成补丁，与本节禁止 lambda/函数对象的门槛冲突；现有字节码测试没有覆盖 `invokedynamic`，因此不能把 early path 记为已通过。
- `ClassHierarchyRepository` 当前在资源缺失或 ASM 解析异常时返回空 metadata，并抑制关闭流异常；这会混淆“无法读取”和“确实没有层级”，不能作为安全分类证据。

## Mixin/ASM 迁移准入检查表

迁移旧 ASM 目标前，四项必须同时成立：

1. 目标类在明确的模组或原版/Forge 基础路径中稳定存在，并能确定应由 late 或 early Mixin 负责。
2. 目标方法签名已由源码或字节码确认；重载必须用完整 descriptor 区分。静态 ASM 可能按方法名/字节码位置寻找 `BIPUSH 64`，命中本身不能替代该常量语义位置的证据，也不等于 descriptor 已闭合。
3. 所需行为能由 Mixin 表达为返回值修改、表达式结果修改或原调用包裹；不能表达或受加载阶段限制时，才保留窄范围 ASM。
4. 有测试或实际运行验证证明该目标不会被 dynamic ASM 二次命中；需要避让时，固定类已进入 `FixedCompatTargets`。

已明确的 late Mixin 目标不得重新放回泛化 ASM；未知 `IItemHandler` 不得作为快速扩容的理由，也不得用 remainder-system 修复吞物品。

## 固定目标与动态 ASM 边界
**当前实现**

- `DynamicCompatTargetProfile` 保存各 profile 的目标类型和补丁目标方法数组。
- 候选方法名仍由 `DynamicCompatMethodProbe` 直接匹配，补丁 helper 名称仍在 `CompatibilityLimitPatch`；不能把三者描述成已闭合的单一事实源。
- `FixedCompatTargets` 是 dynamic ASM 固定跳过目标的唯一数据源；实际 transform 路径的 `CompatibilityLimitPatch.planFor(..., basicClass)` 先交给 `DynamicCompatMethodProbe` 检查当前类直接声明的候选方法名，再分类和生成补丁。
- 固定表与 probe 职责不可互换：固定表只表达需要避让的固定目标，probe 只提供当前类是否声明候选方法名的证据；probe 不扩充固定表，固定表也不代替声明探测。late mixin 模块表只描述配置装载，不是 dynamic ASM 的跳过表。

**门槛**

- 已由显式 Mixin 接管、且 dynamic ASM 需要避让的类，必须进入 `FixedCompatTargets`；不要在 transformer、classifier、patch planner 各自维护样例名单。
- `PlayerInvWrapper`、`SlotCrafting` 等只继承父类行为的桥接子类，不要为名义完整重复补 static Mixin；按方法名探测确认其未直接声明候选方法名后，dynamic ASM 应跳过它们。
- 对当前类字节码未直接声明候选方法名的继承类，dynamic ASM 不进行改写。未知类不得仅因实现某个接口、类名相似或返回 `64` 就被推断为可扩容目标；接口关系必须与当前类直接声明的候选方法名及其他目标证据同时成立。
- 动态补丁只能修改已确认的目标方法和已确认的字节码模式；不得借 ASM 绕过真实写入容量证据。
- 核对固定表时必须逐项对照显式 `@Mixin` 目标和实际写入方法；固定表命中、Mixin 配置包含类名或 `probeTargets` 子集都不能代替该对照，也不能证明容量守恒。
- 第三方 jar 没有可核对源码或字节码写入路径时，结论必须写“无源码不可判定”，并列出缺失的 jar；不得用类名、方法名或我方注释冒充写入路径证据。

**已知限制**

- `DynamicCompatMethodProbe` 仅按方法名识别当前类直接声明的候选方法，`CompatibilityLimitPatch` 再按方法名匹配并替换匹配方法内的所有 `BIPUSH 64`；方法 descriptor 与常量的语义位置均未确认，不得把它描述成已完成 descriptor-aware、语义精确的动态补丁。
- `FixedCompatTargets` 与 `ForgeItemHandlerLimitMixin` 的目标集合不完全相同：`ForgeItemHandlerLimitMixin` 当前只改写 `net.minecraftforge.items.ItemStackHandler` 和 `net.minecraftforge.items.wrapper.EntityEquipmentInvWrapper` 两个自洽类；固定表另列出无需注入的 `net.minecraftforge.items.VanillaDoubleChestItemHandler`、`net.minecraftforge.items.wrapper.EmptyHandler`，以及已移出注入的 `InvWrapper`、`SidedInvWrapper`、`CombinedInvWrapper`、`RangedWrapper`；`net.minecraftforge.items.SlotItemHandler` 另由 `SlotItemHandlerMixin` 目标覆盖。现有相关测试中，`CompatibilityLimitPatchTest` 的固定名单检查未传入 `basicClass`，`DynamicCompatTargetClassifierTest` 的对应断言只检查固定分类跳过及 `probeTargets` 子集，`EarlyMixinConfigTest` 只检查配置文本包含 Mixin 名称；没有测试证明固定表与显式 Mixin 逐项对齐，也没有测试证明 early Mixin 应用时 wrapper、backing inventory 与实际写入路径组合的容量安全。

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
- late 模块清单与 `FixedCompatTargets` 必须分别维护；有 late 配置不等于 dynamic ASM 必须跳过该类。
- 改动 late loader、Mixin 配置或本地依赖语义后，执行 `MixinBooterIntegrationTest`，并检查实际启动是否出现重复装载或缺失内嵌依赖。

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
- 改动 coremod、Mixin 或自动化参数层后，至少执行 `CoremodHierarchyBytecodeSafetyTest`、`EarlyMixinBytecodeSafetyTest`、`MixinBooterIntegrationTest` 和 `runServerAutoTest`。未执行的项目必须标为未验证。
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

- 收紧 early path 的函数对象、层级读取失败和字节码门槛，并以对应 bytecode tests 复核。
- 为动态 ASM 补齐 descriptor 与语义位置证据，或把不满足证据要求的目标移出动态补丁。
- 完成 wrapper、backing inventory、AE2 插入路径的容量守恒审计；在此之前不扩大 trusted 范围，也不把现有 remainder 聚合当作通用方案。
- 剩余失败入口（规则注入、目标物品解析、客户端注入）的 fail-fast 统一仍待实现与测试验证。
- 用 Gradle/IDE classpath 证据确认本地 jar 语义，并用真实客户端 Forge 资源重载链验证 F3+T 本地化。
