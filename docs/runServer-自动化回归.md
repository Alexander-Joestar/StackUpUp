# `runServer` 自动化回归

> **文档状态：源码核对说明。** 本文描述当前构建脚本、自动化入口和相关驱动源码，不是运行结果报告；本次未把任何自动验收写成“已通过”。

## 当前实现

### 入口

| Gradle 任务               | 当前行为                                                                                                                                                                           |
|---------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `runClientAutoTest`       | 任务只依赖 `runClient`。启用客户端驱动后，驱动进入测试世界、注入临时规则、发送 `/give`，再观察目标物品。                                                                           |
| `runServerAutoTest`       | 任务只依赖 `runServer`。服务端在 `FMLServerStartedEvent` 后运行单目标探针：解析目标、计算规则上限，并用真实的 `ItemStackHandler.insertItem(..., false)` 检查存入数量和 remainder。 |
| `runServerAutoTestMatrix` | `Exec` 包装任务，以 `--no-daemon` 启动子 Gradle 的 `runServerAutoTest`，在一次服务端启动中运行内建目标矩阵和兼容探针。矩阵参数由任务本身固定传给子进程。                           |

`build.gradle.kts` 还注册了 `runServerAutoTestIngotSteel`、`runServerAutoTestPlateSteel`、`runServerAutoTestDustSteel` 和
`runServerAutoTestVacuumTube`；它们都是带固定目标参数的服务端包装任务，不是另一套驱动实现（`build.gradle.kts:565-640`）。

服务端入口链是 `StackUpUp.serverStarted()` → `DevAutomationBridge.runServerAutomation()` →
`DevAutomationServerDriver.run()`（`src/main/kotlin/io/alexjoest/stackupup/StackUpUp.kt:110-119`、
`DevAutomationBridge.kt:27-37`）。客户端则在客户端 proxy 注册 tick 驱动（`ProxyClient.kt:85-88`）。IDEA 运行配置只注册普通
Client/Server、`Run Server AutoTest Matrix` 及混淆运行配置，没有单独的 `runServerAutoTest` 或 `runClientAutoTest`
配置；后两者应按上面的 Gradle 任务执行（`build.gradle.kts:518-533`）。

### 默认关闭与参数链

自动验收默认关闭。只有以下任一条件成立时，构建脚本才向 Minecraft JVM 注入自动验收属性：

- 任务名是 `runClientAutoTest` 或 `runServerAutoTest`；
- 传入 `-PstackupupDevAutoTest=true`。

推荐在 Windows 项目根目录执行：

```bat
.\gradlew.bat runServerAutoTest
.\gradlew.bat runServerAutoTestMatrix
```

单独执行 `runServer` 不会开启自动验收。若用通用 `runServer` 入口，必须显式指定服务端模式：

```bat
.\gradlew.bat runServer -PstackupupDevAutoTest=true -PstackupupDevAutoTestMode=server
```

表中列出的可转发参数（除 `ServerPort` 外）由 `build.gradle.kts` 读取，再转换为 `-Dstackupup.dev.autoTest.*`，最后由
`DevAutomationConfig` 读取；`ServerPort` 只由 Gradle 的准备任务写入 `run/server.properties`。`compat` 没有 `-P` 转发，
`autoShutdown`、`failFast` 和 `clearInventoryBeforeGive` 则由构建脚本固定注入（`build.gradle.kts:70-98,161-204,335-350`、
`DevAutomationConfig.kt:53-78`）。可用的构建参数如下：

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

启用自动验收时，构建脚本还固定传入 `autoShutdown=true`、`failFast=true` 和 `clearInventoryBeforeGive=true`。当前没有对应的
`-PstackupupDevAutoTestAutoShutdown`、`...FailFast` 或 `...ClearInventoryBeforeGive` 映射。旧的 `stackupDevAutoTest*` 和
`stackup.dev.autoTest*` 仅是源码中的兼容 fallback，不作为新命令示例。

`DevAutomationConfig` 还读取 `compat`，但当前 `build.gradle.kts` 没有把 `stackupupDevAutoTestCompat` 转发为游戏 JVM
属性；不要把它当作现成的 `-P` 参数使用。

### 服务端运行环境与 `ServerPort`

服务端模式开启时，`prepareAutoTestServerFiles` 会在项目根的 `run/` 下准备无交互启动文件（`build.gradle.kts:161-204`）：

