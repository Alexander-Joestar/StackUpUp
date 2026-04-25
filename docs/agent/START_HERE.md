# StackUpUp Agent Start Here

## 快速接手

先读这 4 份：

1. `docs/agent/stackupup-agent.md`
2. `docs/developer/Cleanroom-对齐与架构说明.md`
3. `docs/ASM-迁移状态.md`
4. `docs/runServer-自动化回归.md`

需要用户面 DSL 时再读：

1. `docs/DSL-v2-规则示例.md`
2. `docs/DSL-v2-迁移说明.md`
3. `docs/StackUpUp-实现与兼容性说明.md`

## 当前主线

StackUpUp 是 `Minecraft 1.12.2` 的大堆叠控制模组。当前阶段不优先加功能，重点是：

1. 压旧样板和重复中转
2. 固定目标优先 Mixin
3. 动态 ASM 只保留运行时才能确定的边界
4. 保持 `runServerAutoTestMatrix` 覆盖不回归

## 关键入口

1. `src/main/kotlin/io/alexjoest/stackupup/StackUpUp.kt`
2. `src/main/kotlin/io/alexjoest/stackupup/RuleRuntimeCoordinator.kt`
3. `src/main/kotlin/io/alexjoest/stackupup/StackLimitHooks.kt`
4. `src/main/kotlin/io/alexjoest/stackupup/limit/StackLimitService.kt`
5. `src/main/kotlin/io/alexjoest/stackupup/rules/parse/DslParser.kt`
6. `src/main/kotlin/io/alexjoest/stackupup/rules/compile/RuleConditionCompiler.kt`
7. `src/main/kotlin/io/alexjoest/stackupup/dev/DevAutomationServerDriver.kt`
8. `src/main/kotlin/io/alexjoest/stackupup/dev/DevCompatProbeRunner.kt`

## 验证

默认主回归：

```powershell
.\gradlew.bat runServerAutoTestMatrix
```

IDEA MCP 可用时，优先用它读文件、格式化和跑 IDE build。完整 Gradle / runServer 验证如果被本机 JDK 或 socket
环境拦住，要明确记录，不要把结构收缩工作卡死在工具问题上。
