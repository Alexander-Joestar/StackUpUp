# `runServer` 自动化回归

> 状态：PARTIAL（源码入口已同步；未知 probe ID、可用性异常与探针执行异常已按源码记录为失败而非跳过。最近一次矩阵运行通过（2026-08-10，`run/logs/autotest-report.txt` 最终状态 PASS），但此后代码改动未经矩阵重跑，独立复核与完整运行门仍未闭合）
> 文档定位：源码核对说明，描述当前构建脚本、自动化入口与驱动源码，不是运行结果报告；未运行或未独立复核的自动验收不写成“已通过”。
>
> 当前构建基线：MixinBooter 11.13 + CleanMix 0.7.1，Mixin 配置经 `IMixinConnector` 注册；10.7 与旧 early/late loader 仅作历史记录。

## 当前实现

### 入口

| Gradle 任务               | 当前行为                                                                                                                                                                           |
|---------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `runClientAutoTest`       | 只依赖 `runClient`；驱动进入测试世界、注入临时规则、发送 `/give`，再观察目标物品。                                                                                                 |
| `runServerAutoTest`       | 只依赖 `runServer`；服务端在 `FMLServerStartedEvent` 后运行单目标探针：解析目标、计算规则上限，并用真实 `ItemStackHandler.insertItem(..., false)` 检查存入数量和 remainder。       |
| `runServerAutoTestMatrix` | `Exec` 包装任务，以 `--no-daemon` 启动子 Gradle 的 `runServerAutoTest`，一次服务端启动中运行内建目标矩阵和兼容探针；矩阵参数由任务固定传给子进程。                                 |

`build-logic/convention/src/main/kotlin/autoTest.gradle.kts` 另注册 `runServerAutoTestIngotSteel`、`runServerAutoTestPlateSteel`、`runServerAutoTestDustSteel`、`runServerAutoTestVacuumTube`，均为带固定目标参数的服务端包装任务，不是另一套驱动实现（`autoTest.gradle.kts:195-253,255-293`）。

服务端入口链：`StackUpUp.serverStarted()` → `DevAutomationBridge.runServerAutomation()` → `DevAutomationServerDriver.run()`（`src/main/kotlin/io/alexjoest/stackupup/StackUpUp.kt:114-123`、`src/main/kotlin/io/alexjoest/stackupup/DevAutomationBridge.kt:32-46`）；客户端在 proxy 注册 tick 驱动（`ProxyClient.kt:50-54`）。IDEA 运行配置只注册普通 Client/Server、`Run Server AutoTest Matrix` 及混淆配置，无单独 `runServerAutoTest`/`runClientAutoTest` 配置，后两者按 Gradle 任务执行（`autoTest.gradle.kts:97-107,255-293`）。

### 默认关闭与参数链

自动验收默认关闭，仅当任务名为 `runClientAutoTest`/`runServerAutoTest` 或传入 `-PstackupupDevAutoTest=true` 时，构建脚本才向 Minecraft JVM 注入自动验收属性。推荐在 Windows 项目根目录执行：

```bat
.\gradlew.bat runServerAutoTest
.\gradlew.bat runServerAutoTestMatrix
```

单独执行 `runServer` 不会开启自动验收；用通用入口须显式指定服务端模式：

```bat
.\gradlew.bat runServer -PstackupupDevAutoTest=true -PstackupupDevAutoTestMode=server
```

除 `ServerPort` 外，表列参数由 `build-logic/convention/src/main/kotlin/autoTest.gradle.kts` 读取并转换为 `-Dstackupup.dev.autoTest.*`，再由 `DevAutomationConfig` 读取；`ServerPort` 只由 Gradle 准备任务写入 `run/server.properties`。`compat` 无 `-P` 转发；`autoShutdown`、`failFast`、`clearInventoryBeforeGive` 由构建脚本固定注入（`autoTest.gradle.kts:41-93,129-173`、`src/main/kotlin/io/alexjoest/stackupup/dev/DevAutomationConfig.kt:53-78`）：