- 写入 `run/eula.txt`，内容包含 `eula=true`；
- 重写 `run/server.properties` 中的 `online-mode`、`server-port` 和 `level-name`；
- `online-mode=false`，`server-port` 使用 `stackupupDevAutoTestServerPort`，`level-name` 使用
  `stackupupDevAutoTestWorldFolder`，其他属性保留。

因此需要可写的 `run/` 目录、可用的 Forge 1.12.2 开发运行环境以及运行所需的本地模组依赖。默认端口值是 `0`；需要固定端口时，在
`runServerAutoTest` 上显式传 `-PstackupupDevAutoTestServerPort=<端口>`。矩阵包装任务对子进程固定使用 `0`，外层传入的同名参数不会自动转发。

脚本只准备 EULA 和服务端属性，没有清理 `level-name` 对应的世界目录；重复运行应通过独立的 `WorldFolder` 避免复用旧世界状态。

### 服务端探针与矩阵

单目标服务端驱动（`DevAutomationServerDriver.kt:16-83`）会：

1. 在临时规则非空且尚未注入时追加一次 DSL 规则；注入失败时记录错误，并仅在 `autoShutdown=true` 时请求停服。
2. 按显式 `item/meta` 或矿辞解析运行时目标；找不到目标时记录错误，并仅在 `autoShutdown=true` 时请求停服。
3. 计算规则解析结果，复制目标栈并以 `simulate=false` 执行真实插入，记录 `requested`、规则上限、实际栈上限、槽位上限、存入数量和真实返回的
   remainder。
4. 用 `min(requested, actualLimit, slotLimit)` 和真实观察值做审计比较；源码另计算一个预期 remainder
   只用于比较，不回填、重试、补偿或替换真实返回值。成功路径记录“验证通过”并按配置请求停服。

矩阵任务的内建目标是 `IngotSteel`、`PlateSteel`、`DustSteel` 和 `VacuumTube`。目标全部未解析且 `gregtech` 未加载时，只跳过内建
GT/metadata 专项；部分未解析，或 `gregtech` 已加载但全部未解析，会形成失败。内建循环正常完成后才运行 `DevCompatProbeRunner`
注册的兼容探针；`hasClass()` 将类加载异常视为不可用并跳过，只有其他显式可用性异常才记为失败；探针执行异常会转换为失败（
`DevAutomationServerDriver.kt:85-123`、`DevCompatProbeSupport.kt:10-17,59-63`、`DevCompatProbeRunner.kt:27-64`）。

安全底线仍是“对外广告容量不大于真实写入路径容量”，但当前 `evaluateProbeResult` 没有直接断言 `actualLimit <= slotLimit`
，因此该探针不是完整的广告容量审计；现有测试甚至把 `actualLimit=512`、`slotLimit=64`、实际存入/剩余各 `64` 判为通过（
`src/main/kotlin/io/alexjoest/stackupup/dev/DevAutomationServerDriver.kt:210-241`、
`src/test/kotlin/io/alexjoest/stackupup/dev/DevProbeEvaluatorTest.kt:43-56`）。未知 `IItemHandler` 的 dynamic ASM 路径不扩展，但
`SlotItemHandler` 现状仍有非 64 分支风险；不能因接口实现而推断容量安全。Forge 的 `EntityEquipmentInvWrapper#insertItem`
会计算插入上限并返回 `remainder`；`setStackInSlot` 与 vanilla setter 是另一条写入路径，不能用该 `insertItem` 的
limit/remainder 行为概括。第三方写入路径若没有源码证据，结论只能写“无源码不可判定”，不能依据类名或代理探针臆测。

## 日志与报告定位

当前自动化实现只调用 `StackUpUp.logger` 记录日志，没有写入自动化专用 JSON、文本报告或其他报告文件。

- 游戏运行日志：在默认 Forge/RFG `run/` 运行目录约定下查看 `run/logs/latest.log`；搜索 `开发自动验收`、`开发自动验收[服务端]`
  和 `开发自动验收[兼容探针]`。构建脚本明确准备的是 `run/eula.txt` 与 `run/server.properties`
  ，没有显式配置日志目录；若运行配置改变了目录，应以实际运行目录为准。
- Gradle 输出：直接运行任务的终端输出；`runServerAutoTestMatrix` 的子 Gradle 命令附带 `--stacktrace`。
- 崩溃信息：若 Forge 生成崩溃报告，默认按同一运行目录查看 `run/crash-reports/`；该路径同样依赖运行环境。

`latest.log` 中的“通过”或“失败”是驱动日志，不等于独立机器报告；当前也没有稳定的自动化报告路径可供其他工具读取。

## 失败、异常与退出边界

