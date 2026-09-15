# Mixin 生态与注入最佳实践

> 状态：PARTIAL（2026-08-10，同步当前迁移事实；未完成独立复核）

> 面向 StackUpUp 重构代理的执行规范，只规定调查、选择、实现和验证 Mixin 的方法；当前项目基线为 MixinBooter 11.13 + CleanMix 0.7.1 + `IMixinConnector`。本文保留 10.7/旧 loader 作为历史对照，不把上游资料、dev/SRG 运行证据或未完成矩阵写成发布准入通过。
>
> 结论标签：**[当前项目事实]**（当前项目源码/构建配置/规范直接定位）；**[上游资料]**（官方仓库 README/源码或 DeepWiki；精确 API 仍以实际依赖 jar 和目标版本核对）；**[建议]**（重构必须遵守）；**[UNKNOWN]**（证据不足，保持收缩、跳过可选目标或补齐证据）。
>
> “链式”只表示多个注入可在同一操作上共存，不表示业务语义自动正确；是否调用原操作、调用几次、传什么参数，由每个 handler 的行为测试证明。

## 1. 定位、范围与不可协商的不变量

### 1.1 任务先回答四件事

1. **目标**：改方法入口/出口、表达式、调用参数、接收者、私有状态，还是完整控制流。
2. **真实写入或业务路径**：容量任务必须同时看到广告面和真实写入面，不能只改 GUI、slot 查询或返回值。
3. **加载阶段**：当前使用 11.13 的 manifest `MixinConnector`/`IMixinConnector` 注册方式；10.7 的 early/late loader 仅作为历史对照，不能与当前入口混写。
4. **共存证明**：选择可共存的注入器，记录目标 descriptor、匹配数、原操作调用次数和行为结果。

本文不授权修改代码（文件租约由上层任务给出）；源码搜索、静态 `contains` 或类名相似性不是运行行为证明。

### 1.2 安全不变量（优先于“看起来能工作”的方案）

- **容量不变量**：对外广告容量不得大于真实写入容量；证据来自目标对象的实际查询、限制、setter/insert 和持久化路径，不来自类名、注释、`== 64` 哨兵或未知实现的主动表态。
- **插入守恒**：真实 `IItemHandler#insertItem` 审计满足 `storedDelta + remainderCount == offered`；这是只读审计公式，不是写入后的回填、重试或补偿算法。
- **simulate 分离**：`simulate=true` 只是预览；`simulate=false` 才是真实写入证据。审计分别记录 `offered`、实际落库量、原调用返回的 `remainder`、目标类和 `slot`。
- **禁止事后补偿**：真实写入后禁止重新计算余量、回填源栈或槽位、重试、补偿，或以事后状态改写业务结果；守恒审计只能观察、报告和失败。
- **未知 handler 不扩容**：未知 `IItemHandler` 的 `getSlotLimit`、wrapper 返回值或接口类型不能证明其内部 setter、delegate 和持久化路径可承载更大堆叠；无写入证据就保持原上限。
- **Mixin 优先、ASM 兜底**：目标类、方法、完整 descriptor 和行为明确时优先窄范围 Mixin；Mixin 无法表达且写入/控制流证据闭合时才保留窄范围 ASM。ASM 不能绕过容量证据，也不能用方法名或 `BIPUSH 64` 单独证明语义。
- **Fail Fast**：核心原版/Forge 路径目标缺失、descriptor 不符或注入次数不满足时必须失败并留下可定位信息；只有明确可选的第三方版本差异才允许软失败，且必须记录结构化诊断。

### 1.3 不以注解数量评价质量

**[建议]** 注解匹配数量随搜索口径变化，不能作为质量指标。`@Inject` 适合生命周期点观察、校验、提前拒绝或返回前后调整；`@Redirect` 直接接管调用点，旧代码易写但通常牺牲共存性；`@Shadow` 不是注入器，只是访问目标已有字段/方法的声明桥，无私有状态需求时强加 Shadow 只会增加目标内部实现耦合。评价标准：目标精确性、原逻辑保留、多 Mixin 共存、混淆/refmap 正确性、容量守恒和行为测试。

## 2. 证据层级、官方资料与版本矩阵

### 2.1 证据层级（低层级不能覆盖高层级冲突）

1. 目标版本本地源码、反编译源码、实际字节码和可重复运行日志：确认 descriptor、调用顺序、delegate、写入路径和转换结果。
2. 当前项目真实依赖坐标、构建配置和源码：确认项目实际运行哪个 MixinBooter/MixinExtras。
3. 官方仓库对应版本的 README、源码和发布信息：确认启动、注册和 API 边界。
4. DeepWiki：架构、生命周期和调用链索引；不能单独作为精确 API 或运行行为证明。
5. 类名、注释、grep 计数和历史代码：只能作为调查入口。

**[建议]** 上游 README、DeepWiki 与本地依赖冲突时先记录冲突，再以当前项目实际依赖及对应版本源码为准，不用“上游最新”替换“当前项目已使用”。

### 2.2 五个官方仓库和 DeepWiki 证据台账