| Gradle 属性                       | 传给游戏的 JVM 属性                  | 默认值或说明                                                                                    |
|-----------------------------------|--------------------------------------|-------------------------------------------------------------------------------------------------|
| `stackupupDevAutoTest`            | `stackupup.dev.autoTest.enabled`     | `true` 开启；任务入口会自动开启。                                                               |
| `stackupupDevAutoTestMode`        | `stackupup.dev.autoTest.mode`        | `client`；`runClientAutoTest`/`runServerAutoTest` 的任务名优先，分别强制 `client`/`server`。    |
| `stackupupDevAutoTestOre`         | `stackupup.dev.autoTest.ore`         | `ingotSteel`。                                                                                  |
| `stackupupDevAutoTestRule`        | `stackupup.dev.autoTest.rule`        | `ore = <ore> -> 1024`；矩阵任务显式传空规则，不追加临时规则。                                   |
| `stackupupDevAutoTestItem`        | `stackupup.dev.autoTest.item`        | 空字符串；显式 `item/meta` 有效时优先，无效时回退到矿辞候选。                                   |
| `stackupupDevAutoTestMeta`        | `stackupup.dev.autoTest.meta`        | `11305`。                                                                                       |
| `stackupupDevAutoTestCount`       | `stackupup.dev.autoTest.count`       | `128`。                                                                                         |
| `stackupupDevAutoTestWorldFolder` | `stackupup.dev.autoTest.worldFolder` | `stackupup_dev_autotest`。                                                                      |
| `stackupupDevAutoTestWorldName`   | `stackupup.dev.autoTest.worldName`   | `StackUpUp 自动测试`。                                                                          |
| `stackupupDevAutoTestServerPort`  | —（不注入游戏 JVM）                  | `0`；仅由 `prepareAutoTestServerFiles` 写入 `run/server.properties`，矩阵包装任务也固定传 `0`。 |
| `stackupupDevAutoTestMatrix`      | `stackupup.dev.autoTest.matrix`      | `false`；`runServerAutoTestMatrix` 固定传 `true`。                                              |

启用自动验收时固定传入 `autoShutdown=true`、`failFast=true`、`clearInventoryBeforeGive=true`，无对应 `-PstackupupDevAutoTestAutoShutdown`/`...FailFast`/`...ClearInventoryBeforeGive` 映射；旧 `stackupDevAutoTest*` 和 `stackup.dev.autoTest*` 仅是源码中的兼容 fallback，不作为新命令示例。`DevAutomationConfig` 还读取 `compat`，但当前 `autoTest.gradle.kts` 未把 `stackupupDevAutoTestCompat` 转发为游戏 JVM 属性，不要当作现成的 `-P` 参数使用。

### 服务端运行环境与 `ServerPort`

服务端模式开启时，`prepareAutoTestServerFiles` 在项目根 `run/` 下准备无交互启动文件（`build-logic/convention/src/main/kotlin/autoTest.gradle.kts:109-173`）：写 `run/eula.txt`（`eula=true`）；重写 `run/server.properties` 的 `online-mode`（`false`）、`server-port`（`stackupupDevAutoTestServerPort`）、`level-name`（`stackupupDevAutoTestWorldFolder`），其他属性保留。

需要可写的 `run/` 目录、可用的 Forge 1.12.2 开发运行环境及本地模组依赖。默认端口 `0`；固定端口在 `runServerAutoTest` 上显式传 `-PstackupupDevAutoTestServerPort=<端口>`；矩阵包装任务对子进程固定用 `0`，外层同名参数不自动转发。脚本不清理 `level-name` 对应世界目录，重复运行应通过独立 `WorldFolder` 避免复用旧世界状态。

### 服务端探针与矩阵

单目标服务端驱动（`DevAutomationServerDriver.kt:43-109`）：

