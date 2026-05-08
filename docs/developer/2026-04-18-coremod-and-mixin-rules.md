# Coremod 与 Static Mixin 约束

## Coremod Early Path

`DynamicCompatTransformer -> CompatibilityLimitPatch.planFor(...) -> TypeRelationshipResolver -> ClassHierarchyRepository`
这条链路已经实际踩到过 Kotlin stdlib 循环加载。

当前约束：

1. 不在这条路径里使用 `Sequence`、集合扩展、高阶函数、字符串扩展、`use {}`。
2. 不使用会额外生成 `WhenMappings` / `kotlin.NoWhenBranchMatchedException` 的 `enum + when` 组合。
3. 动态补丁先探测“当前类是否声明任何候选上限方法”；无命中时，直接跳过，不再做层级遍历。
4. 命中候选方法后，再探测“当前类是否真的声明了目标方法”，未声明则直接跳过，避免把纯继承类也拖进 ASM 改写。
5. 优先显式循环、JDK 集合、基础条件判断。
6. 对应回归：`DynamicCompatEarlyPathBytecodeTest`
7. `DynamicCompatTargetProfile` 现在同时承担目标类型名、候选方法名与补丁方法名的单一事实来源；新增动态目标规则时先改这里，不要再分散写到多层中转对象。
8. `FixedCompatTargets` 是 dynamic ASM 固定跳过目标的唯一事实源；新增或移出固定目标时，必须同步让 `classifier / patch planner / transformer` 三层回归都遍历它。
9. `DynamicCompatPlan` 只保留补丁载荷，不再携带未消费的类名状态。
10. slash/dot 类名归一化统一走同一个 helper，不要在 early path 再复制一份字符串转换实现。
11. `ClassHierarchyRepository` 只允许读取父类与接口签名，不再为层级判断构建完整 `ClassNode`。
12. `DynamicCompatMethodProbe` 现在应保持“单次 profile-aware 方法扫描”；不要回退成“先扫 candidateMethods，再扫 profileMethods”的双扫描。
13. `PlayerInvWrapper`、`SlotCrafting` 这类桥接子类不要为了“名义完整”再补 static mixin；现有父类 mixin 已覆盖实际行为，dynamic ASM 也已通过声明方法探测跳过它们。

## Static Mixin Handlers

对于目标是静态方法的 early mixin：

1. 优先使用 Java mixin 类与 `private static` handler。
2. 不再使用 Kotlin `companion object + @JvmStatic`。
3. 原因不是代码风格，而是 Mixin 会把 `Companion` 字段当作额外静态成员并给出结构警告。
4. 对应回归：`EarlyMixinBytecodeSafetyTest`

## Late Mixin Modules

1. `StackUpUpLateMixinLoader` 里的模块表只负责“按模组维度装载哪个 late 配置文件”。
2. 它不等于 `FixedCompatTargets`，也不替代 dynamic ASM 的跳过表。
3. 只有某个固定类已经被显式 mixin 独占接管，且需要避免 dynamic ASM 重复补丁时，才把该类加入 `FixedCompatTargets`。
4. 当前 `AE2 / Refined Storage / CyclopsCore` 都属于“既有 late mixin 模块，也有部分固定类进入跳过表”的情况。

## MixinExtras Priority

1. 当前工程通过 `MixinBooter` 运行时获得 `MixinExtras`，源码侧只保留编译期依赖。
2. 对方法调用做条件包裹时，优先使用 `@WrapOperation`，不要继续写新的 `@Redirect`。
3. 对常量或表达式结果做轻量修正时，优先考虑 `@ModifyExpressionValue`。
4. 如果 IntelliJ/MinecraftDev 对 pseudo optional target 持续误报，先修开发类路径与索引，不要第一时间回退到旧 ASM。
5. 当前已迁移样例：`ContainerMixin`、`RenderItemMixin`。
6. `ItemGridHandlerMixin`、`ItemGridHandlerPortableMixin` 已迁到 `@WrapOperation`，行为保持不变，后续不要回退到 `@Redirect`。

## Local Dev Mods

1. `run/mods/*.jar` 只用于 FML 目录扫描，同时进入编译类路径供 IDEA 建索引。
2. `run/mods/*.jar` 不再额外注入运行时 classpath，避免重复装载。
3. `local-dev-mods/*.jar` 同时进入编译类路径与运行时 classpath，适合放仅用于本地开发联调的模组。
4. `.jar.disable` 仅表示“停用运行”，不要再把它当成运行时依赖来源。

## Dev Auto Test Prefix

开发自动化参数前缀已切到：

1. `stackupup.dev.autoTest.*`

兼容策略：

1. 运行时继续回退读取 `stackup.dev.autoTest.*`
2. Gradle 新主入口为 `-PstackupupDevAutoTest*`
3. 旧 `-PstackupDevAutoTest*` 仅作 fallback 兼容
4. 新增文档、脚本与配置统一写新前缀

## Dev 层收口原则

1. `dev/` 只保留“能独立复用或独立测试”的单元。
2. 若某段逻辑只被一个调用点消费，优先并回调用侧，减少文件碎片和类型跳转。
3. 2026-04-19 这一轮已把 `DevTargetSelector`、`DevProbeEvaluator` 并回调用侧，`dev` 主代码文件数收缩到 `9` 个。
4. 可用性探针不能再把任意 `Throwable` 吞成“模组未加载”；只有明确的缺失场景才允许 skip，链接异常必须记为失败。
5. GT 内建矩阵只有在 `gregtech` 未加载时才允许“全部 unresolved 视作跳过”；若模组已加载但所有样本都未解析到，必须记为失败。

## Rules 内核收敛原则

1. DSL 字段名、比较运算符、动作类型统一使用强类型枚举，不再继续扩散字符串判断。
2. `DslRuleSource` 现在同时承担单行、单文件、多文件规则输入；新增规则来源时先接到这里，不要再复制一层 `*RuleSource` 包装。
3. 规则重写优先做“边界收敛”和“类型收敛”，再考虑语法扩展。
4. 运行态编排统一收口到 `RuleRuntimeCoordinator`，它是 `reload / report / world persist` 的唯一协调层；`StackUpUp` 只保留生命周期与少量门面。
5. `DslParser` 只做 AST 解析，token 读取状态由 `DslTokenCursor` 承担。
6. `RuleConditionCompiler` 只做条件拼装；item/string 通配与 metadata 语法糖统一下沉到 `RuleLiteralMatcherCompiler`。
7. `org.cyclops.cyclopscore.inventory.SimpleInventory` 已进入 `FixedCompatTargets`，其库存上限完全由 late mixin 负责，不再允许 dynamic ASM 重复补丁。
8. `appeng.tile.inventory.AppEngInternalInventory` 与 `appeng.tile.inventory.AppEngInternalAEInventory` 已补齐构造期常量和 `getInventoryStackLimit` 返回值两条路径，现已进入 `FixedCompatTargets`。
> **已过时**
