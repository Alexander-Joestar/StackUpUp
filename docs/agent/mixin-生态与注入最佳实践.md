# Mixin 生态与注入最佳实践

> 面向 StackUpUp 重构代理的执行规范。本文只规定调查、选择、实现和验证 Mixin 的方法；不表示项目已经升级
> MixinBooter，也不把上游当前版本的行为倒灌为本项目事实。
>
> 文中每个结论使用以下标签：
>
> - **[当前项目事实]**：能由当前项目源码、构建配置或已登记的项目规范直接定位。
> - **[上游资料]**：来自官方仓库 README/源码或 DeepWiki 架构资料；精确 API 仍须以实际依赖 jar 和目标版本核对。
> - **[建议]**：StackUpUp 重构时必须遵守的执行规则或推荐做法。
> - **[UNKNOWN]**：证据不足，不能安全判定；应保持收缩、跳过可选目标或补齐证据，不得写成“安全”或“已完成”。
>
> 本文中的“链式”只表示多个注入可以在同一操作上共存，不表示业务语义自动正确。是否调用原操作、调用几次、传递什么参数，仍由每个
> handler 的行为测试证明。

## 1. 定位、范围与不可协商的不变量

### 1.1 这份文档解决什么问题

代理接到 Mixin/ASM 任务时，先回答四件事：

1. **目标是什么**：要改的是方法入口/出口、某个表达式、某个调用参数、接收者、私有状态，还是完整控制流。
2. **真实写入或业务路径是什么**：容量任务必须同时看到广告面和真实写入面，不能只改 GUI、slot 查询或返回值。
3. **目标属于哪个加载阶段**：当前 10.7 项目的 early/late 事实与上游 11.x 的注册方式不能混写。
4. **如何证明没有破坏其他 Mixin**：选择可共存的注入器，记录目标 descriptor、匹配数、原操作调用次数和行为结果。

本文不授权代理修改代码；每个实现任务仍须由上层任务明确文件租约。本文也不把源码搜索、静态 `contains` 检查或类名相似性当作运行行为证明。

### 1.2 安全不变量

以下规则优先于“看起来能工作”的注入方案：

- **容量不变量**：对外广告容量不得大于真实写入容量。容量证据来自目标对象的实际查询、限制、setter/insert 路径和持久化路径，不来自类名、注释、
  `== 64` 哨兵或未知实现的主动表态。
- **插入守恒**：每次真实 `IItemHandler#insertItem` 审计都应满足：

  ```text
  storedDelta + remainderCount == offered
  ```

  这是只读审计公式，不是写入后的回填、重试或补偿算法。

- **simulate 分离**：`simulate=true` 只说明预览结果；`simulate=false` 才能作为真实写入证据。审计必须分别记录 `offered`
  、实际落库量、原调用返回的 `remainder`、目标类和 `slot`。
- **禁止事后补偿**：真实写入后禁止重新计算余量、回填源栈或槽位、重试已接受数量、补偿丢失数量，或用事后状态改写业务结果。守恒审计只能观察、报告和失败，不能修正业务结果。
- **未知 handler 不扩容**：未知 `IItemHandler` 的 `getSlotLimit`、wrapper 返回值或接口类型不能证明其内部 setter、delegate
  和持久化路径可以承载更大堆叠。没有写入证据就保持原上限。
- **Mixin 优先、ASM 兜底**：目标类、方法、完整 descriptor 和行为都明确时，优先使用窄范围 Mixin；只有 Mixin
  无法表达且写入/控制流证据闭合时才保留窄范围 ASM。ASM 不能绕过容量证据，也不能用方法名或 `BIPUSH 64` 单独证明语义。
- **Fail Fast**：核心原版/Forge 路径目标缺失、descriptor 不符或注入次数不满足时必须失败并留下可定位信息。只有明确可选的第三方版本差异才允许软失败，并且必须记录结构化诊断。

### 1.3 不以注解数量评价质量

**[建议]** 当前代码中 Mixin 分布在 early、late、原版、Forge 和多个第三方兼容配置中；注解搜索的匹配数量会随搜索口径变化，不能作为质量指标。
`@Inject`/`@Redirect` 多，可能只是历史代码覆盖了很多调用点；`@Shadow` 少，也可能正是因为 handler 不需要访问目标私有状态。

代理常见“Inject/Redirect 多、Shadow 少”的现象有合理原因：

- `@Inject` 适合在生命周期点观察、校验、提前拒绝或在返回前后调整；
- `@Redirect` 直接接管一个调用点，旧代码容易写，但通常牺牲共存性；
- `@Shadow` 不是注入器，只是访问目标已有字段/方法的声明桥。没有私有状态访问需求时，强行增加 Shadow 只会增加目标内部实现耦合；
- 评价标准应是目标精确性、原逻辑保留、多个 Mixin 的共存、混淆/refmap 正确性、容量守恒和行为测试，而不是某一种注解的数量。

## 2. 证据层级、官方资料与版本矩阵

### 2.1 证据层级

按以下顺序取证，低层级证据不能覆盖高层级冲突：

1. **目标版本的本地源码、反编译源码、实际字节码和可重复运行日志**：确认方法 descriptor、调用顺序、delegate、写入路径和转换结果。
2. **当前项目的真实依赖坐标、构建配置和源码**：确认项目到底运行哪个 MixinBooter/MixinExtras，而不是哪个上游 README 看起来最新。
3. **官方仓库在对应版本的 README、源码和发布信息**：确认启动、注册和 API 边界。
4. **DeepWiki 页面**：用于架构、生命周期和调用链索引；不能单独作为当前版本的精确 API 或运行行为证明。
5. **类名、注释、grep 计数和历史代码**：只能作为调查入口。

**[建议]** 上游 README、DeepWiki 和本地依赖冲突时，先记录冲突，再以当前项目实际依赖及对应版本源码为准；不要用“上游最新”替换“当前项目已使用”。

### 2.2 五个官方仓库和 DeepWiki 证据台账