1. 临时规则非空且未注入时追加一次 DSL 规则；注入失败记录错误，仅 `autoShutdown=true` 时请求停服。
2. 按显式 `item/meta` 或矿辞解析运行时目标；找不到时记录错误，仅 `autoShutdown=true` 时请求停服。
3. 计算规则解析结果，复制目标栈以 `simulate=false` 执行真实插入，记录 `requested`、规则上限、实际栈上限、槽位上限、存入数量和真实返回的 remainder。
4. 以 `min(requested, actualLimit, slotLimit)` 与真实观察值做审计比较；源码另算预期 remainder 只用于比较，不回填、重试、补偿或替换真实返回值；成功路径记录“验证通过”并按配置请求停服。

矩阵内建目标为 `IngotSteel`、`PlateSteel`、`DustSteel`、`VacuumTube`；目标全部未解析且 `gregtech` 未加载时只跳过内建 GT/metadata 专项，部分未解析或 `gregtech` 已加载但全部未解析则形成失败。内建循环逐目标捕获执行异常后继续其他目标；内建循环正常完成后才运行 `DevCompatProbeRunner` 注册的兼容探针。兼容探针只有明确的 `ClassNotFoundException` 才按目标模组未加载跳过；其他可用性异常、探针执行异常和未知 probe ID 都记录失败（`DevAutomationServerDriver.kt:111-184`、`DevCompatProbeSupport.kt:10-17,59-63`、`DevCompatProbeRunner.kt:27-74`）。

安全底线是“对外广告容量不大于真实写入路径容量”。当前 `evaluateProbeResult` 直接拒绝 `actualLimit > slotLimit`，并按 `min(requested, actualLimit, slotLimit)` 同时校验真实存入量和 remainder；这只覆盖探针实际使用的 `ItemStackHandler` 写入面，不等于所有未知 handler 或第三方路径已闭合。当前已移除 dynamic ASM replacement layer，未知 `IItemHandler` 不因缺少显式 Mixin 目标而扩容；`SlotItemHandler` 现状仍有非 64 分支风险，不能因接口实现推断容量安全。Forge `EntityEquipmentInvWrapper#insertItem` 会计算插入上限并返回 `remainder`；`setStackInSlot` 与 vanilla setter 是另一条写入路径，不能用该 `insertItem` 的 limit/remainder 行为概括。第三方写入路径没有源码证据时，结论只能写“无源码不可判定”，不能依据类名或代理探针臆测。

## 日志与报告定位

当前自动化实现同时调用 `StackUpUp.logger` 并在服务端运行目录的 `logs/autotest-report.txt` 写入自动化报告；报告是文本证据，不替代独立复核。

- 游戏运行日志：按默认 Forge/RFG `run/` 目录约定查看 `run/logs/latest.log`，搜索 `开发自动验收`、`开发自动验收[服务端]`、`开发自动验收[兼容探针]`；构建脚本只准备 `run/eula.txt` 与 `run/server.properties`，未显式配置日志目录，以实际运行目录为准。
- Gradle 输出：直接运行任务的终端输出；`runServerAutoTestMatrix` 的子 Gradle 命令附带 `--stacktrace`。
- 崩溃信息：Forge 生成崩溃报告时默认按同一运行目录查看 `run/crash-reports/`，该路径同样依赖运行环境。

`latest.log` 中的“通过/失败”是驱动日志；`logs/autotest-report.txt` 记录每次服务端自动验收的逐行结果和最终状态，但它仍不等于独立复核结论。

## 失败、异常与退出边界

