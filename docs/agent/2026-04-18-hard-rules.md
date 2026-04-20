# 2026-04-18 Hard Rules

## Coremod Early Path

1. `DynamicCompatTransformer -> DynamicCompatPlanBuilder -> TypeRelationshipResolver -> ClassHierarchyRepository` 属于 coremod 早期路径。
2. 这条路径禁止重新引入 `kotlin.collections`、`kotlin.sequences`、`kotlin.text`、`kotlin.io`、`kotlin.ranges`、lambda/function 引用，以及会生成 `WhenMappings` / `NoWhenBranchMatchedException` 的分支写法。
3. 动态补丁先做“当前类是否声明任何候选上限方法”的探测；没有命中时，连层级分类都不做。
4. 命中候选方法后，再做“当前类是否真的声明目标方法”的细分探测；只继承父类实现的目标直接跳过，不进入完整 ASM 改写。
5. 如果必须改这层，优先使用显式循环、JDK 集合、朴素条件判断。
6. 对应硬门槛测试：`DynamicCompatEarlyPathBytecodeTest`
7. `DynamicCompatTransformer.transform` 必须容忍 `transformedName` / `basicClass` 为空，不能再把 LaunchWrapper 的 platform type 当成强非空。
8. `FixedCompatTargets` 是 dynamic ASM 固定跳过回归的唯一数据源；不要再手写散落样例。
9. AE2 的 `AppEngInternalInventory` / `AppEngInternalAEInventory` 已归入 fixed-target；若后续改动它们，必须同时守住构造期常量与 `getInventoryStackLimit` 返回值两条路径。
10. `ClassHierarchyRepository` 不应再依赖 `asm.tree` 读取完整类树；层级判断只保留父类与接口签名扫描。
11. `DynamicCompatMethodProbe` 必须保持单次 profile-aware 扫描，再按命中的 profile 做定向关系判断；不要回退成双扫描 + 全量分类。

## Static Mixin Handlers

1. 静态目标方法 mixin 优先使用 Java `private static` handler。
2. 不要再在这类 mixin 里使用 Kotlin `companion object + @JvmStatic`。
3. 对应硬门槛测试：`EarlyMixinBytecodeSafetyTest`

## Late Mixin Modules

1. `StackUpUpLateMixinLoader` 的模块表只负责 late 配置装载，不等于 `FixedCompatTargets`。
2. 若某类已由 late mixin 覆盖，但 dynamic ASM 不会命中它，则不要为了“名义统一”强行塞进 `FixedCompatTargets`。
3. 只有确定需要让 dynamic ASM 主动避让的固定类，才进入 `FixedCompatTargets`。

## MixinExtras Priority

1. 只要是“包裹原调用”而不是“完全替换调用”，优先用 `@WrapOperation`，不要新写 `@Redirect`。
2. 只要是“改表达式结果”而不是“改整段流程”，优先考虑 `@ModifyExpressionValue`。
3. 如果 pseudo late target 在 IDEA 中无法稳定解析，优先修类路径与索引；必要时允许保留局部 `@Redirect`，但不要回退到 ASM。
4. 已稳定迁移样例：`ContainerMixin`、`RenderItemMixin`。
5. `ItemGridHandlerMixin`、`ItemGridHandlerPortableMixin` 已迁到 `@WrapOperation`，后续不要再把 RS / MoreRS 的提取路径改回 `@Redirect`。
6. 运行时由 `MixinBooter` 提供 `MixinExtras`，不要额外塞独立 runtime jar。

## Local Dev Mods

1. `run/mods/*.jar` 只参与 FML 目录扫描，不额外放进运行时 classpath。
2. `local-dev-mods/*.jar` 才允许额外注入运行时 classpath。
3. 开发期若出现 duplicate mods，先检查是否把 `run/mods` 同时接入了 runtime classpath。

## Dev Auto Test

1. 开发自动化运行时参数新前缀为 `stackupup.dev.autoTest.*`。
2. 旧前缀 `stackup.dev.autoTest.*` 仅作兼容回退，不再继续扩散。
3. Gradle 新主入口为 `-PstackupupDevAutoTest*`，旧 `-PstackupDevAutoTest*` 仅作 fallback。
4. 动过 coremod / mixin / 自动化参数层后，至少补跑：
   `CoremodHierarchyBytecodeSafetyTest`
   `EarlyMixinBytecodeSafetyTest`
   `MixinBooterIntegrationTest`
   `runServerAutoTest`
5. `dev/` 下不要继续新增“只包一层调用”的薄文件；像目标选择、探针评估这类单消费逻辑，优先并回调用侧。
10. probe 可用性检查只能把“明确缺失”当作 skip；链接异常、类加载异常必须转成失败摘要。
11. GT 内建矩阵仅在 `gregtech` 未加载时允许整组 unresolved 跳过；一旦 `gregtech` 已加载，整组 unresolved 必须失败。
12. `rules/` 内核禁止继续扩散“字段名/运算符/动作类型”的裸字符串分发；统一走强类型枚举。
13. 规则加载入口统一收敛到 `DslRuleSource`，不要重新引入并行的单文件/多文件 source 包装器。
14. 规则运行态协调统一收口到 `RuleRuntimeCoordinator`：`reload / lastReport / rulesFile / worldRulesFile / persistWorldRules` 不要再散回 `StackUpUp`、命令层或代理层。
15. `StackUpUp` 继续只做 Forge 生命周期编排；新逻辑优先落到独立协调器或服务，不要把入口类重新养胖。
16. `DslParser` 只负责语法结构；token 游标放在 `DslTokenCursor`，字面量 matcher 编译放在 `RuleLiteralMatcherCompiler`，不要把 parser/compiler/runtime 的细节重新揉回一个文件。