| 生态组件                     | 官方仓库                                                              | 已查 DeepWiki 页面/用途                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | 证据边界                                                                                                                                                                                                  |
|------------------------------|-----------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| MixinBooter                  | [CleanroomMC/MixinBooter](https://github.com/CleanroomMC/MixinBooter) | [DeepWiki](https://deepwiki.com/CleanroomMC/MixinBooter)：Overview、Core Architecture、Mixin Loading System（Early/Late）、Compatibility System、Build and Deployment                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | 用于理解 1.12.2 Forge 侧 bootstrap、配置发现和兼容层；11.x 注册事实以官方当前 README 为准。                                                                                                               |
| CleanMix                     | [CleanroomMC/CleanMix](https://github.com/CleanroomMC/CleanMix)       | [DeepWiki](https://deepwiki.com/CleanroomMC/CleanMix)：Bootstrap & Platform Integration、Transformation Engine、Mixin Annotations、Injection System、Annotation Processor & Obfuscation                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | 用于 Mixin 核心、service/platform、转换和 AP/refmap 架构；不能由 DeepWiki 推出项目 10.7 已经使用 CleanMix。                                                                                               |
| CleanroomMC MixinExtras fork | [CleanroomMC/MixinExtras](https://github.com/CleanroomMC/MixinExtras) | `read_wiki_structure` 与问答均返回 `Repository not found`；这是 DeepWiki 索引失败，不是 GitHub 仓库不存在。官方 [GitHub 仓库页面](https://github.com/CleanroomMC/MixinExtras) 与 [README.MD raw](https://raw.githubusercontent.com/CleanroomMC/MixinExtras/master/README.MD) 可作为一等入口；本次 `https://raw.githubusercontent.com/CleanroomMC/MixinExtras/main/README.md`、`https://raw.githubusercontent.com/CleanroomMC/MixinExtras/master/README.md`、`https://raw.githubusercontent.com/CleanroomMC/MixinExtras/main/README.MD` 返回 HTTP 404，仅 `https://raw.githubusercontent.com/CleanroomMC/MixinExtras/master/README.MD` 成功，失败范围是这些 branch/path 组合，不是官方页面不可用。 | 官方页面/README 当前示例坐标为 `com.cleanroommc:mixinextras-common:0.5.5`，这是页面示例和时间敏感资料，不代表当前项目已使用；provider、shading、manifest/service 和 runtime 仍须以实际 jar/启动证据核对。 |
| LlamaLad7 MixinExtras        | [LlamaLad7/MixinExtras](https://github.com/LlamaLad7/MixinExtras)     | [DeepWiki](https://deepwiki.com/LlamaLad7/MixinExtras)：Core Injection System、WrapOperation、ModifyExpressionValue、ModifyReceiver、WrapMethod、Local/Share/LocalRef、Service Management；另核对官方 [WrapWithCondition Wiki](https://github.com/LlamaLad7/MixinExtras/wiki/WrapWithCondition)                                                                                                                                                                                                                                                                                                                                                                                                   | 用于解释 Extras 注入器语义和链式设计；初始化、打包和版本兼容仍须核对当前平台。                                                                                                                            |
| SpongePowered Mixin          | [SpongePowered/Mixin](https://github.com/SpongePowered/Mixin)         | [DeepWiki](https://deepwiki.com/SpongePowered/Mixin)：Bootstrap、Transformation Pipeline、Injection System、Annotation Processing、Accessor Generation                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | 用于原生 Mixin 的注入器、Shadow、Accessor/Invoker、AP/refmap 基础；项目可能运行其 CleanMix fork，不能把上游发行版本号直接当作运行时事实。                                                                 |

**DeepWiki 冲突记录**：MixinBooter 的 DeepWiki 问答曾返回仍以 `IEarlyMixinLoader`/`ILateMixinLoader` 为核心的 two-phase
描述；官方当前 MixinBooter README 对 11.x 明确写明 early/late divide 已淡出且接口 deprecated，同时给出 Manifest
`MixinConfigs`/`MixinConnector`。这两者冲突时，本文把 DeepWiki 结果视为旧索引或未按当前版本收敛的架构线索，不把它当作 11.x
注册事实。

**搜索失败记录**：本次通用 WebSearch 请求因服务端 HTTP 402 membership verification 失败；没有把其结果用于结论。官方 GitHub
页面可直接访问，故采用官方仓库 README/页面和 DeepWiki 已返回的架构资料。

**第三方源码/jar 缺口**：依据 `docs/agent/compatibility-decision-record.md:156-163` 的现有台账，本次不能把下列 1.12.x
第三方实现写成已核实写入路径：`appliedenergistics2`、`actuallyadditions`、`brandonscore`、`cyclopscore`、`enderio`、`ic2`、
`mantle`、`refinedstorage`、`storagenetwork`、`integrateddynamics`、`limelib`、`immersiveengineering`，以及机器研究用的
`nuclearcraft`、`techreborn`、`reborncore`。对应 jar 文件名/版本未登记或源码不可读；这些目标统一为 `无源码不可判定`，不能用
Mixin 类名、注释或历史方法名补足证据。Forge wrapper 和 vanilla 反编译源码不属于这份缺口，但仍须核对具体写入路径。

### 2.3 当前项目和上游版本矩阵

| 范围                      | 已核对版本/配置                                                                                                                                                                                                                                                                                                                                  | 当前可写成的事实                                                                                                                                                                                                                                                                                                              | 不得写成的结论                                                                                                                                                                         |
|---------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 当前 StackUpUp            | Minecraft 1.12.2、Forge 14.23.5.2847；`zone.rong:mixinbooter:10.7`                                                                                                                                                                                                                                                                               | **[当前项目事实]** `build.gradle.kts:434` 通过 `modUtils.enableMixins` 锁定 MixinBooter 10.7；`build.gradle.kts:438-439` 使用 `io.github.llamalad7:mixinextras-common:0.5.0` 的 `compileOnly` 与 `annotationProcessor`。                                                                                                      | 不得写成项目已经升级到 11.x、CleanMix 0.6.0 或 CleanroomMC MixinExtras fork。                                                                                                          |
| MixinBooter 10.7 上游 tag | [README raw](https://raw.githubusercontent.com/CleanroomMC/MixinBooter/10.7/README.md)、[build.gradle raw](https://raw.githubusercontent.com/CleanroomMC/MixinBooter/10.7/build.gradle)、[MixinBooterPlugin.java raw](https://raw.githubusercontent.com/CleanroomMC/MixinBooter/10.7/src/main/java/zone/rong/mixinbooter/MixinBooterPlugin.java) | **[上游资料]** 官方 `10.7` tag README 写 `UniMix 0.15.3`（CleanroomMC fork，derived from 0.8.7）和 LlamaLad7 MixinExtras `0.5.0`；tag 的 build 使用 `com.github.CleanroomMC:UniMix:9d4b487ed3`，并 `embed 'io.github.llamalad7:mixinextras-common:0.5.0'`；source 的 `MixinBooterPlugin` 调用 `MixinExtrasBootstrap.init()`。 | 这些只是上游 10.7 README/build/source 资料，不是当前项目实际 jar 已核验；不能由此证明当前 provider、shading、manifest 或启动日志。                                                     |
| 当前加载注册              | `StackUpUpCore.kt:3-13,88-93`；`StackUpUpLateMixinLoader.kt:5-15,18-35`                                                                                                                                                                                                                                                                          | **[当前项目事实]** core 入口实现 `IEarlyMixinLoader` 并注册 `mixins.stackupup.early.json`；late loader 按 `Context`、mod ID 和 `MixinToggles` 排队多个第三方配置。                                                                                                                                                            | 不得因为 11.x README 的 manifest 方式就直接删除当前 loader；10.7 的精确接口和运行阶段仍以本地依赖/运行验证为准。                                                                       |
| 当前 jar manifest 注册    | `build.gradle.kts:490-507`                                                                                                                                                                                                                                                                                                                       | **[当前项目事实]** `tasks.withType<Jar> { manifest }` 条件写入 `FMLCorePlugin`；启用 mod 时写 `FMLCorePluginContainsFMLMod`、按 task 写 `ForceLoadAsMod`；启用 AT 时写 `FMLAT`。该段不含 `MixinConfigs` 或 `MixinConnector`，说明 11.x manifest 注册模型尚未进入当前项目构建配置。                                            | 不得把 11.x manifest attributes 当作当前已注册；最终 jar manifest、provider/shading 和启动路径仍需实际 artifact/log 核对。                                                             |
| 当前配置                  | `src/main/resources/mixins.stackupup.early.json:1-7` 及各 late JSON                                                                                                                                                                                                                                                                              | **[当前项目事实]** early JSON 明确使用 `refmap`、`minVersion: 0.8`、`compatibilityLevel: JAVA_8`；各第三方配置按模块拆分并共享 refmap，但部分 late JSON 缺少 `minVersion` 或 `compatibilityLevel`，不能概括为字段完全一致。                                                                                                   | 不得把 `required=false` 或 `require=0` 当成“目标一定存在”或“功能已生效”；迁移时逐个配置核对字段。                                                                                      |
| MixinBooter 11.0+         | 官方当前 README                                                                                                                                                                                                                                                                                                                                  | **[上游资料]** 11.x 基于 CleanMix；README 写明 early/late divide 不再存在、`IEarlyMixinLoader`/`ILateMixinLoader` deprecated；支持 Manifest `MixinConfigs` 和 `MixinConnector`。                                                                                                                                              | 不得将 11.x 的 manifest、connector、Context、ModDiscoverer 或兼容修复无条件移植到 10.7。                                                                                               |
| MixinBooter 11.12         | MixinBooter 与 CleanroomMC/MixinExtras 官方 README                                                                                                                                                                                                                                                                                               | **[上游资料]** MixinBooter README 标注 CleanMix 0.6.0/Mixin 0.8.7，并写明 11.12 使用 CleanroomMC 自有 MixinExtras fork；CleanroomMC/MixinExtras 官方页面/README 当前展示的 `com.cleanroommc:mixinextras-common:0.5.5` 是页面示例和时间敏感资料，不是当前项目依赖事实。                                                        | 不得假设 `io.github.llamalad7` 0.5.0 与 Cleanroom fork 0.5.5 二进制、bootstrap、service 或打包方式自动兼容；版本/坐标仍须用实际 jar metadata 复核，2.2 已记录 raw URL 的具体失败范围。 |
| LlamaLad7 MixinExtras     | 官方当前 README                                                                                                                                                                                                                                                                                                                                  | **[上游资料]** 需要按平台初始化 `MixinExtrasBootstrap.init()` 并按平台打包；其他平台的 ShadowJar/relocation 只是粗略指南。                                                                                                                                                                                                    | 不得因能编译 annotation 就假设运行时已初始化、service 已注册或独立 jar 不会重复装载。当前项目是否由 MixinBooter 10.7 提供运行时，必须以实际 jar/启动日志核对。                         |

**10.7 上游嵌入与当前运行时必须分开记账**：官方 10.7 tag 的 build/source 显示 MixinBooter 上游把 LlamaLad7 MixinExtras
`0.5.0` 嵌入并调用 `MixinExtrasBootstrap.init()`；这只是上游 tag 的嵌入/初始化路径。当前项目只有
`build.gradle.kts:434,438-439` 的坐标与 `compileOnly`/AP 声明，当前实际运行时的
provider、shading/relocation、manifest/service 和启动日志均未由本地 jar 或启动证据确认，保持 **[UNKNOWN]**。不能把上游 10.7
资料改写成当前 jar 已核验，也不能写成项目已采用 11.x 的 CleanroomMC fork。

### 2.4 11.x 升级准入门

升级不是“改一行坐标”。在任何 11.x 迁移前，代理必须完成以下独立记录：

1. **版本和来源**：锁定 MixinBooter 具体版本、CleanMix 版本、MixinExtras provider/fork 版本、Maven 仓库和最终运行时 jar；列出所有
   compileOnly、annotationProcessor、runtime 和内嵌关系。
2. **注册路径**：检查候选版本 jar 的 manifest、`MixinConfigs`、`MixinConnector`、`IMixinConnector`、实际
   `Mixins.addConfiguration` 调用和 Forge 1.12.2 的 classloader 顺序。不能只读 README。
3. **现有 loader 迁移**：逐项说明 `StackUpUpCore` 的 early 配置、late mod-gate、冲突禁用逻辑和 `MixinToggles`
   如何在新注册模型中保留；若某项不再有等价生命周期，必须重新设计，不得静默删除。
4. **Extras 运行时**：确认 `@WrapOperation` 等注入器来自哪个 provider，bootstrap 是否由 MixinBooter 自动完成，是否需要显式
   `MixinExtrasBootstrap.init()`，是否存在重复 provider、重复 service 或 shading/relocation 冲突。
5. **AP/refmap**：用候选版本重新生成并检查 refmap；核对 Java 8 bytecode、SRG/混淆环境、目标 descriptor 和 `remap=false` 边界。
6. **矩阵验证**：至少比较 10.7 基线与候选 11.x：仅 vanilla/Forge、每个 late mod 缺失和存在、服务端/客户端、核心目标命中/缺失、同一调用点多个
   wrapper 共存、保存/读取大堆叠和 handler remainder。
7. **回退界线**：任何 provider、注册、refmap、classloader 或容量证据不闭合时，候选版本标为 `UNKNOWN`，不宣称升级成功，不用上游新
   API 修补旧运行时。

## 3. CleanMix、MixinBooter 与 Forge 1.12.2 的边界

### 3.1 责任链

**[上游资料]** CleanMix/DeepWiki 对 Mixin 核心的职责可以按以下链路理解；这是架构图，不是对当前 10.7 jar 内部实现的逐类证明：

```text
Forge/FML + LaunchWrapper
  → MixinTweaker / bootstrap
  → MixinBootstrap.start / doInit / inject
  → platform manager + container + platform agent
  → IMixinService（与底层 classloader/side/logging 的 SPI）
  → MixinEnvironment + MixinConfig
  → MixinTransformer / processor
  → target ClassNode 预处理、合并、注入、写回字节码
```

- **Bootstrap**：把 Mixin 子系统接入启动器，建立环境、配置和 transformer。
- **Platform/container**：发现包含 Mixin 的 jar 或 classpath root，读取 manifest 中的配置/connector 信息，并按平台生命周期执行
  agent。
- **`IMixinService`**：隔离 LaunchWrapper 等启动器，负责字节码读取、类加载辅助、side 判断和日志等平台服务。
- **Transformation**：在目标类定义进入运行时前读取字节码，应用 Mixin 成员和注入器，再把结果交回 classloader；目标类可能已经被其他
  transformer 改写，不能只按开发环境反编译源码判断。
- **AP/refmap**：AP 在编译期解析 Mixin 注解、检查目标并写入混淆映射；refmap 在运行时把开发名映射到生产环境目标。两者是部署链的一部分，不是可有可无的日志文件。

**[建议]** 研究 10.7 时要区分三层：MixinBooter 的 Forge 侧桥接、Mixin/CleanMix 的核心转换器、MixinExtras 的扩展注入器。三者职责不同，不能把
`MixinBootstrap`（Mixin 核心/launcher 层）写成 MixinBooter 的 Forge 侧 loader，也不能把 `MixinExtras` 写成 Mixin 核心的一部分。

### 3.2 Java 8 和旧 LaunchWrapper 边界

- **[当前项目事实]** `mixins.stackupup.early.json:2-6` 明确声明 `compatibilityLevel: JAVA_8`；部分 late JSON
  也声明该级别，但并非所有配置字段一致，必须逐个文件核对。项目目标是 Minecraft 1.12.2/Forge 14.23.5.2847。
- **[建议]** handler、Mixin 类和生成字节码不得依赖 Java 9+ API 或高于目标运行时的 classfile；不要把现代 ModLauncher
  经验直接套到旧 LaunchWrapper。
- **[当前项目事实]** 静态目标方法的现有示例使用 Java `private static` handler，例如 `InventoryHelperMixin`、
  `PacketUtilMixin`；项目硬规则要求保持该形态。不要用 Kotlin `companion object + @JvmStatic` 伪装静态 handler。
- **[建议]** 任何改变 bootstrap、coremod 排除、classloader 或 Mixin 配置注册的变更，必须同时检查 coremod transformer、Mixin
  目标是否仍进入变换链，以及是否出现重复加载/缺失依赖；不能只检查编译结果。

### 3.3 AP、descriptor 和 refmap 的最低要求

1. 给重载方法写完整 descriptor，例如 `getInventoryStackLimit()I` 和 `getInventoryStackLimit(I)I` 必须区分。
2. `@At(target = ...)` 的 owner、name、descriptor 必须来自目标版本实际字节码；`Math.min(JJ)J`、
   `ItemStack#getMaxStackSize()I` 之类的公共目标也要核对调用处和参数顺序。
3. `remap=false` 只在目标已经是非混淆的第三方/Forge API 名称、或有明确映射边界时使用；不能为了消除 AP 报错而全局关闭
   remap。
4. `@Pseudo` 只解决可选目标类缺失的加载问题，不会替你验证目标方法、字段、descriptor 或行为。它必须与 mod presence
   gate、版本探针和注入匹配诊断一起使用。
5. AP 通过但 refmap 缺失、错误或没有覆盖生产混淆名时，运行时仍可能找不到注入点。构建验证必须检查生成 refmap 内容和最终 jar
   内位置。
6. MixinBooter、CleanMix 和 MixinExtras 的实际版本升级后，重新核对注解可用性、handler 签名、`order` 支持和
   bootstrap；DeepWiki 示例版本不能代替本地依赖源码。

## 4. 注入器决策表

下表是选择顺序，不是鼓励把所有逻辑都迁到 Extras。先写出目标字节码语义，再选最小能力的注入器。

| 工具                     | 职责                                                       | 适用场景                                                                     | 主要风险                                                                                 | 共存/链式规则                                                                                                                  |
|--------------------------|------------------------------------------------------------|------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `@Inject`                | 在明确 injection point 调用 callback                       | 入口校验、观察、返回前后副作用、无法用纯结果修改表达的局部逻辑               | `HEAD + cancellable` 可接管全部控制流；`locals` 依赖 LVT；取消顺序与其他 callback 竞争   | 普通 callback 可共存，但取消不是链式协议。核心目标设明确 `require`，不要用软失败隐藏失效。                                     |
| `@Shadow`                | 声明并访问目标已有字段/方法                                | handler 确实需要目标私有状态或已有方法                                       | 类型、static/final、可见性、名称、descriptor 或映射不匹配；内部字段变化会断裂            | 不是注入器，不存在“多 Shadow 更好”。只保留必要成员；不要用它猜容量。                                                           |
| `@Accessor` / `@Invoker` | 生成字段访问器/方法调用器                                  | 多个 Mixin 或独立桥接需要复用私有成员访问                                    | 目标缺失、签名不闭合、静态性不符、错误 remap；访问器接口本身仍依赖目标结构               | 不是业务注入，也不是容量证明。把访问集中在窄接口，避免每个 Mixin 各自 Shadow 同一内部字段。                                    |
| `@WrapOperation`         | 把一次调用、字段操作、构造或支持的表达式包装成 `Operation` | 需要查看参数、决定是否调用原操作、在保留原语义的前提下改变输入/输出          | handler 参数顺序/owner 不符；不调用或多次调用原 operation 会改变业务；包装层级需测试     | MixinExtras 的首选链式工具。要支持其他 Mixin，保留 `Operation` 并按契约调用原操作；“能链式”不等于 handler 可以随意吞掉原调用。 |
| `@ModifyExpressionValue` | 修改表达式已经产生的值                                     | 只想调整某个调用/字段/常量/`instanceof`/构造表达式的结果，不需要跳过原表达式 | 把表达式结果误当成真实写入能力；目标表达式匹配过宽；无法阻止原调用产生副作用             | MixinExtras 设计上适合叠加。保留 `original` 结果作为输入，说明修改后的业务不变量。                                             |
| `@ModifyReturnValue`     | 修改目标方法返回值                                         | 只需要后处理目标方法的返回值，不需重建方法体                                 | 返回值可能只是广告而非真实写入结果；多个返回修改的顺序需确认                             | 适合小范围结果调整；仍应核对实际 Extras 版本、order 和其他返回修改器，不把它当作容量闭合证明。                                 |
| `@ModifyArg`             | 修改某次调用的一个参数                                     | 只改一个调用参数，且不需接管调用                                             | ordinal/调用点错误；修改对象可能影响原调用契约；多个 modifier 的顺序不自动代表业务顺序   | 原生 Mixin 工具，通常比 Redirect 小；不具备 `WrapOperation` 的原操作控制协议，需测试多个 Mixin 共存。                          |
| `@ModifyArgs`            | 通过 `Args` 修改一次调用的多个参数                         | 同一调用确实需要联合修改多个参数                                             | 参数索引、类型和装箱错误；改动面过大                                                     | 只有多参数联合语义成立时使用；单参数优先 `@ModifyArg`。                                                                        |
| `@ModifyVariable`        | 修改目标方法中的局部变量                                   | 局部变量是唯一稳定的语义载体、且没有更窄的表达式/参数入口                    | LVT 会受其他 transformer/Mixin 改写；index/ordinal/name 和 slice 易漂移                  | 尽量后置选择；显式定位并做字节码验证，不把开发环境的 locals 视为生产保证。                                                     |
| `@ModifyConstant`        | 修改匹配到的常量                                           | 常量在目标版本中有明确唯一语义，且能用 slice/ordinal 限定                    | 相同常量多处出现；把字面量 `64` 当成容量证明；`require=0` 静默失效                       | 只在常量语义和写入路径均已证明时使用。不能默认替换所有 `64`。                                                                  |
| `@ModifyReceiver`        | 修改非静态调用/字段操作的接收者                            | 必须把操作转发给另一个接收者，且该替换本身是目标语义                         | receiver 类型/生命周期/dispatch 改变；可能绕过真实 delegate 限制                         | MixinExtras 设计上支持叠加；仍须证明新 receiver 的真实行为和容量，不可借 wrapper 类型推断能力。                                |
| `@WrapMethod`            | 包装整个目标方法                                           | 必须围绕完整方法建立前后语义，而非一个局部调用                               | 方法级控制流和异常/返回/副作用边界扩大；接近完整接管                                     | 只有方法级包装确有必要时使用；优先局部 `@Inject`、返回修改或 `@WrapOperation`。                                                |
| `@WrapWithCondition`     | 在保留原参数/字段操作形态下，按条件决定是否执行一次操作    | 只需要条件性执行调用或字段写入，且“不执行”是明确业务语义                     | 条件副作用、返回值和原调用链必须明确；条件函数不能误把容量拒绝当成 remainder 补偿        | MixinExtras 的可链式条件工具；先确认当前 provider 版本支持，再验证多个条件 wrapper 的组合和原操作最多执行一次。                |
| `@Redirect`              | 直接替换一次调用、字段访问或构造                           | 旧实现必须彻底替换操作，且不存在可接受的原操作语义；或已有代码暂时保留待迁移 | 通常不可链式，后加载的 Redirect 可能覆盖前一个；原调用不可见；调用点错误会悄悄改变控制流 | 新代码默认禁用。只有 `WrapOperation`/表达式/返回值/条件包装无法表达且有行为证据时保留，并在记录中说明不可迁移原因。            |
| `@Overwrite`             | 完整替换目标方法                                           | 仅在局部注入无法表达、且必须控制整个方法时                                   | 与目标版本、其他 Mixin、修复和异常语义强耦合；最容易丢失未来逻辑                         | 默认禁止。必须有完整方法源码/字节码对照、理由/作者、版本边界、变换后行为和冲突审计。                                           |

### 4.1 选择口诀

- 想“在某个点做事”：先看 `@Inject`。
- 想“包住一次原操作”：`@WrapOperation`。
- 只想“改已经算出的表达式”：`@ModifyExpressionValue`。
- 只想“改方法返回”：`@ModifyReturnValue`。
- 只想“换一个输入参数”：`@ModifyArg`；只想“换 receiver”：`@ModifyReceiver`；只想条件执行原操作：核对版本后使用
  `@WrapWithCondition`。
- 想“读写私有状态”：必要时 `@Shadow`，可复用访问使用 `@Accessor/@Invoker`。
- 想“让原调用消失”：先证明 wrapper/表达式无法表达，再考虑 `@Redirect`；默认不要新写。
- 想“重写完整方法”：先证明局部注入无法表达，`@Overwrite` 作为最后手段。

## 5. Shadow 的正确使用和“少 Shadow”现象

### 5.1 Shadow 的本质

**[上游资料]** `@Shadow` 是 Mixin 预处理阶段识别的目标成员声明桥，不是控制流注入器。它告诉 Mixin：“目标类已经有这个字段/方法，请允许
Mixin 代码按这个签名访问它。”它不会替目标方法扩容、不会验证 delegate 的写入容量，也不会让未知第三方类变成可信实现。

**[建议]** 需要状态时使用 Shadow 是正常且必要的；不需要状态时为了降低“Shadow 太少”的观感而新增 Shadow 是错误方向。可复用访问优先集中为
accessor/invoker 接口，但 accessor 也必须有目标版本证据。

### 5.2 Shadow 准入检查

每个 Shadow 字段/方法都要在交接记录中回答：

1. 目标类在当前版本是否实际声明该成员，而不是只从父类、接口或同名类猜测。
2. 字段类型、数组/泛型擦除后的 JVM 类型、方法参数和返回值是否完全一致。
3. static/instance 是否一致；目标 `final` 字段是否用 `@Final` 正确反映；没有充分理由不得用可变注解绕过 final。
4. 方法是否写完整 descriptor；重载不能靠短方法名区分。
5. `remap` 是否与目标名称来源匹配；第三方非混淆名称才考虑 `remap=false`，不能把 AP 错误一律改成 `remap=false`。
6. 访问是否真的服务于该 handler 的语义；若只是为了读取容量，必须继续追到真实 setter/insert，而不是把 Shadow 值当作写入证明。
7. 多个 Mixin 是否可以共享同一个 accessor，而不是各自复制一组内部 Shadow。

### 5.3 当前项目的 Shadow 风险样例

- `src/main/java/io/alexjoest/stackupup/mixin/early/NetHandlerPlayServerMixin.java:21-23` Shadow `player` 与
  `itemDropThreshold`，用于创造模式包处理；这些字段访问本身合理，但 `:25-56` 的 `HEAD + cancellable` 已接管整个方法，风险不在
  Shadow 数量，而在控制流、线程检查、槽位更新、丢弃和取消语义是否完整。
- `src/main/java/io/alexjoest/stackupup/mixin/early/ItemStackNbtMixin.java:21-37` Shadow 多个 `ItemStack` 字段，`:52-71`
  在 `writeToNBT` 的 HEAD 取消并重建序列化；这是持久化高风险，不应以“字段都 Shadow 了”当作完整性证明。必须逐项对照目标版本的
  id/count/damage/tag/capability 和返回对象语义。
- `src/main/java/io/alexjoest/stackupup/mixin/early/SlotItemHandlerMixin.java:16-17` Shadow `getSlotStackLimit`，`:24-39`
  修改返回值；该 Shadow 只能提供 handler 查询，不能证明未知 handler 的真实 insert/setter 能写入动态上限。当前
  `original == 64` 分支和其他原值分支的已知限制见 `docs/agent/2026-04-18-hard-rules.md:65-79`。
- `src/main/java/io/alexjoest/stackupup/mixin/late/BrandonsCoreInventoryLimitMixin.java:20-21` 的字段 Shadow
  若要保留，必须有对应版本第三方源码或字节码；缺少 jar 时统一写 `无源码不可判定`，不能用注释“protected int
  stackLimit”替代外部证据。

## 6. Redirect 到 MixinExtras 的迁移规则

### 6.1 迁移流程

迁移一个旧 Redirect 时按顺序执行，不得只做注解字符串替换：

1. **锁定调用点**：记录目标方法完整 descriptor、调用 owner/name/descriptor、是否静态、是否构造/字段操作、ordinal/slice
   和调用参数顺序。
2. **写出旧语义**：旧 handler 是替换返回值、改参数、改 receiver、跳过原调用，还是依赖目标对象状态。确认旧 Redirect
   是否真的应该调用原操作。
3. **选择最小替代**：
    - 需要保留或条件调用原操作：`@WrapOperation`；保留 `Operation<T>`，按明确契约调用原操作。
    - 只改变操作产出的值：`@ModifyExpressionValue`。
    - 只改变整个目标方法的返回值：`@ModifyReturnValue`。
    - 只改变 receiver：`@ModifyReceiver`。
    - 只需读写稳定局部变量：`@Local`；要修改局部变量使用相应 `LocalRef`，多个 injection point 共享状态再考虑 `@Share`。
4. **保留业务边界**：包装器不得把 `simulate` 当真实写入，不得在原调用后补偿 remainder；容量处理必须仍然追到 delegate 和
   setter。
5. **证明共存**：至少验证两个 wrapper 同时存在时的调用顺序、每个 wrapper 是否调用原操作、原操作调用次数和最终返回/落库状态。
6. **不具备等价语义时保留并登记**：如果旧 Redirect 的“完全阻止原操作”无法由合适的 wrapper/表达式安全表达，暂时保留
   Redirect；记录原因、目标版本、不可链式影响和后续测试，不得伪称已迁移。

### 6.2 当前代码的迁移候选

- `EntityItemMergeMixin.java:11-20` 使用 `@Redirect` 替换 `ItemStack#getMaxStackSize()I`。它是明确的 `INVOKE`
  操作包装候选：迁移前需确认 `candidate`、当前堆栈和 `other` 的语义，然后用 `Operation<Integer>` 保留原值并计算合并上限。不能仅凭“Redirect
  禁止”删除原逻辑。
- `InventoryPlayerAddResourceMixin.java:12-43` 有三个 Redirect，分别影响 `canMergeStacks` 与 `addResource`
  的调用点。每个点必须独立确认是纯结果修改还是需要 receiver/参数/原操作；不能把三个调用点合并成一个泛化 hook。
  `resolveInventoryClampLimit` 仍有两个业务调用方，删除或改签名前必须逐一处理。
- `ItemGridHandlerMixin.java:13-24` 与 `ItemGridHandlerPortableMixin.java` 已采用 `@WrapOperation`，方向正确；但当前
  handler 接收 `Operation<Long>` 却不调用 `original`，语义上仍接近“硬替换”。必须证明这是有意替换 `Math.min(JJ)J`，并验证其他
  wrapper 叠加时不会丢失原逻辑。
- `RenderItemMixin.java:13-29`、`AppEngAdaptorItemHandlerMixin.java:15-30` 也接收 `Operation`
  但当前实现不调用它们。前者需验证渲染原操作是否有意被替换；后者连接未知 handler 的真实插入路径，必须单独做
  remainder/守恒审计，不能仅因注解是 `WrapOperation` 就视为安全链式包装。
- `ContainerMixin.java:14-26` 是较好的包装形态：读取 `original.call(slot)` 后再做 item-aware 限制。它仍不能单独证明所有库存真实写入容量已经同步。
- `RefinedStorageMixinSourceTest.kt:11-19` 只做源码结构护栏：检查包含 `WrapOperation` 且不含 `@Redirect`。它不能证明运行时
  injection 命中、原操作调用次数、调用链顺序或容量守恒，必须有行为/字节码验证补足。

## 7. 目标选择、失败门与可选兼容

### 7.1 目标必须闭合

每个 injection 在代码和交接记录中至少写明：

- target 类全名及 early/late/optional 属性；
- 目标方法完整 JVM descriptor，构造器用正确 `<init>` 形式；
- `@At` 类型、owner/name/descriptor、ordinal；
- 必要时的 `slice` 起止点和 `shift`；`At.Shift.BY` 只能在字节码位置稳定且有证据时使用；
- handler 参数顺序、receiver、原始表达式/返回值类型以及是否静态；
- `remap`、`@Pseudo`、Mixin config 和 refmap 关系；
- 预期匹配数和在目标版本中实际匹配数。

避免：只写短方法名、依赖第一个相同调用、用任意常量作为位置标记、用 `HEAD` 代替真正语义位置、用大 `shift` 跨越不稳定代码。

### 7.2 核心目标和可选目标的不同门槛

**核心原版/Forge 路径**：

- 注入目标缺失、descriptor 不符或匹配数不是预期值时 fail fast；
- 显式设置最低成功注入要求，避免默认软失败掩盖核心功能失效；
- 不用 `required=false` 或 `require=0` 将核心错误改成启动成功。

**可选第三方路径**：

- 先由 mod presence、配置开关和目标版本探针决定是否加载；
- 配置级 `required=false`、注入器级 `require=0` 和 `@Pseudo` 必须分别登记：前者只改变配置失败的终止性，第二个只改变该
  injector 的最低命中数，后者只影响可选目标类加载；三者不能互换，也不能单独证明“该版本可能不存在”或“功能不重要”；
- 缺失时产出结构化记录：mod ID、jar 版本、配置名、目标类、方法 descriptor、注入点、匹配数、跳过原因；
- 目标存在但 descriptor 或行为不符时记为失败/不兼容，不得伪装成缺失；
- 测试必须覆盖“mod 不存在”“目标类存在但方法不存在”“目标匹配成功”“目标版本变更”四类结果。

#### 7.2.1 四个失败门的层级（禁止互换）

`required`、`require`、`expect` 和 `injectors.defaultRequire` 位于不同层级，不能用一个字段代替另一个：

- **配置级 `required`**：Mixin JSON 的配置级门，控制该配置初始化、版本/特性检查或其 mixin 处理失败时是否视为终止性错误；它不等于“配置中的每个
  injector 至少命中一次”。官方 UniMix commit `9d4b487ed3` 的 [
  `MixinConfig.java`](https://raw.githubusercontent.com/CleanroomMC/UniMix/9d4b487ed3/src/main/java/org/spongepowered/asm/mixin/transformer/MixinConfig.java)
  将 `required` 解析为 boxed value，根配置省略时在 `onLoad` 中得到 false，子配置省略时还可能按 parent 继承；这属于版本敏感的上游源码语义。
- **注入器级 `require`**：`@Inject` 等 injector 注解上的最低成功回调数。官方 UniMix [
  `Inject.java`](https://raw.githubusercontent.com/CleanroomMC/UniMix/9d4b487ed3/src/main/java/org/spongepowered/asm/mixin/injection/Inject.java)
  显示省略值为 `-1`；[
  `InjectionInfo.java`](https://raw.githubusercontent.com/CleanroomMC/UniMix/9d4b487ed3/src/main/java/org/spongepowered/asm/mixin/injection/struct/InjectionInfo.java)
  仅在显式值为非负数时直接采用，否则 default group 才回退到 `getDefaultRequiredInjections()`。`require=0` 是该 injector
  的零最低命中门，不是配置级 `required=false` 的同义词。
- **`expect`**：注解级预期回调数。上述官方 [
  `Inject.java`](https://raw.githubusercontent.com/CleanroomMC/UniMix/9d4b487ed3/src/main/java/org/spongepowered/asm/mixin/injection/Inject.java)
  的默认值是 `1`，[
  `InjectionInfo.java`](https://raw.githubusercontent.com/CleanroomMC/UniMix/9d4b487ed3/src/main/java/org/spongepowered/asm/mixin/injection/struct/InjectionInfo.java)
  只在 `mixin.debug.countInjections`/`DEBUG_INJECTORS` 开启时用它做检查；它是 debug 诊断门，不是生产环境的最低成功要求。
- **`injectors.defaultRequire`**：Mixin JSON 的 `injectors` 对象中的默认 `require`，不是配置级 `required`。同一官方 [
  `MixinConfig.java`](https://raw.githubusercontent.com/CleanroomMC/UniMix/9d4b487ed3/src/main/java/org/spongepowered/asm/mixin/transformer/MixinConfig.java)
  将其默认值设为 `0`；它只为省略/`-1` 的 injector `require` 提供 default-group 回退，且 parent merge 仍是版本敏感行为。

以上默认值和省略语义是官方 UniMix `9d4b487ed3`（MixinBooter 10.7 上游 build 所引用的 commit）证据，不自动代表当前项目实际
jar、其他 Mixin fork 或 11.x CleanMix；实际 provider/jar 未核验时必须保持 **[UNKNOWN]**。当前 early/late JSON 的
`required`、`injectors.defaultRequire` 以及每个 injector 的 `require`/`expect` 必须由 T14.5 逐文件登记，不能从模块名、
`required=false` 或默认值推断。

**当前基线静态结果（未完成 T14.5）**：`mixins.stackupup.early.json` 未声明 `required`；
`mixins.stackupup.late.integrateddynamics.json`、`enderio.json`、`cyclopscore.json`、`brandonscore.json`、
`immersiveengineering.json`、`limelib.json` 声明 `required=false`；当前资源 JSON 静态检索未发现 `injectors.defaultRequire`
。这只是现状记录，不是逐文件目标/loader/日志/refmap/AP 登记，也不是核心 fail-fast 或 optional skip 已通过的证明。

**当前样例**：`ItemGridHandlerMixin.java:13-17` 的 `require=0`
可以作为第三方版本差异的可选门，但当前代码没有在该处展示结构化缺失报告；重构时必须补齐诊断或在更上层明确记录，不能让它静默失效。

### 7.3 不默认使用的写法

- `@Overwrite`：除非完整方法替换是唯一可表达方案，并有源码/字节码、版本、异常、返回和共存审计。
- `@Inject(at = @At("HEAD"), cancellable = true)`：除非完整控制流接管是必要的；优先局部 injection 或包装表达式。
- `@ModifyConstant(constant = 64)`：除非常量在该位置有唯一业务语义，并由 slice/ordinal 和写入测试证明；字面量 `64` 不是容量语义。
- `@Redirect`：新代码禁用；旧代码迁移前按第 6 节建立等价行为证据。
- `@Shadow`：不为读取“可能的容量”而新增；不以 Shadow 绕过未知 wrapper/handler 的写入证据。
- `require=0`：不用于掩盖 AP、descriptor 或核心目标错误。

## 8. StackUpUp 当前源码的高风险审查清单

以下是审查入口，不是“已经修复”的清单：

| 文件与位置                                                                                                                                | 当前事实                                                                                                                                                   | 重构审查动作                                                                                                                                                           |
|-------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `src/main/java/io/alexjoest/stackupup/mixin/early/EntityItemMergeMixin.java:11-20`                                                        | `@Redirect` 替换合并流程中 `ItemStack#getMaxStackSize()I`                                                                                                  | 记录调用点和合并双方的真实上限；评估 `@WrapOperation`，验证原操作调用、合并容量和 remainder/实体数量结果。                                                             |
| `src/main/java/io/alexjoest/stackupup/mixin/early/InventoryPlayerAddResourceMixin.java:12-43`                                             | 三个 Redirect 分布在合并和资源加入路径                                                                                                                     | 每个调用点单独建立语义；检查 `resolveInventoryClampLimit` 两个调用方；禁止用一次泛化替换掩盖不同的 source/target/limit 关系。                                          |
| `src/main/java/io/alexjoest/stackupup/mixin/early/NetHandlerPlayServerMixin.java:21-56`                                                   | Shadow 两个字段，HEAD cancellable 重建创造模式包处理                                                                                                       | 对照目标版本完整方法；覆盖线程切换、创造模式、非法槽位、空栈、BlockEntityTag 清洗、slot 更新、丢弃阈值和取消后的后续逻辑。优先减少完整控制流接管。                     |
| `src/main/java/io/alexjoest/stackupup/mixin/early/ItemStackNbtMixin.java:21-71`                                                           | 多 Shadow，`writeToNBT` HEAD cancellable 重建 NBT                                                                                                          | 逐字段对照目标版本；覆盖空 item、tag alias、ForgeCaps、读取/写入往返、未知字段保留和其他 Mixin 共存。不能只验证 Count 变大。                                           |
| `src/main/java/io/alexjoest/stackupup/mixin/early/SlotItemHandlerMixin.java:16-39`                                                        | Shadow slot limit，修改 slot limit 和 item-aware limit                                                                                                     | 先确认 handler 的真实 insert/setter；未知 handler 不扩容；不能把 `original == 64` 分支当作安全通用证明。                                                               |
| `src/main/java/io/alexjoest/stackupup/mixin/early/ForgeItemHandlerLimitMixin.java:15-33`                                                  | 对 `ItemStackHandler`、多个 Forge wrapper 和 `EntityEquipmentInvWrapper` 统一修改 `getSlotLimit`                                                           | 按目标拆分查询—写入链；wrapper 只作转发证据，不能独立抬高；装备 armor/hand、`insertItem`、`setStackInSlot` 和 vanilla setter 分开验证。                                |
| `src/main/java/io/alexjoest/stackupup/mixin/early/SlotLimitMixin.java:12-27`                                                              | 修改 slot item limit；正的 inventory limit 才 clamp                                                                                                        | 覆盖非正 inventory limit；广告、实际 setter 和 handler insert 要同源；不能用 GUI/slot 返回值掩盖服务端容量。                                                           |
| `src/main/java/io/alexjoest/stackupup/mixin/early/InventoryHelperMixin.java:17-25`                                                        | `HEAD + cancellable` 完整替换原版掉落拆分                                                                                                                  | 对照原版实体生成、空栈、随机拆分和总数守恒；确认这是业务重写而非可局部包装的调用点。                                                                                   |
| `src/main/java/io/alexjoest/stackupup/mixin/early/PacketBufferMixin.java:16-59`                                                           | 读写 `ItemStack` 的两个 `HEAD + cancellable` 协议替换                                                                                                      | 对照 1.12.2 原版协议、空栈、id、count、damage、share tag、异常和读写往返；验证客户端/服务端两端一致。                                                                  |
| `src/main/java/io/alexjoest/stackupup/mixin/early/PacketUtilMixin.java:16-39`                                                             | 客户端到服务端 ItemStack 写入的 `HEAD + cancellable` 替换                                                                                                  | 与 `PacketBufferMixin` 一起做协议矩阵和异常验证；不能把编解码替换当成普通返回值修改。                                                                                  |
| `src/main/java/io/alexjoest/stackupup/mixin/early/VanillaInventoryWriteMixin.java:33-49`                                                  | 多种 inventory 的 setter 前后都使用 `require=0`                                                                                                            | 逐目标确认 setter 是否声明、写入是否真正发生、异常时 begin/end 是否成对；核心目标不应靠软失败掩盖缺失。                                                                |
| `src/main/java/io/alexjoest/stackupup/mixin/early/NetHandlerPlayClientMixin.java:17-50`                                                   | 先调用原 setter/setAll，再恢复客户端堆叠数量                                                                                                               | 将其定义为客户端同步路径而非 remainder 补偿；验证服务端权威状态、容器更新顺序、空槽和多槽列表，避免与禁止事后补偿规则混淆。                                            |
| `src/main/java/io/alexjoest/stackupup/mixin/late/AppEngAdaptorItemHandlerMixin.java:15-30`、`core/Ae2ItemHandlerInsertLimiter.java:18-57` | 可选 `WrapOperation` 接入未知 handler；真实分支会按 cap 分片调用 `insertItem(..., false)` 并重建返回 remainder，handler wrapper 当前不调用传入 `Operation` | 这是已登记的当前已知限制，不得与“未知 handler 不扩容”混为一谈；逐调用记录 offered/落库/remainder，证明预先限幅与事后补偿的边界；第三方内部写入缺证据时标为 `UNKNOWN`。 |
| `src/main/java/io/alexjoest/stackupup/mixin/late/ItemGridHandlerMixin.java:13-24` 与 portable 版本                                        | `@WrapOperation` 带 `require=0`，当前不调用 `original`                                                                                                     | 加载缺失诊断；确定是否有意替换 `Math.min`；验证多 wrapper 叠加、原 operation 调用契约和抽取真实结果。                                                                  |
| `src/main/java/io/alexjoest/stackupup/mixin/early/RenderItemMixin.java:13-29`                                                             | 渲染文本调用使用 `@WrapOperation` 但当前不调用 `original`                                                                                                  | 确认是否有意完全替换字体绘制；验证客户端渲染、颜色/坐标/空文本和其他渲染 Mixin 共存，不能仅凭注解名称认为它是保留原调用的 wrapper。                                    |
| `src/main/java/io/alexjoest/stackupup/core/DynamicCompatMethodProbe.java:32-58`、`core/CompatibilityLimitPatch.java:56-85`                | 动态 ASM 只按方法名识别，并在命中方法中替换所有 `BIPUSH 64`；当前 probe 不按 descriptor、常量位置或语义确认                                                | 不能把方法名/常量命中写成目标闭合；逐个补 descriptor 和语义位置证据，确认 Mixin 已接管目标进入 `FixedCompatTargets`，并检查动态 transformer 不二次命中。               |
| `src/test/kotlin/io/alexjoest/stackupup/mixin/RefinedStorageMixinSourceTest.kt:11-19`                                                     | 只检查源码含 WrapOperation 且不含 Redirect                                                                                                                 | 保留为结构护栏，但增加真实行为/变换后字节码/注入匹配验证；不能把测试名当运行时证明。                                                                                   |
| `src/main/kotlin/io/alexjoest/stackupup/StackUpUpCore.kt:3-13,88-93`                                                                      | 当前 core 入口实现 `IEarlyMixinLoader` 并条件注册 early JSON                                                                                               | 只在确认 MixinBooter 版本后迁移；记录冲突 coremod、排除包和 LaunchWrapper classloader 影响。                                                                           |
| `src/main/kotlin/io/alexjoest/stackupup/bootstrap/StackUpUpLateMixinLoader.kt:5-15,18-35`                                                 | 当前 late loader 按 Context、mod presence、toggle 排队配置                                                                                                 | 11.x 不得直接沿用；迁移时保留每个 mod 的可选边界和失败诊断。                                                                                                           |

### 8.1 当前实现的额外已知限制

- **[当前项目事实] AE2 未知 handler 路径**：
  `src/main/java/io/alexjoest/stackupup/core/Ae2ItemHandlerInsertLimiter.java:18-57` 对不在白名单的 handler 计算
  `min(64, getSlotLimit)`；大于 cap 时，真实 `simulate=false` 分支会循环调用多个 chunk，并用 `accepted`/`remainderOf`
  重建返回余量。`src/main/java/io/alexjoest/stackupup/mixin/late/AppEngAdaptorItemHandlerMixin.java:15-30` 以可选
  `@WrapOperation` 接入该路径，但没有调用传入的 `Operation`。这不是“未知 handler 已扩容”的证明，也不能被本规范批准为无风险；它是当前已登记的
  remainder/未知写入链限制，必须逐次记录真实落库和 remainder，并在证据闭合前保持 `UNKNOWN`。
- **[当前项目事实] 动态 ASM 未闭合**：`DynamicCompatMethodProbe.java:32-58` 只按方法名识别，
  `CompatibilityLimitPatch.java:56-85` 在匹配方法内替换所有 `BIPUSH 64`；`DynamicCompatTransformer.java:21-49` 负责应用补丁，
  `FixedCompatTargets.java:29-64` 负责固定跳过表。当前实现没有按 descriptor 和常量语义位置建立完整单一事实源；重构时必须先核对
  Mixin 目标、固定表、动态 transformer 的避让关系，不能把 ASM 命中写成容量或方法语义证明。
- **[当前项目事实] 其他完整控制流/协议替换**：`InventoryHelperMixin.java:17-25`、`PacketBufferMixin.java:16-59`、
  `PacketUtilMixin.java:16-39` 都在 HEAD 取消原方法，分别涉及实体掉落拆分和 ItemStack
  网络协议；它们必须按原版源码、异常、两端读写和数量守恒审查，不能套用普通返回值修改的低风险判断。
- **[当前项目事实] coremod 异常分类缺口**：`StackUpUpCore.kt:35-49` 的冲突检测捕获 `Throwable` 后返回空列表；异常可能被误判为“没有冲突”。这与
  Fail Fast 规则冲突，迁移或重构时必须保留可定位诊断并单独测试，当前不能标为已闭合。
- **[当前项目事实/UNKNOWN] 核心注入的显式失败门尚未逐项收敛**：early JSON `:1-7` 未声明 `required: true`；
  `EntityItemMergeMixin.java:11-17`、`ContainerMixin.java:14-20`、`ItemStackNbtMixin.java:39-52`、
  `PacketBufferMixin.java:16-20,36-39` 未显式写最低 `require`。这些是代表性样例，不是完整枚举；完整清单必须逐项扫描 early
  JSON 及其全部 injection。当前框架默认行为及最终匹配结果未由本文件运行验证，因此这里只能记录为“规则要求与现状之间的审查项”，不能写成核心
  fail-fast 已通过。

### 8.2 容量目标的逐项审计表

任何新的容量兼容目标都应先建立一行审计记录，至少包含：

```text
目标类 / jar 版本
广告方法：getInventoryStackLimit、getSlotLimit、slot limit 或 GUI 查询
真实写入方法：insertItem、setInventorySlotContents、setStackInSlot、delegate setter、持久化入口
delegate 链：每一层查询和写入的来源
空槽 / 已有堆叠 / 满槽行为
simulate=true 结果
simulate=false：offered、写入前后数量、storedDelta、remainderCount
守恒：storedDelta + remainderCount == offered
Mixin/ASM 目标与完整 descriptor
证据状态：自洽 / 转发 / 断链 / 无源码不可判定
```

`EntityEquipmentInvWrapper` 尤其要分开：Forge `insertItem` 会计算上限并在超量时返回 remainder；`setStackInSlot`/vanilla
setter 是另一条写入路径。不能写成“wrapper 无余量必吞”，也不能因为 setter 看起来直接就替代 Forge 的 insert 契约。

## 9. 可执行验证矩阵

以下是实现任务完成前的最低验证面；本文件创建任务没有运行这些生命周期命令。

### 9.1 结构与字节码

- Mixin AP 成功，refmap 生成且包含每个需要混淆的目标；检查最终 jar 中配置和 refmap 路径。
- 目标方法 descriptor、静态性、handler 参数和 `@At(target)` 与目标版本字节码一致。
- 核心 injection 的匹配数符合预期；可选目标的 `require=0` 只在明确 optional 情况使用，并留下日志/报告。
- 检查转换后类：旧 Redirect 是否真的被移除、WrapOperation 是否保留正确 `Operation` 链、原调用次数是否正确、没有重复
  ASM/Mixin 改写。
- 对动态 ASM 额外检查 `DynamicCompatMethodProbe` 的 descriptor 盲区、`CompatibilityLimitPatch` 的 `BIPUSH 64` 语义位置、
  `DynamicCompatTransformer` 的空输入/重复命中以及 `FixedCompatTargets` 与显式 Mixin 目标逐项对齐。
- 对核心 injection 分开检查显式注入器级 `require`、配置级 `required`、`injectors.defaultRequire` 与实际匹配数；`expect` 只按
  debug-only 语义单独登记；若仍依赖框架默认值，必须标为未收敛而不是默认通过。
- 按项目现有门槛覆盖 `CoremodHierarchyBytecodeSafetyTest`、`EarlyMixinBytecodeSafetyTest` 和 `MixinBooterIntegrationTest`
  ；这些是静态/集成门槛，不能替代真实容量行为。

### 9.2 Mixin 共存

至少安排两个独立 wrapper/修改器同时作用于同一调用点，观察：

- 每个 handler 是否命中；
- `Operation` 是否按约定调用，是否恰好一次；
- 返回值/参数/receiver 的顺序是否符合设计；
- 某个可选目标缺失时，另一个核心目标是否仍按预期失败或通过；
- 不同 priority/order 只是解决已证明的冲突，不作为没有行为证据的默认修复。

### 9.3 StackUpUp 行为

- vanilla/Forge 基础路径：合并、slot 写入、玩家资源加入、实体掉落、创造模式包、客户端 slot/window 同步。
- NBT：小于等于 64 与大于 64 的读写往返，空 tag、ForgeCaps、未知字段、重载后再保存。
- handler：空槽、有相同堆叠、有不同堆叠、已满槽、`offered` 小于/等于/大于限制，`simulate=true/false`，非零/全量 remainder。
- wrapper：`ItemStackHandler`、转发 `InvWrapper`/`SidedInvWrapper`/`CombinedInvWrapper`/`RangedWrapper`、
  `EntityEquipmentInvWrapper` 的 armor 与手部；分别检查 delegate 和 setter 路径。
- 可选第三方：mod 缺失、正确版本、目标类缺失、方法 descriptor 变更、目标存在但注入点消失；结果分别是 skip、pass 或 fail，不能全部记为
  skip。
- 每个真实写入调用输出机器可读审计：目标类、slot、simulate、offered、落库量、remainder、守恒结果。报告只能观察，不可回填或重试。

### 9.4 运行任务边界

涉及 coremod、Mixin、MixinExtras、自动化参数或 late loader 时，项目规范要求至少覆盖相应的 `runServerAutoTest`；矩阵任务使用项目现有的
`runServerAutoTestMatrix` 或明确的 `run*AutoTest` 入口。运行前需确认本地 jar、FML 扫描目录、server/client 侧和自动化开关，避免把缺依赖误报成
Mixin 失败。

未实际运行的检查必须在交接中写“未执行”，不得把静态搜索、编译通过或文档推理写成运行通过。

## 10. 代理工作流与交接模板

### 10.1 工作流

1. **调查**：读根级 `AGENTS.md` 和目标目录规范；检查目标文件/源码现有改动；列出精确文件租约。搜索符号后用调用层级确认入口，不用类名猜调用链。
2. **版本核对**：读当前依赖坐标和 Mixin 配置；查询五个官方仓库与对应 DeepWiki 页面；记录版本冲突、索引失败和缺失 jar。
3. **目标闭合**：拿到目标版本源码或字节码，确认完整 descriptor、调用点、owner、参数、receiver、局部变量和写入 delegate。
4. **注入决策**：按第 4 节选最窄注入器；新逻辑默认不用 Redirect/Overwrite；需要私有状态才 Shadow；容量逻辑先闭合真实写入链。
5. **最小实现**：只写租约文件；不把可选目标变成核心目标，不通过 `require=0` 隐藏错误，不引入事后 remainder 补偿。
6. **结构验证**：检查 AP、refmap、变换后字节码、匹配计数和日志；确认没有重复 ASM 命中或与固定 Mixin 冲突。
7. **行为验证**：运行核心、可选、共存、客户端/服务端、simulate/remainder 和容量矩阵；记录实际命令和结果。
8. **独立复核**：作者不能自审。由独立代理逐项复核目标 descriptor、注入选择、容量守恒、失败分类和测试证据。
9. **交接**：只报告真实修改和真实验证；任何缺失源码、未运行命令、版本未锁定或冲突未解决都写为 `UNKNOWN`/`无源码不可判定`。

### 10.2 每个实现任务的交接记录

交接文档或任务描述必须包含以下实际内容，不能留空项：

- **目标**：一句话写清验收条件，例如“在 Forge 1.12.2 下仅包装已确认的 `insertItem` 调用，并证明广告容量不超过真实写入容量”。
- **允许修改**：列出精确的源码、配置和测试路径；未列出的路径不可写。
- **禁止事项**：列出禁止的 Redirect/Overwrite、未知 handler 扩容、写入后补偿、额外 runtime jar、生命周期命令或外部操作。
- **基线证据**：列出本地源码/字节码路径、当前依赖坐标、官方仓库 URL、DeepWiki 页面、缺失 jar 和冲突记录。
- **安全不变量**：写出容量守恒、simulate 分离、原操作调用契约、Fail Fast 和 Mixin 优先/ASM 兜底边界。
- **注入选择**：说明为什么用 `@Inject`、`@WrapOperation`、`@ModifyExpressionValue`、`@ModifyReturnValue`
  或其他工具，以及为什么没有用更高风险工具。
- **验证方式**：逐条列出实际执行的结构检查、测试、运行任务和结果；未执行项必须标明未执行及原因。
- **代理分工与租约**：声明作者、独立复核者、每个文件的唯一写租约和交接边界。
- **结论状态**：只能使用 `PASS`、`FAIL`、`BLOCKED`、`UNKNOWN` 或 `PARTIAL`。没有独立复核、运行证据或关键第三方源码时，不得写
  `PASS`。

### 10.3 UNKNOWN 的处理规则

`UNKNOWN` 不是暂时的“可以先按安全处理”的同义词，也不是失败证据。代理必须同时写：

1. 缺什么：具体 jar、版本、源码、字节码、运行日志或目标 descriptor；
2. 为什么缺失会影响结论：例如无法确认 delegate setter 是否截断、无法确认 `MixinExtrasBootstrap` provider、无法确认目标方法重载；
3. 当前安全动作：不扩容、不加载可选配置、保留原 operation、让核心目标 fail fast；
4. 需要谁补齐以及如何验证：取得对应 artifact、做 AP/refmap 检查、导出变换类或运行真实 `simulate=false` 矩阵。

不要把“类名看起来像 wrapper”“注释写着 stackLimit”“DeepWiki 有同名页面”改写成写入路径证据。对第三方 jar 缺失时使用项目统一措辞：
**无源码不可判定**。

## 11. 最终原则

- 选择注入器是语义决策，不是注解数量竞赛。
- `@Shadow` 少不是缺陷；只有确实需要目标私有状态时才使用 Shadow，并严格匹配类型、static/final、descriptor 和 refmap。
- 新代码优先 `@WrapOperation`、`@ModifyExpressionValue`、`@ModifyReturnValue`、`@ModifyReceiver` 等可表达局部语义的工具；操作包装必须保留并正确使用原
  operation。
- `@Inject` 的局部 callback 优于无理由的完整方法接管；`HEAD + cancellable`、`@Redirect` 和 `@Overwrite` 都要承担明确的共存与行为证明责任。
- MixinBooter 10.7 是当前项目事实；MixinBooter 11.x 的 CleanMix、manifest 注册和 CleanroomMC MixinExtras fork
  是上游资料，直到来源、API、打包、classloader 和运行矩阵全部完成前都不是当前实现。
- 容量任务永远同时审查广告和真实写入：`storedDelta + remainderCount == offered`，区分 simulate，禁止写入后补偿；未知 handler
  不动态扩容。
- Mixin 能表达时优先 Mixin，确实不能表达且证据闭合时才用窄范围 ASM；任何未验证项如实保持 `UNKNOWN`。