- 规则注入失败、目标物品未解析：服务端记录错误并按 `failFast` 抛出；桥接层捕获驱动异常、写入 `run/logs/autotest-failed.marker` 并请求服务端停服。直接 `runServerAutoTest` 的 JVM 仍可能以 0 结束，矩阵包装任务会检查 marker 并升级为 Gradle 失败。
- 单目标或矩阵探针失败：构建默认 `failFast=true`，驱动抛出 `IllegalStateException`；配置为 `false` 时写入文本报告并按 `autoShutdown` 请求停服，不把失败静默改成通过。
- 兼容探针 `compat` 请求按已注册 ID 选择；未知 ID 会生成 `unknown_probe_id` 失败并写错误日志。已注册探针只有明确的 `ClassNotFoundException` 才按目标模组未加载跳过，其他可用性异常和执行异常都记录失败（`src/main/kotlin/io/alexjoest/stackupup/dev/DevAutomationConfig.kt:97-116`、`DevCompatProbeRunner.kt:27-74`）。
- 矩阵内建 unresolved 分支逐目标处理并继续其他目标；部分 unresolved（或 `gregtech` 已加载时全部 unresolved）形成失败，结果写入自动化报告并在 `failFast` 时通过桥接 marker 暴露（`DevAutomationServerDriver.kt:111-184`）。
- 关键边界在 `DevAutomationBridge.runServerAutomation()`：反射调用被 `runCatching` 包围，驱动异常由桥接层记录、写失败 marker 并请求停服；桥接不向 Forge/Gradle 重新抛出，也无 `System.exit`。`runServerAutoTestMatrix` 的外层 `Exec` 在子任务结束后检查 marker，因而可获得非零结果。
- `DevAutomationBridge.isEnabled()` 读取配置类或 getter 出错时用 `getOrDefault(false)` 当作关闭，不升级为失败或桥接错误（`src/main/kotlin/io/alexjoest/stackupup/DevAutomationBridge.kt:67-71`）。
- `runServerAutoTestMatrix` 现已对内建目标逐目标隔离异常，并分别记录兼容探针的未知 ID、可用性异常和执行异常；但是否通过仍须同时查看 Gradle 结果、`autotest-failed.marker`、`autotest-report.txt` 和日志，不能仅凭单条日志宣称矩阵通过。
- 客户端驱动规则注入失败或目标缺失会 `abort` 状态机，不抛异常，无关闭客户端或 CI 退出码实现；`runClientAutoTest` 是开发客户端流程，不是可靠的进程级测试门禁。

## 已知限制

1. `autotest-report.txt` 是文本报告而非独立机器可读 schema；`autotest-failed.marker` 由矩阵包装任务检查，直接 `runServerAutoTest` 的 JVM 退出码仍不单独构成可靠失败契约。
2. `runServerAutoTestMatrix` 是固定参数的子 Gradle 包装器，不会把外层自定义 `-PstackupupDevAutoTest*` 自动传给子进程。
3. `compat` 虽被运行时配置读取，但当前构建脚本没有对应的 `-P` 转发项。
4. 服务端探针现已拒绝 `actualLimit > slotLimit`，并比较 `min(requested, actualLimit, slotLimit)` 与真实存入/remainder；这仍不是未知 handler 或第三方写入路径的完整容量审计。
5. `hasClass()` 只有 `ClassNotFoundException` 才表示目标缺失并跳过；其他链接/类加载异常进入失败，内建目标探针也逐目标隔离，但完整矩阵仍需实际运行验证。
6. `run/logs/latest.log` 与 `run/crash-reports/` 是默认 Forge/RFG 运行目录约定；自动化文本报告和失败 marker 由驱动/桥接相对服务端运行目录写入，不能替代独立复核。
7. 缺第三方源码时，兼容探针只能提供运行时观测；写入路径结论保持“无源码不可判定”。

## 计划中的任务

本次文档同步不重新裁决 T2–T13；已实现的失败 marker、文本报告和广告容量失败门按源码记录，T12 JSONL 方案 A 仍为历史归档，不作为 T13 依赖。第三方写入路径和未完成矩阵仍待对应任务提供源码与实际验证证据。

## 本次核对范围

本次只静态核对了 `build-logic/convention/src/main/kotlin/autoTest.gradle.kts:41-293`、`src/main/kotlin/io/alexjoest/stackupup/StackUpUp.kt:114-123`、`src/main/kotlin/io/alexjoest/stackupup/DevAutomationBridge.kt:32-71`、`ProxyClient.kt`、`dev/` 下自动化配置/客户端/服务端驱动/兼容探针源码及相关测试；本 lane 未执行 `runClientAutoTest`、`runServerAutoTest` 或 `runServerAutoTestMatrix`，因此本文不提供本次文档同步的运行通过结论。最近一次矩阵运行结果为 2026-08-10 PASS（`run/logs/autotest-report.txt`），属既有运行产物，不代表此后代码改动已验证。