| 生态组件 | 官方仓库 | 职责/证据边界 |
|---|---|---|
| MixinBooter | [CleanroomMC/MixinBooter](https://github.com/CleanroomMC/MixinBooter) | 1.12.2 Forge 侧 bootstrap、配置发现和兼容层（[DeepWiki](https://deepwiki.com/CleanroomMC/MixinBooter)：Overview、Core Architecture、Early/Late、Compatibility、Build）；当前依赖锁定 11.13，精确注册事实以该版本产物、源码和运行日志为准。 |
| CleanMix | [CleanroomMC/CleanMix](https://github.com/CleanroomMC/CleanMix) | 当前由 MixinBooter 11.13 使用的 Mixin 核心、service/platform、转换和 AP/refmap 架构；项目显式以 `annotationProcessor` 使用 CleanMix `0.7.1`，其职责可由 [DeepWiki](https://deepwiki.com/CleanroomMC/CleanMix) 研究、精确行为由本地依赖与日志核对。 |
| CleanroomMC MixinExtras fork | [CleanroomMC/MixinExtras](https://github.com/CleanroomMC/MixinExtras) | `read_wiki_structure` 与问答均返回 `Repository not found`——是 DeepWiki 索引失败，不是仓库不存在；官方页面与 [README.MD raw](https://raw.githubusercontent.com/CleanroomMC/MixinExtras/master/README.MD) 为一等入口。11.13 POM/运行日志对应 Cleanroom fork `0.5.5`；provider、shading、manifest/service 和 runtime 仍须以实际 jar/启动证据核对。 |
| LlamaLad7 MixinExtras | [LlamaLad7/MixinExtras](https://github.com/LlamaLad7/MixinExtras) | 解释 Extras 注入器语义和链式设计（[DeepWiki](https://deepwiki.com/LlamaLad7/MixinExtras)：WrapOperation、ModifyExpressionValue、ModifyReceiver、WrapMethod、Local/Share/LocalRef；另核对官方 [WrapWithCondition Wiki](https://github.com/LlamaLad7/MixinExtras/wiki/WrapWithCondition)）；初始化、打包和版本兼容仍须核对当前平台。 |
| SpongePowered Mixin | [SpongePowered/Mixin](https://github.com/SpongePowered/Mixin) | 原生注入器、Shadow、Accessor/Invoker、AP/refmap 基础（[DeepWiki](https://deepwiki.com/SpongePowered/Mixin)：Bootstrap、Transformation、Injection、AP、Accessor）；当前项目运行的是 MixinBooter 11.13 搭配 CleanMix 0.7.1，不能把上游发行版本号直接当作运行时事实。 |

**DeepWiki 冲突记录**：MixinBooter 的 DeepWiki 问答仍以 `IEarlyMixinLoader`/`ILateMixinLoader` two-phase 为核心；该结果是旧索引，不代表当前 11.13 注册路径。官方当前 README 对 11.x 写明 early/late divide 已淡出且接口 deprecated，并给出 Manifest `MixinConfigs`/`MixinConnector`；本项目当前通过 manifest `MixinConnector` 加载 `IMixinConnector`，详见 CDR §8.8。

**搜索失败记录**：本次通用 WebSearch 请求因服务端 HTTP 402 membership verification 失败，未采用其结果；官方 GitHub 页面可直接访问。

**第三方源码/jar 缺口**（依据 CDR「缺失第三方 artifact 台账」节）：以下 1.12.x 目标统一为 `无源码不可判定`，不能用 Mixin 类名、注释或历史方法名补足证据：`appliedenergistics2`、`actuallyadditions`、`brandonscore`、`cyclopscore`、`enderio`、`ic2`、`mantle`、`refinedstorage`、`storagenetwork`、`integrateddynamics`、`limelib`、`immersiveengineering`，以及机器研究用的 `nuclearcraft`、`techreborn`、`reborncore`。Forge wrapper 和 vanilla 反编译源码不属于缺口，但仍须核对具体写入路径。

### 2.3 当前项目和上游版本矩阵

| 范围 | 已核对配置 | 可写成的事实 | 不得写成的结论 |
|---|---|---|---|
| 当前 StackUpUp | Minecraft 1.12.2、Forge 14.23.5.2847 | **[当前项目事实]** `gradle/libs.versions.toml` 锁定 MixinBooter 11.13、CleanMix 0.7.1；`build.gradle.kts` 以 `annotationProcessor` 挂载 CleanMix AP。 | 不得把 10.7/旧 loader 或未完成的生产混淆、第三方容量和高风险 Mixin 证据写成当前通过；当前迁移状态仍为 PARTIAL。 |
| MixinBooter 10.7 上游 tag | [README](https://raw.githubusercontent.com/CleanroomMC/MixinBooter/10.7/README.md)、[build.gradle](https://raw.githubusercontent.com/CleanroomMC/MixinBooter/10.7/build.gradle)、[MixinBooterPlugin.java](https://raw.githubusercontent.com/CleanroomMC/MixinBooter/10.7/src/main/java/zone/rong/mixinbooter/MixinBooterPlugin.java) | **[上游资料]** 官方 10.7 tag README 写 UniMix 0.15.3（CleanroomMC fork，derived from 0.8.7）与 LlamaLad7 MixinExtras 0.5.0；build 使用 `com.github.CleanroomMC:UniMix:9d4b487ed3` 并 `embed 'io.github.llamalad7:mixinextras-common:0.5.0'`；`MixinBooterPlugin` 调用 `MixinExtrasBootstrap.init()`。 | 只是上游 tag 资料，不是当前项目 jar 已核验；不能由此证明当前 provider、shading、manifest 或启动日志。 |
| 当前加载注册 | `StackUpUpMixinConnector.kt`、`StackUpUpCore.kt`、`build-logic/convention/src/main/kotlin/minecraft.gradle.kts` | **[当前项目事实]** manifest 注册 `StackUpUpMixinConnector`；`IMixinConnector.connect()` 保留 early 配置、冲突禁用、校验、mod presence 和 `MixinToggles` 条件。旧 `IEarlyMixinLoader`/`ILateMixinLoader` 仅为历史实现。 | 不得把 dev/SRG connector 装载外推为生产 notch 或完整容量/高风险 Mixin 通过；T14.7 仍需独立复核。 |
| 当前 jar manifest | `build-logic/convention/src/main/kotlin/minecraft.gradle.kts` | **[当前项目事实]** 启用 Mixin 时写入 `MixinConnector`，并按既有条件写入 `FMLCorePlugin`、`FMLCorePluginContainsFMLMod`、`ForceLoadAsMod`、`FMLAT`；dev 服务端日志已证明 connector 发现与装载。 | 生产混淆 jar 的最终 manifest 消费、notch classloader 与第三方 late 目标仍需 artifact/log 核对。 |
| 当前配置 | `mixins.stackupup.early.json:1-7` 及各 late JSON | **[当前项目事实]** early JSON 使用 `refmap`、`minVersion: 0.8`、`compatibilityLevel: JAVA_8`；各第三方配置按模块拆分并共享 refmap，部分 late JSON 缺少 `minVersion`/`compatibilityLevel`，不能概括为字段完全一致。 | 不得把 `required=false` 或 `require=0` 当成“目标一定存在”或“功能已生效”；迁移时逐个核对字段。 |
| MixinBooter 11.0+ | 官方当前 README | **[上游资料]** 基于 CleanMix；early/late divide 不再存在、`IEarlyMixinLoader`/`ILateMixinLoader` deprecated；支持 Manifest `MixinConfigs`/`MixinConnector`。 | 不得将 11.x 的 manifest、connector、Context、ModDiscoverer 或兼容修复无条件移植到 10.7。 |
| MixinBooter 11.12 | MixinBooter 与 CleanroomMC/MixinExtras README | **[上游资料]** 标注 CleanMix 0.6.0/Mixin 0.8.7，11.12 使用 CleanroomMC 自有 MixinExtras fork；`com.cleanroommc:mixinextras-common:0.5.5` 是页面示例，不是依赖事实。 | 不得假设 `io.github.llamalad7` 0.5.0 与 Cleanroom fork 0.5.5 的二进制、bootstrap、service 或打包方式自动兼容；版本/坐标须用实际 jar metadata 复核。 |
| LlamaLad7 MixinExtras | 官方当前 README | **[上游资料]** 需要按平台初始化 `MixinExtrasBootstrap.init()` 并按平台打包；ShadowJar/relocation 只是粗略指南。 | 不得因能编译 annotation 就假设运行时已初始化、service 已注册或独立 jar 不会重复装载；当前 11.13 provider 的 dev/SRG 初始化证据见 CDR §8.6.1，生产环境仍需核对。 |

**10.7 上游嵌入与当前 11.13 运行时分开记账**：官方 10.7 tag 的 LlamaLad7 MixinExtras 0.5.0 与旧 loader 仅是迁移前历史；当前项目使用 MixinBooter 11.13 的 Cleanroom fork 0.5.5，CleanMix 0.7.1 作为编译期 AP，manifest `MixinConnector` 负责项目入口。dev/SRG provider 初始化、refmap 生成和服务端装载已有证据，生产 notch 消费及完整矩阵仍保持 **[UNKNOWN]/PARTIAL**。

### 2.4 MixinBooter 11 迁移准入门与当前验收状态（必选迁移）

迁移不是“改一行坐标”。当前 11.13/0.7.1/`IMixinConnector` 已实施，但准入证据仍需独立复核；以下清单同时规定迁移调查和当前验收边界：

1. **版本和来源**：锁定 MixinBooter 11.13、CleanMix 0.7.1、Cleanroom MixinExtras fork 0.5.5 的来源和最终运行时 jar；列出 compileOnly、annotationProcessor、runtime 和内嵌关系。
2. **注册路径**：检查当前 jar 的 manifest、`MixinConfigs`、`MixinConnector`、`IMixinConnector`、实际 `Mixins.addConfiguration` 调用和 Forge 1.12.2 classloader 顺序；不能只读 README。当前 dev/SRG connector 装载证据见 CDR §8.8。
3. **loader 迁移结果**：确认 `StackUpUpMixinConnector` 保留 early 配置、late mod-gate、冲突禁用逻辑和 `MixinToggles`；旧 `IEarlyMixinLoader`/`ILateMixinLoader` 仅为历史对照，不得当作当前入口。
4. **Extras 运行时**：确认 `@WrapOperation` 等注入器来自 Cleanroom provider、bootstrap 是否由 MixinBooter 自动完成、是否存在重复 provider/service 或 shading/relocation 冲突；当前 dev/SRG provider 证据见 CDR §8.6.1。
5. **AP/refmap**：用 11.13 依赖重新生成并检查 refmap；当前 CleanMix 0.7.1 以编译期 AP 生成 refmap，生产 notch 消费仍未实测；继续核对 Java 8 bytecode、目标 descriptor 和 `remap=false` 边界。
6. **矩阵验证**：比较迁移前 10.7 基线与当前 11.13：vanilla/Forge、每个 late mod 缺失和存在、服务端/客户端、核心目标命中/缺失、同一调用点多 wrapper 共存、保存/读取大堆叠和 handler remainder。最近一次矩阵通过（2026-08-10，`run/logs/autotest-report.txt`，最终状态 PASS），此后代码改动未经矩阵重跑，详见 CDR §8.8；不得写成全通过或发布准入通过。
7. **准入界线**：任何 provider、注册、refmap、classloader、容量、第三方源码或高风险 Mixin 证据不闭合时，状态保持 `UNKNOWN`/`PARTIAL`/`BLOCKED`，不宣称升级或发布准入成功。

## 3. CleanMix、MixinBooter 与 Forge 1.12.2 的边界

### 3.1 责任链

**[上游资料/当前运行证据边界]** CleanMix/DeepWiki 的 Mixin 核心职责链（架构图；当前项目使用 11.13/0.7.1，仍不能替代本地 jar、字节码和日志）：

```text
Forge/FML + LaunchWrapper
  → MixinBooterPlugin / CleanMix bootstrap
  → MixinBootstrap.start / doInit / inject
  → platform manager + container + platform agent
  → IMixinService（与底层 classloader/side/logging 的 SPI）
  → MixinEnvironment + MixinConfig
  → MixinTransformer / processor
  → 目标 ClassNode 预处理、合并、注入、写回字节码
```

- **Bootstrap**：把 Mixin 子系统接入启动器，建立环境、配置和 transformer。
- **Platform/container**：发现含 Mixin 的 jar/classpath root，读取 manifest 配置/connector，按平台生命周期执行 agent。
- **`IMixinService`**：隔离 LaunchWrapper，负责字节码读取、类加载辅助、side 判断和日志。
- **Transformation**：目标类定义进入运行时前读取字节码，应用 Mixin 成员和注入器后交回 classloader；目标类可能已被其他 transformer 改写，不能只按开发环境反编译源码判断。
- **AP/refmap**：AP 编译期解析注解、检查目标并写混淆映射；refmap 运行时把开发名映射到生产目标；两者是部署链一部分，不是可有可无的日志文件。

**[建议]** 研究当前 11.13 时分清三层：MixinBooter 的 Forge 侧桥接、Mixin/CleanMix 的核心转换器、MixinExtras 的扩展注入器。不能把 `MixinBootstrap`（Mixin 核心/launcher 层）写成 MixinBooter 的 Forge 侧 connector，也不能把 `MixinExtras` 写成 Mixin 核心的一部分；10.7 的 TweakClass/旧 loader 只作历史对照。

### 3.2 Java 8 和旧 LaunchWrapper 边界

- **[当前项目事实]** `mixins.stackupup.early.json:2-6` 声明 `compatibilityLevel: JAVA_8`；部分 late JSON 也声明，但并非所有配置字段一致，须逐个文件核对。
- **[建议]** handler、Mixin 类和生成字节码不得依赖 Java 9+ API 或高于目标运行时的 classfile；不把现代 ModLauncher 经验直接套到旧 LaunchWrapper。
- **[当前项目事实]** 静态目标方法现有示例使用 Java `private static` handler（如 `InventoryHelperMixin`、`PacketUtilMixin`），项目硬规则要求保持；不用 Kotlin `companion object + @JvmStatic` 伪装。
- **[建议]** 改变 bootstrap、coremod 排除、classloader 或 Mixin 配置注册的变更，必须同时检查 coremod transformer、Mixin 目标是否仍进入变换链，以及是否重复加载/缺依赖；不能只检查编译结果。

### 3.3 AP、descriptor 和 refmap 的最低要求

1. 重载方法写完整 descriptor，例如 `getInventoryStackLimit()I` 与 `getInventoryStackLimit(I)I` 必须区分。
2. `@At(target=...)` 的 owner、name、descriptor 必须来自目标版本实际字节码；`Math.min(JJ)J`、`ItemStack#getMaxStackSize()I` 等公共目标也要核对调用处和参数顺序。
3. `remap=false` 只在目标已是非混淆的第三方/Forge API 名称或有明确映射边界时使用；不能为消除 AP 报错而全局关闭 remap。
4. `@Pseudo` 只解决可选目标类缺失的加载问题，不验证目标方法、字段、descriptor 或行为；必须与 mod presence gate、版本探针和注入匹配诊断一起使用。
5. AP 通过但 refmap 缺失、错误或未覆盖生产混淆名时，运行时仍可能找不到注入点；构建验证必须检查生成 refmap 内容和最终 jar 内位置。
6. MixinBooter 11 迁移已落地，仍需按当前 11.13/0.7.1 依赖重新核对注解可用性、handler 签名、`order` 支持和 bootstrap；DeepWiki 示例版本不能代替本地依赖源码。

## 4. 注入器决策表

下表是选择顺序，不是鼓励把所有逻辑迁到 Extras。先写出目标字节码语义，再选最小能力的注入器。

| 工具 | 职责 | 适用场景 | 主要风险 | 共存/链式规则 |
|---|---|---|---|---|
| `@Inject` | 在明确 injection point 调用 callback | 入口校验、观察、返回前后副作用、无法用纯结果修改表达的局部逻辑 | `HEAD + cancellable` 可接管全部控制流；`locals` 依赖 LVT；取消顺序与其他 callback 竞争 | 普通 callback 可共存，但取消不是链式协议。核心目标设明确 `require`，不用软失败隐藏失效。 |
| `@Shadow` | 声明并访问目标已有字段/方法 | handler 确实需要目标私有状态或已有方法 | 类型、static/final、可见性、名称、descriptor 或映射不匹配；内部字段变化会断裂 | 不是注入器，不存在“多 Shadow 更好”。只保留必要成员；不用它猜容量。 |
| `@Accessor`/`@Invoker` | 生成字段访问器/方法调用器 | 多个 Mixin 或独立桥接复用私有成员访问 | 目标缺失、签名不闭合、静态性不符、错误 remap | 不是业务注入，也不是容量证明。访问集中在窄接口，避免各自 Shadow 同一内部字段。 |
| `@WrapOperation` | 把一次调用、字段操作、构造或支持的表达式包装成 `Operation` | 需要查看参数、决定是否调用原操作、在保留原语义下改变输入/输出 | handler 参数顺序/owner 不符；不调用或多次调用原 operation 改变业务；包装层级需测试 | MixinExtras 首选链式工具。保留 `Operation` 并按契约调用原操作以支持其他 Mixin；“能链式”不等于可随意吞掉原调用。 |
| `@ModifyExpressionValue` | 修改表达式已经产生的值 | 只调整某个调用/字段/常量/`instanceof`/构造表达式的结果，不需跳过原表达式 | 把表达式结果误当真实写入能力；目标表达式匹配过宽；无法阻止原调用副作用 | MixinExtras 设计上适合叠加。保留 `original` 结果作为输入，说明修改后的业务不变量。 |
| `@ModifyReturnValue` | 修改目标方法返回值 | 只需后处理目标方法返回值，不需重建方法体 | 返回值可能只是广告而非真实写入结果；多个返回修改的顺序需确认 | 适合小范围结果调整；核对实际 Extras 版本、order 和其他返回修改器，不当作容量闭合证明。 |
| `@ModifyArg` | 修改某次调用的一个参数 | 只改一个调用参数，且不需接管调用 | ordinal/调用点错误；修改对象可能影响原调用契约；多个 modifier 顺序不自动代表业务顺序 | 原生 Mixin 工具，通常比 Redirect 小；不具备 `WrapOperation` 的原操作控制协议，需测试多个 Mixin 共存。 |
| `@ModifyArgs` | 通过 `Args` 修改一次调用的多个参数 | 同一调用确实需要联合修改多个参数 | 参数索引、类型和装箱错误；改动面过大 | 只有多参数联合语义成立时使用；单参数优先 `@ModifyArg`。 |
| `@ModifyVariable` | 修改目标方法中的局部变量 | 局部变量是唯一稳定语义载体、且没有更窄的表达式/参数入口 | LVT 会受其他 transformer/Mixin 改写；index/ordinal/name 和 slice 易漂移 | 尽量后置选择；显式定位并做字节码验证，不把开发环境的 locals 视为生产保证。 |
| `@ModifyConstant` | 修改匹配到的常量 | 常量在目标版本中有明确唯一语义，且能用 slice/ordinal 限定 | 相同常量多处出现；把字面量 `64` 当成容量证明；`require=0` 静默失效 | 只在常量语义和写入路径均已证明时使用；不能默认替换所有 `64`。 |
| `@ModifyReceiver` | 修改非静态调用/字段操作的接收者 | 必须把操作转发给另一个接收者，且替换本身是目标语义 | receiver 类型/生命周期/dispatch 改变；可能绕过真实 delegate 限制 | MixinExtras 设计上支持叠加；仍须证明新 receiver 的真实行为和容量，不可借 wrapper 类型推断能力。 |
| `@WrapMethod` | 包装整个目标方法 | 必须围绕完整方法建立前后语义，而非一个局部调用 | 方法级控制流和异常/返回/副作用边界扩大；接近完整接管 | 只有方法级包装确有必要时使用；优先局部 `@Inject`、返回修改或 `@WrapOperation`。 |
| `@WrapWithCondition` | 保留原参数/字段操作形态，按条件决定是否执行一次操作 | 只需条件性执行调用或字段写入，且“不执行”是明确业务语义 | 条件副作用、返回值和原调用链必须明确；条件函数不能误把容量拒绝当成 remainder 补偿 | MixinExtras 可链式条件工具；先确认当前 provider 版本支持，再验证多个条件 wrapper 组合和原操作最多执行一次。 |
| `@Redirect` | 直接替换一次调用、字段访问或构造 | 旧实现必须彻底替换操作且无可用原操作语义；或旧代码暂留待迁移 | 通常不可链式，后加载 Redirect 可能覆盖前一个；原调用不可见；调用点错误悄悄改变控制流 | 新代码默认禁用。只有 wrapper/表达式/返回值/条件包装无法表达且有行为证据时保留，并记录不可迁移原因。 |
| `@Overwrite` | 完整替换目标方法 | 仅局部注入无法表达且必须控制整个方法时 | 与目标版本、其他 Mixin、修复和异常语义强耦合；最容易丢失未来逻辑 | 默认禁止。必须有完整方法源码/字节码对照、理由/作者、版本边界、变换后行为和冲突审计。 |

**选择口诀**：在某个点做事先看 `@Inject`；包住一次原操作用 `@WrapOperation`；改已算出的表达式用 `@ModifyExpressionValue`；改方法返回用 `@ModifyReturnValue`；换输入参数用 `@ModifyArg`、换 receiver 用 `@ModifyReceiver`、条件执行原操作核对版本后用 `@WrapWithCondition`；读写私有状态必要时 `@Shadow`、可复用访问用 `@Accessor/@Invoker`；让原调用消失先证明 wrapper/表达式无法表达再考虑 `@Redirect`（默认不新写）；重写完整方法以 `@Overwrite` 为最后手段。

## 5. Shadow 的正确使用

### 5.1 本质

**[上游资料]** `@Shadow` 是 Mixin 预处理阶段识别的目标成员声明桥，不是控制流注入器：它告诉 Mixin“目标类已有该字段/方法，请允许按此签名访问”。它不会替目标方法扩容、不验证 delegate 的写入容量，也不会让未知第三方类变成可信实现。

**[建议]** 需要状态时使用 Shadow 正常且必要；为降低“Shadow 太少”观感而新增 Shadow 是错误方向。可复用访问优先集中为 accessor/invoker 接口，但 accessor 也必须有目标版本证据。

### 5.2 准入检查（每个 Shadow 在交接记录中回答）

1. 目标类在当前版本是否实际声明该成员，不是从父类、接口或同名类猜测。
2. 字段类型、数组/泛型擦除后的 JVM 类型、方法参数和返回值是否完全一致。
3. static/instance 是否一致；目标 `final` 字段是否用 `@Final` 正确反映；无充分理由不得用可变注解绕过 final。
4. 方法是否写完整 descriptor；重载不能靠短方法名区分。
5. `remap` 是否与目标名称来源匹配；第三方非混淆名称才考虑 `remap=false`，不能把 AP 错误一律改 `remap=false`。
6. 访问是否真的服务于该 handler 语义；若只为读取容量，必须追到真实 setter/insert，不把 Shadow 值当写入证明。
7. 多个 Mixin 是否可以共享同一 accessor，而不是各自复制一组内部 Shadow。

### 5.3 当前项目 Shadow 风险样例

- `early/NetHandlerPlayServerMixin.java:21-23` Shadow `player`、`itemDropThreshold` 用于创造模式包处理，字段访问合理；但 `:25-56` 的 `HEAD + cancellable` 已接管整个方法，风险在控制流、线程检查、槽位更新、丢弃和取消语义是否完整，不在 Shadow 数量。
- `early/ItemStackNbtMixin.java:21-37` Shadow 多个 `ItemStack` 字段，`:52-71` 在 `writeToNBT` HEAD 取消并重建序列化；持久化高风险，必须逐项对照目标版本的 id/count/damage/tag/capability 和返回对象语义。
- `early/SlotItemHandlerMixin.java:16-17` Shadow `getSlotStackLimit`，`:24-39` 修改返回值；只能提供 handler 查询，不能证明未知 handler 的真实 insert/setter 能写入动态上限；`original == 64` 分支和其他原值分支的已知限制见 `docs/agent/2026-04-18-hard-rules.md:65-79`。
- `late/BrandonsCoreInventoryLimitMixin.java:20-21` 字段 Shadow 要保留必须有对应版本第三方源码或字节码；缺 jar 统一写 `无源码不可判定`，不能用注释“protected int stackLimit”替代外部证据。

## 6. Redirect 到 MixinExtras 的迁移规则

### 6.1 迁移流程（不得只做注解字符串替换）

1. **锁定调用点**：记录目标方法完整 descriptor、调用 owner/name/descriptor、是否静态、是否构造/字段操作、ordinal/slice 和参数顺序。
2. **写出旧语义**：旧 handler 是替换返回值、改参数、改 receiver、跳过原调用还是依赖目标对象状态；确认是否真的应该调用原操作。
3. **选择最小替代**：需保留或条件调用原操作 → `@WrapOperation`（保留 `Operation<T>`，按明确契约调用）；只改操作产出值 → `@ModifyExpressionValue`；只改整个目标方法返回 → `@ModifyReturnValue`；只改 receiver → `@ModifyReceiver`；只读写稳定局部变量 → `@Local`，修改用相应 `LocalRef`，多 injection point 共享状态再考虑 `@Share`。
4. **保留业务边界**：包装器不得把 `simulate` 当真实写入，不得在原调用后补偿 remainder；容量处理仍须追到 delegate 和 setter。
5. **证明共存**：至少验证两个 wrapper 同时存在时的调用顺序、每个 wrapper 是否调用原操作、原操作调用次数和最终返回/落库状态。
6. **不具备等价语义时保留并登记**：旧 Redirect 的“完全阻止原操作”无法由合适的 wrapper/表达式安全表达时，暂时保留 Redirect；记录原因、目标版本、不可链式影响和后续测试，不得伪称已迁移。

### 6.2 当前代码的审查候选

- `EntityItemMergeMixin.java:11-23` 已按迁移完成 `@ModifyExpressionValue`（T14.2 的 M1/M2 迁移，2026-08-08）：修改 `ItemStack#getMaxStackSize()I` 表达式结果并保留原值参与计算，不再使用 `@Redirect`。
- `InventoryPlayerAddResourceMixin.java:12-44` 三个调用点均已迁移为 `@ModifyExpressionValue`（两个 `getInventoryStackLimit` 调用点经 `resolveInventoryClampLimit`，一个 `getMaxStackSize` 调用点取入站栈上限）；每个点仍是独立语义，不能合并成一个泛化 hook。`resolveInventoryClampLimit` 仍有两个业务调用方，删除或改签名前必须逐一处理。
- `ItemGridHandlerMixin.java:13-24` 与 portable 版本已用 `@WrapOperation`，方向正确；但 handler 接收 `Operation<Long>` 却不调用 `original`，语义仍接近“硬替换”。必须证明是有意替换 `Math.min(JJ)J`，并验证其他 wrapper 叠加时不丢失原逻辑。
- `RenderItemMixin.java:13-29` 接收 `Operation` 但当前不调用；需验证渲染原操作是否有意被替换。
- `AppEngAdaptorItemHandlerMixin.java`（T12 方案 A，2026-08-08，已归档）已不含任何注入：类保留为 AE2 late config 入口保险丝，`AdaptorItemHandler#addItems` 对 `IItemHandler#insertItem` 的调用原样执行（热路径零分支、零分配）。不用 `@WrapOperation` + `operation.call(...)` 的原因：`Operation.call(Object...)` 是 varargs，javac 每次调用生成 Object[] 与 Integer/Boolean 装箱（字节码证据），违背零分配约束。"不吞"由原版/Forge remainder 契约结构性保证，边界探针（`DevAutomationServerDriver#probeBoundaryLimit`）负责运行期验证；不再委托 `Ae2ItemHandlerInsertLimiter`，也不做任何审计。该方案不再作为 T13 依赖或解锁条件。
- `ContainerMixin.java:14-26` 是较好包装形态：读取 `original.call(slot)` 后再做 item-aware 限制；仍不能单独证明所有库存真实写入容量已同步。
- `RefinedStorageMixinSourceTest.kt:11-19` 只做源码结构护栏（含 `WrapOperation` 且不含 `@Redirect`），不能证明运行时 injection 命中、原操作调用次数、调用链顺序或容量守恒，必须有行为/字节码验证补足。

## 7. 目标选择、失败门与可选兼容

### 7.1 目标必须闭合

每个 injection 在代码和交接记录中至少写明：target 类全名及 early/late/optional 属性；目标方法完整 JVM descriptor（构造器用 `<init>`）；`@At` 类型、owner/name/descriptor、ordinal；必要时 `slice` 起止点和 `shift`（`At.Shift.BY` 只能在字节码位置稳定且有证据时使用）；handler 参数顺序、receiver、原始表达式/返回值类型及是否静态；`remap`、`@Pseudo`、Mixin config 和 refmap 关系；预期匹配数和实际匹配数。

避免：只写短方法名、依赖第一个相同调用、用任意常量作位置标记、用 `HEAD` 代替真正语义位置、用大 `shift` 跨越不稳定代码。

### 7.2 核心目标和可选目标的不同门槛

**核心原版/Forge 路径**：注入目标缺失、descriptor 不符或匹配数非预期时 fail fast；显式设置最低成功注入要求；不用 `required=false` 或 `require=0` 把核心错误改成启动成功。

**可选第三方路径**：

- 先由 mod presence、配置开关和目标版本探针决定是否加载；
- 配置级 `required=false`、注入器级 `require=0` 和 `@Pseudo` 必须分别登记：前者只改变配置失败的终止性，第二个只改变该 injector 的最低命中数，后者只影响可选目标类加载；三者不能互换，也不能单独证明“该版本可能不存在”或“功能不重要”；
- 缺失时产出结构化记录：mod ID、jar 版本、配置名、目标类、方法 descriptor、注入点、匹配数、跳过原因；
- 目标存在但 descriptor 或行为不符时记为失败/不兼容，不得伪装成缺失；
- 测试覆盖“mod 不存在”“目标类存在但方法不存在”“目标匹配成功”“目标版本变更”四类结果。

#### 7.2.1 四个失败门的层级（禁止互换）

`required`、`require`、`expect` 和 `injectors.defaultRequire` 位于不同层级，不能用一个字段代替另一个：

- **配置级 `required`**：Mixin JSON 的配置级门，控制配置初始化、版本/特性检查或 mixin 处理失败时是否视为终止性错误；不等于“每个 injector 至少命中一次”。官方 UniMix commit `9d4b487ed3` 的 [`MixinConfig.java`](https://raw.githubusercontent.com/CleanroomMC/UniMix/9d4b487ed3/src/main/java/org/spongepowered/asm/mixin/transformer/MixinConfig.java) 将 `required` 解析为 boxed value：根配置省略时 `onLoad` 得 false，子配置省略时还可能按 parent 继承；属版本敏感的上游源码语义。
- **注入器级 `require`**：injector 注解上的最低成功回调数。官方 UniMix [`Inject.java`](https://raw.githubusercontent.com/CleanroomMC/UniMix/9d4b487ed3/src/main/java/org/spongepowered/asm/mixin/injection/Inject.java) 省略值为 `-1`；[`InjectionInfo.java`](https://raw.githubusercontent.com/CleanroomMC/UniMix/9d4b487ed3/src/main/java/org/spongepowered/asm/mixin/injection/struct/InjectionInfo.java) 仅在显式值非负时直接采用，否则 default group 回退 `getDefaultRequiredInjections()`。`require=0` 是该 injector 的零最低命中门，不是配置级 `required=false` 的同义词。
- **`expect`**：注解级预期回调数。`Inject.java` 默认 `1`，`InjectionInfo.java` 只在 `mixin.debug.countInjections`/`DEBUG_INJECTORS` 开启时用它检查；是 debug 诊断门，不是生产环境最低成功要求。
- **`injectors.defaultRequire`**：Mixin JSON `injectors` 对象中的默认 `require`，不是配置级 `required`。`MixinConfig.java` 默认 `0`；只为省略/`-1` 的 injector `require` 提供 default-group 回退，parent merge 仍是版本敏感行为。

以上默认值和省略语义是官方 UniMix `9d4b487ed3`（MixinBooter 10.7 上游 build 引用的 commit）历史证据，不自动代表当前 11.13/0.7.1 CleanMix。当前 provider 已有 dev/SRG 装载证据，但每个配置的 `required`、`injectors.defaultRequire` 及 injector 的 `require`/`expect` 仍必须由 T14.5 逐文件登记，不能从模块名、`required=false` 或默认值推断；未收敛处保持 **[UNKNOWN]**。

**当前基线静态结果（未完成 T14.5）**：`mixins.stackupup.early.json` 未声明 `required`；`late.integrateddynamics.json`、`enderio.json`、`cyclopscore.json`、`brandonscore.json`、`immersiveengineering.json`、`limelib.json` 声明 `required=false`；静态检索未发现 `injectors.defaultRequire`。这只是现状记录，不是逐文件目标/loader/日志/refmap/AP 登记，也不是 fail-fast 或 optional skip 已通过的证明。

**当前样例**：`ItemGridHandlerMixin.java:13-17` 的 `require=0` 可作第三方版本差异的可选门，但当前未展示结构化缺失报告；重构时必须补齐诊断或在更上层明确记录，不能静默失效。

### 7.3 不默认使用的写法

- `@Overwrite`：除非完整方法替换是唯一可表达方案，并有源码/字节码、版本、异常、返回和共存审计。
- `@Inject(at = @At("HEAD"), cancellable = true)`：除非完整控制流接管确有必要；优先局部 injection 或包装表达式。
- `@ModifyConstant(constant = 64)`：除非常量在该位置有唯一业务语义，并由 slice/ordinal 和写入测试证明；字面量 `64` 不是容量语义。
- `@Redirect`：新代码禁用；旧代码迁移前按第 6 节建立等价行为证据。
- `@Shadow`：不为读取“可能的容量”而新增；不以 Shadow 绕过未知 wrapper/handler 的写入证据。
- `require=0`：不用于掩盖 AP、descriptor 或核心目标错误。

## 8. StackUpUp 当前源码的高风险审查清单

以下只是审查入口，不是“已修复”的清单：

| 文件与位置 | 当前事实 | 重构审查动作 |
|---|---|---|
| `early/EntityItemMergeMixin.java:11-23` | 已迁移为 `@ModifyExpressionValue`，修改合并流程中 `ItemStack#getMaxStackSize()I` 的表达式结果（保留原值参与 `Math.max` 计算），不再是 `@Redirect` | 记录调用点和合并双方真实上限；验证原值参与、合并容量和 remainder/实体数量结果。 |
| `early/InventoryPlayerAddResourceMixin.java:12-44` | 三个调用点均为 `@ModifyExpressionValue`，分布在合并和资源加入路径 | 每个调用点单独建立语义；检查 `resolveInventoryClampLimit` 两个调用方；禁止用一次泛化替换掩盖不同的 source/target/limit 关系。 |
| `early/NetHandlerPlayServerMixin.java:21-56` | Shadow 两个字段，HEAD cancellable 重建创造模式包处理 | 对照目标版本完整方法；覆盖线程切换、创造模式、非法槽位、空栈、BlockEntityTag 清洗、slot 更新、丢弃阈值和取消后逻辑；优先减少完整控制流接管。 |
| `early/ItemStackNbtMixin.java:21-71` | 多 Shadow，`writeToNBT` HEAD cancellable 重建 NBT | 逐字段对照目标版本；覆盖空 item、tag alias、ForgeCaps、读写往返、未知字段保留和其他 Mixin 共存；不能只验证 Count 变大。 |
| `early/SlotItemHandlerMixin.java:26-38` | 只保留 `getItemStackLimit()` 的 `@ModifyReturnValue`（`resolveItemHandlerSlotLimit` 按 `min(slotLimit, itemLimit)` 收敛）+ `@Shadow getSlotStackLimit()`；T3 已移除 `getSlotStackLimit()` 的独立动态上限注入与 `original == 64` 分支 | 先确认 handler 的真实 insert/setter；未知 handler 不扩容；不能把已移除的 `original == 64` 分支当作现状描述。 |
| `early/ForgeItemHandlerLimitMixin.java:33-46` | 目标只有 `ItemStackHandler` 与 `EntityEquipmentInvWrapper` 两个自洽类（`@Mixin` 列表 :33-39、handler :43-46）；四个转发 wrapper 已按 T3 移出 | 按目标拆分查询—写入链；wrapper 只作转发证据不能独立抬高；装备 armor/hand、`insertItem`、`setStackInSlot` 和 vanilla setter 分开验证。 |
| `early/SlotLimitMixin.java:12-27` | 修改 slot item limit；正的 inventory limit 才 clamp | 覆盖非正 inventory limit；广告、实际 setter 和 handler insert 同源；不能用 GUI/slot 返回值掩盖服务端容量。 |
| `early/InventoryHelperMixin.java:17-25` | `HEAD + cancellable` 完整替换原版掉落拆分 | 对照原版实体生成、空栈、随机拆分和总数守恒；确认是业务重写而非可局部包装的调用点。 |
| `early/PacketBufferMixin.java:16-59` | 读写 `ItemStack` 的两个 `HEAD + cancellable` 协议替换 | 对照 1.12.2 原版协议、空栈、id、count、damage、share tag、异常和读写往返；验证客户端/服务端两端一致。 |
| `early/PacketUtilMixin.java:16-39` | 客户端到服务端 ItemStack 写入的 `HEAD + cancellable` 替换 | 与 `PacketBufferMixin` 一起做协议矩阵和异常验证；不能把编解码替换当成普通返回值修改。 |
| `early/VanillaInventoryWriteMixin.java`（**已随 T4a 删除**） | **该文件在当前工作副本中不存在**（`rg -n "VanillaInventoryWriteMixin" src/` 无命中）；旧描述「多种 inventory 的 setter 前后都使用 `require=0`」仅为 T4a 前历史形态 | 仅作历史对照保留；不得据此判断当前写入面或 `require` 现状。若将来重新引入 setter 上下文，须逐目标确认 setter 是否声明、写入是否真正发生、异常时 begin/end 是否成对。 |
| `early/NetHandlerPlayClientMixin.java:17-50` | 先调用原 setter/setAll，再恢复客户端堆叠数量 | 定义为客户端同步路径而非 remainder 补偿；验证服务端权威状态、容器更新顺序、空槽和多槽列表，避免与禁止事后补偿混淆。 |
| `late/AppEngAdaptorItemHandlerMixin.java`、`core/Ae2ItemHandlerInsertLimiter.java` | T12 方案 A（2026-08-08，已归档）：mixin 无任何注入（保留为 config 入口保险丝），AE2 `insertItem` 原样执行，热路径零分支零分配（不用 `operation.call`：varargs 产生 Object[] 与装箱）；`Ae2ItemHandlerInsertLimiter` 保留为纯工具类（仅测试/探针直接使用），分片循环与白名单不在热路径 | “不吞”由原版/Forge remainder 契约结构性保证；边界探针 `probeBoundaryLimit` 验证 `insertItem(N)` 全存 / 追加 `insertItem(1)` 被拒；第三方内部写入缺证据时仍标 `UNKNOWN`，不得写成已扩容证明；该方案不作为 T13 依赖或解锁条件。 |
| `late/ItemGridHandlerMixin.java:13-24` 与 portable 版本 | `@WrapOperation` 带 `require=0`，当前不调用 `original` | 加载缺失诊断；确定是否有意替换 `Math.min`；验证多 wrapper 叠加、原 operation 调用契约和抽取真实结果。 |
| `early/RenderItemMixin.java:13-29` | 渲染文本调用用 `@WrapOperation` 但当前不调用 `original` | 确认是否有意完全替换字体绘制；验证客户端渲染、颜色/坐标/空文本和其他渲染 Mixin 共存，不能仅凭注解名称认为它保留原调用。 |
| `core/DynamicCompatMethodProbe.java:32-58`、`core/CompatibilityLimitPatch.java:56-85` | 动态 ASM 只按方法名识别，命中方法内替换所有 `BIPUSH 64`；probe 不按 descriptor、常量位置或语义确认 | 不能把方法名/常量命中写成目标闭合；逐个补 descriptor 和语义位置证据，确认 Mixin 已接管目标进入 `FixedCompatTargets`，并检查动态 transformer 不二次命中。 |
| `src/test/kotlin/.../RefinedStorageMixinSourceTest.kt:11-19` | 只检查源码含 WrapOperation 且不含 Redirect | 保留为结构护栏，但增加真实行为/变换后字节码/注入匹配验证；不能把测试名当运行时证明。 |
| `StackUpUpCore.kt`、`bootstrap/StackUpUpMixinConnector.kt` | 当前 core 入口保留 IFMLLoadingPlugin；`IMixinConnector` 条件注册 early JSON，并保留冲突、校验、mod presence 和 toggle 逻辑 | 继续核对冲突 coremod、排除包、manifest 和 LaunchWrapper classloader；dev/SRG 已装载，生产 notch 与完整矩阵仍未闭合。 |
| `bootstrap/StackUpUpLateMixinLoader.kt`（历史已删除） | 10.7 旧 late loader 按 Context、mod presence、toggle 排队配置 | 仅作迁移对照；当前逻辑集中在 `StackUpUpMixinConnector`，不得恢复 deprecated loader 或把历史路径写成现状。 |

### 8.1 当前实现的额外已知限制

- **[当前项目事实] AE2 热路径已零逻辑化（方案 A，2026-08-08）**：`late/AppEngAdaptorItemHandlerMixin.java` 不再含任何注入（仅保留为 AE2 late config 入口保险丝），`AdaptorItemHandler#addItems` 对 `IItemHandler#insertItem` 的调用原样执行，热路径零分支零分配；不用 `@WrapOperation` + `operation.call` 的原因见 §6.2（varargs 生成 Object[] 与装箱分配）。"不吞"由原版/Forge remainder 契约结构性保证：超量时 `insertItem` 返回 remainder，由 AE2 侧自行回收，我方不干预。`Ae2ItemHandlerInsertLimiter.java` 保留为纯工具类（仅 `Ae2ItemHandlerInsertLimiterTest`/`WrapperCapacityDiagnosticTest` 等测试护栏使用），分片循环不在热路径；运行期验证由 `DevAutomationServerDriver#probeBoundaryLimit`（insertItem(N) 全存 / 追加 insertItem(1) 被拒）承担。这仍不是“未知 handler 已扩容”的证明——未知 handler 写入容量依旧不可判定，只是我方不再干预投喂语义。
- **[当前项目事实] 动态 ASM 未闭合**：`DynamicCompatMethodProbe.java:32-58` 只按方法名识别，`CompatibilityLimitPatch.java:56-85` 替换匹配方法内所有 `BIPUSH 64`；`DynamicCompatTransformer.java:21-49` 应用补丁，`FixedCompatTargets.java:29-64` 是固定跳过表。当前没有按 descriptor 和常量语义位置建立完整单一事实源；重构时先核对 Mixin 目标、固定表、动态 transformer 的避让关系，不能把 ASM 命中写成容量或方法语义证明。
- **[当前项目事实] 其他完整控制流/协议替换**：`InventoryHelperMixin.java:17-25`、`PacketBufferMixin.java:16-59`、`PacketUtilMixin.java:16-39` 都在 HEAD 取消原方法，分别涉及实体掉落拆分和 ItemStack 网络协议；必须按原版源码、异常、两端读写和数量守恒审查，不能套用普通返回值修改的低风险判断。
- **[当前项目事实] coremod 异常分类缺口**：`StackUpUpCore.kt:35-49` 的冲突检测捕获 `Throwable` 后返回空列表，异常可能被误判为“没有冲突”。与 Fail Fast 冲突，迁移或重构时必须保留可定位诊断并单独测试，当前不能标为已闭合。
- **[当前项目事实/UNKNOWN] 核心注入显式失败门尚未逐项收敛**：early JSON `:1-7` 未声明 `required: true`；`EntityItemMergeMixin.java:11-17`、`ContainerMixin.java:14-20`、`ItemStackNbtMixin.java:39-52`、`PacketBufferMixin.java:16-20,36-39` 未显式写最低 `require`。这些是代表性样例，不是完整枚举；完整清单须逐项扫描 early JSON 及全部 injection。当前框架默认行为及最终匹配结果未由本文件运行验证，只能记录为“规则要求与现状之间的审查项”，不能写成核心 fail-fast 已通过。

### 8.2 容量目标的逐项审计表

每个新容量兼容目标先建立一行审计记录，至少包含：

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

`EntityEquipmentInvWrapper` 尤其要分开：Forge `insertItem` 会计算上限并在超量时返回 remainder；`setStackInSlot`/vanilla setter 是另一条写入路径。不能写成“wrapper 无余量必吞”，也不能因为 setter 看起来直接就替代 Forge 的 insert 契约。

## 9. 可执行验证矩阵

以下是实现任务完成前的最低验证面；本次文档同步 lane 未运行生命周期命令，已运行与未运行项目见交接记录。

### 9.1 结构与字节码

- Mixin AP 成功，refmap 生成且包含每个需混淆的目标；检查最终 jar 中配置和 refmap 路径。
- 目标方法 descriptor、静态性、handler 参数和 `@At(target)` 与目标版本字节码一致。
- 核心 injection 匹配数符合预期；可选目标的 `require=0` 只在明确 optional 情况使用并留日志/报告。
- 检查转换后类：旧 Redirect 是否真被移除、WrapOperation 是否保留正确 `Operation` 链、原调用次数是否正确、无重复 ASM/Mixin 改写。
- 动态 ASM 额外检查 `DynamicCompatMethodProbe` 的 descriptor 盲区、`CompatibilityLimitPatch` 的 `BIPUSH 64` 语义位置、`DynamicCompatTransformer` 的空输入/重复命中以及 `FixedCompatTargets` 与显式 Mixin 目标逐项对齐。
- 核心 injection 分开检查注入器级 `require`、配置级 `required`、`injectors.defaultRequire` 与实际匹配数；`expect` 只按 debug-only 语义单独登记；仍依赖框架默认值必须标为未收敛。
- 按项目门槛覆盖 `CoremodHierarchyBytecodeSafetyTest`、`EarlyMixinBytecodeSafetyTest` 和 `MixinBooterIntegrationTest`；这些是静态/集成门槛，不能替代真实容量行为。

### 9.2 Mixin 共存

至少安排两个独立 wrapper/修改器同时作用于同一调用点，观察：每个 handler 是否命中；`Operation` 是否按约定恰好调用一次；返回值/参数/receiver 顺序是否符合设计；某个可选目标缺失时另一核心目标是否仍按预期失败或通过；不同 priority/order 只用于解决已证明的冲突，不作为没有行为证据的默认修复。

### 9.3 StackUpUp 行为

- vanilla/Forge 基础路径：合并、slot 写入、玩家资源加入、实体掉落、创造模式包、客户端 slot/window 同步。
- NBT：≤64 与 >64 读写往返，空 tag、ForgeCaps、未知字段、重载后再保存。
- handler：空槽、相同堆叠、不同堆叠、满槽、`offered` 小于/等于/大于限制、`simulate=true/false`、非零/全量 remainder。
- wrapper：`ItemStackHandler`、转发 `InvWrapper`/`SidedInvWrapper`/`CombinedInvWrapper`/`RangedWrapper`、`EntityEquipmentInvWrapper` 的 armor 与手部；分别检查 delegate 和 setter 路径。
- 可选第三方：mod 缺失、正确版本、目标类缺失、方法 descriptor 变更、目标存在但注入点消失；结果分别是 skip、pass 或 fail，不能全部记为 skip。
- 每个真实写入调用输出机器可读审计：目标类、slot、simulate、offered、落库量、remainder、守恒结果；报告只能观察，不可回填或重试。

### 9.4 运行任务边界

涉及 coremod、Mixin、MixinExtras、自动化参数或 connector 时，项目规范要求至少覆盖相应 `runServerAutoTest`；矩阵任务使用 `runServerAutoTestMatrix` 或明确的 `run*AutoTest` 入口。运行前确认本地 jar、FML 扫描目录、server/client 侧和自动化开关，避免把缺依赖误报成 Mixin 失败。当前 connector 的 dev/SRG 服务端证据见 CDR §8.8：最近一次矩阵通过（2026-08-10），此后代码改动未经矩阵重跑。

未实际运行的检查必须在交接中写“未执行”，不得把静态搜索、编译通过或文档推理写成运行通过。

## 10. 代理工作流与交接模板

### 10.1 工作流

1. **调查**：读根级 `AGENTS.md` 和目标目录规范；检查目标文件/源码现有改动；列出精确文件租约。搜索符号后用调用层级确认入口，不用类名猜调用链。
2. **版本核对**：读当前依赖坐标和 Mixin 配置；查询五个官方仓库与对应 DeepWiki 页面；记录版本冲突、索引失败和缺失 jar。
3. **目标闭合**：拿到目标版本源码或字节码，确认完整 descriptor、调用点、owner、参数、receiver、局部变量和写入 delegate。
4. **注入决策**：按第 4 节选最窄注入器；新逻辑默认不用 Redirect/Overwrite；需要私有状态才 Shadow；容量逻辑先闭合真实写入链。
5. **最小实现**：只写租约文件；不把可选目标变核心目标，不通过 `require=0` 隐藏错误，不引入事后 remainder 补偿。
6. **结构验证**：检查 AP、refmap、变换后字节码、匹配计数和日志；确认无重复 ASM 命中或与固定 Mixin 冲突。
7. **行为验证**：运行核心、可选、共存、客户端/服务端、simulate/remainder 和容量矩阵；记录实际命令和结果。
8. **独立复核**：作者不能自审。由独立代理逐项复核目标 descriptor、注入选择、容量守恒、失败分类和测试证据。
9. **交接**：只报告真实修改和真实验证；缺失源码、未运行命令、版本未锁定或冲突未解决都写为 `UNKNOWN`/`无源码不可判定`。

### 10.2 交接记录（不能留空项）

- **目标**：一句话写清验收条件（例如“在 Forge 1.12.2 下仅包装已确认的 `insertItem` 调用，并证明广告容量不超过真实写入容量”）。
- **允许修改**：精确的源码、配置和测试路径；未列出的路径不可写。
- **禁止事项**：禁止的 Redirect/Overwrite、未知 handler 扩容、写入后补偿、额外 runtime jar、生命周期命令或外部操作。
- **基线证据**：本地源码/字节码路径、当前依赖坐标、官方仓库 URL、DeepWiki 页面、缺失 jar 和冲突记录。
- **安全不变量**：容量守恒、simulate 分离、原操作调用契约、Fail Fast 和 Mixin 优先/ASM 兜底边界。
- **注入选择**：说明为何用 `@Inject`、`@WrapOperation`、`@ModifyExpressionValue`、`@ModifyReturnValue` 或其他工具，以及为何没用更高风险工具。
- **验证方式**：逐条列出实际执行的结构检查、测试、运行任务和结果；未执行项标明未执行及原因。
- **代理分工与租约**：作者、独立复核者、每个文件的唯一写租约和交接边界。
- **结论状态**：只能使用 `PASS`、`FAIL`、`BLOCKED`、`UNKNOWN` 或 `PARTIAL`；无独立复核、运行证据或关键第三方源码时不得写 `PASS`。

### 10.3 UNKNOWN 的处理规则

`UNKNOWN` 不是“可以先按安全处理”的同义词，也不是失败证据。代理必须同时写：

1. 缺什么：具体 jar、版本、源码、字节码、运行日志或目标 descriptor；
2. 为什么缺失会影响结论：例如无法确认 delegate setter 是否截断、`MixinExtrasBootstrap` provider、目标方法重载；
3. 当前安全动作：不扩容、不加载可选配置、保留原 operation、让核心目标 fail fast；
4. 需要谁补齐以及如何验证：取得对应 artifact、做 AP/refmap 检查、导出变换类或运行真实 `simulate=false` 矩阵。

不要把“类名看起来像 wrapper”“注释写着 stackLimit”“DeepWiki 有同名页面”改写成写入路径证据。第三方 jar 缺失时使用项目统一措辞：**无源码不可判定**。

## 11. 最终原则

- 选择注入器是语义决策，不是注解数量竞赛。
- `@Shadow` 少不是缺陷；只有确实需要目标私有状态时才用 Shadow，并严格匹配类型、static/final、descriptor 和 refmap。
- 新代码优先 `@WrapOperation`、`@ModifyExpressionValue`、`@ModifyReturnValue`、`@ModifyReceiver` 等可表达局部语义的工具；操作包装必须保留并正确使用原 operation。
- `@Inject` 局部 callback 优于无理由的完整方法接管；`HEAD + cancellable`、`@Redirect` 和 `@Overwrite` 都要承担明确的共存与行为证明责任。
- 当前项目事实是 MixinBooter 11.13 + CleanMix 0.7.1 + manifest `MixinConnector`/`IMixinConnector`；10.7 与 `IEarlyMixinLoader`/`ILateMixinLoader` 仅作历史对照。dev/SRG provider、refmap 和 connector 装载已有证据，但生产 notch、第三方容量、高风险 Mixin 和完整矩阵未闭合，不得把迁移实施写成发布准入通过。
- 容量任务永远同时审查广告和真实写入：`storedDelta + remainderCount == offered`，区分 simulate，禁止写入后补偿；未知 handler 不动态扩容。
- Mixin 能表达时优先 Mixin，确实不能表达且证据闭合时才用窄范围 ASM；任何未验证项如实保持 `UNKNOWN`。