- 规则注入失败、目标物品未解析：服务端记录错误并调用 `shutdownIfRequested()`；只有 `autoShutdown=true` 时才会执行
  `server.initiateShutdown()`，且这两类路径不会抛出到 Gradle。即使服务端随后正常结束，也不能据此保证 Gradle 得到非零退出码。
- 单目标或矩阵探针失败：构建默认把 `failFast` 设为 `true`，驱动抛出 `IllegalStateException`；源码配置为 `false` 时只记录失败，并仅在
  `autoShutdown=true` 时请求停服。
- 兼容探针的 `compat` 请求先按已注册 ID 过滤；未知 ID 不会进入 `selectedProbes`，若过滤后为空则直接返回空列表。因此未知
  `compat` ID 可能静默跳过，既不加入 `failures` 列表，也不写 skip/失败日志；“未加入 failures”不等于“写入日志”（
  `DevAutomationConfig.kt:81-92`、`DevCompatProbeRunner.kt:27-33`）。
- 矩阵内建目标的 unresolved 分支先只累加计数；部分 unresolved（或 `gregtech` 已加载时全部 unresolved）再把汇总字符串加入内存中的
  `failures` 列表，但没有对应的 logger 调用。`failFast=false` 时不抛出该列表内容，因此这类失败只留在内存列表中，不保证写入日志；“加入
  failures 列表”不等于“写入日志”（`DevAutomationServerDriver.kt:96-120`）。
- 关键边界在 `DevAutomationBridge.runServerAutomation()`：反射调用被 `runCatching`
  包围，驱动抛出的异常会在桥接层记录“开发自动验收服务端桥接失败”，不会继续抛给 Forge 或 Gradle。抛异常的失败分支发生在
  `shutdownIfRequested()` 之前，因此当前没有“失败必停服”或“失败必非零退出”的保证，也没有 `System.exit`。
- `DevAutomationBridge.isEnabled()` 自身在读取配置类或 getter 出错时用 `getOrDefault(false)` 当作关闭，未将该异常升级为失败或桥接错误（
  `DevAutomationBridge.kt:14-43`）。
- `runServerAutoTestMatrix` 只有子 Gradle 在任务层失败时才自然获得非零结果；内建目标的 `probeTarget()`
  异常没有逐目标捕获，可能在兼容探针运行前中断，随后仍会被桥接层捕获。驱动层失败可能只出现在 `latest.log`
  ，甚至因未请求停服而使进程继续运行；不能仅凭命令返回或日志中的单条信息宣称矩阵通过。
- 客户端驱动的规则注入失败或目标缺失会 `abort` 状态机，不抛异常，也没有关闭客户端或返回 CI 退出码的实现；
  `runClientAutoTest` 是开发客户端流程，不是可靠的进程级测试门禁。

## 已知限制

1. 没有独立、机器可读的自动化报告，也没有可靠的 Gradle 失败退出契约。
2. `runServerAutoTestMatrix` 是固定参数的子 Gradle 包装器，不会把外层自定义 `-PstackupupDevAutoTest*` 自动传给子进程。
3. `compat` 虽被运行时配置读取，但当前构建脚本没有对应的 `-P` 转发项。
4. 当前服务端探针没有断言 `actualLimit <= slotLimit`；已有测试允许动态物品上限高于槽位上限时通过，因此它不是完整的广告容量审计。
5. `hasClass()` 会把类加载异常视为“不可用”并跳过，链接错误不一定进入失败列表；内建目标探针异常也没有逐目标隔离。
6. `run/logs/latest.log` 与 `run/crash-reports/` 是默认 Forge/RFG 运行目录约定，不是本项目构建脚本显式声明的报告契约。
7. 缺少第三方源码时，兼容探针只能提供运行时观测；写入路径结论保持“无源码不可判定”。

## 计划中的任务

T2–T13 不在本次文档实现范围。本次不新增退出码、报告格式、容量补偿或第三方写入路径结论，也不把这些计划行为写成当前实现；待对应任务有源码和实际验证证据后再单独更新本文。

## 本次核对范围

本次只静态核对了 `build.gradle.kts:70-98,161-204,306-350,518-533,565-640`、`StackUpUp.kt:110-119`、
`DevAutomationBridge.kt`、`ProxyClient.kt`、`dev/` 下自动化配置/客户端/服务端驱动/兼容探针源码，以及
`DevProbeEvaluatorTest.kt` 的容量反例。未执行 `runClientAutoTest`、`runServerAutoTest` 或 `runServerAutoTestMatrix`
，因此本文不提供任何运行通过结论。
