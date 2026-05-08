# Parent Dispatch Manual

这份文档给父代理使用，用来把 StackUpUp 的工作拆成短输入、短输出、低冲突的子代理任务。

参考原则来自 Codex project subagents 的常见用法：项目代理放在 `.codex/agents/`，用 `.toml` 描述，父代理显式分派，子代理拥有独立上下文。

## 调度原则

1. 先拆只读任务，再安排写入任务。
2. 只读任务可以并行，写入任务默认串行。
3. 同一批并行写入必须拥有完全不同的文件范围。
4. 5.4-mini 用于小任务、日志筛查、文档收敛和事实核对。
5. 5.3-codex-spark 用于边界清晰的实现、测试和机械重构。
6. 5.4 用于架构裁决、复杂兼容判断和最终审查。
7. 默认最多一层子代理，不做递归代理树。

## 输入契约

给子代理的输入保持固定形状：

```text
目标：
范围：
禁止：
参考：
输出：
验收：
```

示例：

```text
目标：定位 EnderIO 机器输出仍受 64 限制的调用链。
范围：只读，EnderIO late mixin 与兼容补丁相关文件。
禁止：不要修改代码，不要泛泛讨论架构。
参考：docs/ASM-迁移状态.md、现有 EnderIO mixin。
输出：文件/类/方法、硬编码来源、建议交给哪个实现代理。
验收：至少给出一个可验证调用链。
```

## 输出契约

要求子代理返回：

```text
结论：
证据：
风险：
下一步：
```

输出要能被父代理直接合并。不要让子代理写长篇背景、重复项目介绍或给出不带文件位置的建议。

## 常用分派模板

只读定位：

```text
你是只读定位代理。请不要修改文件。
目标：...
范围：...
输出：一句话结论，最多 5 条证据，每条带文件和符号。
```

实现任务：

```text
你是实现代理。你不是唯一工作者，不要回滚他人改动。
目标：...
写入范围：...
禁止：...
验证：...
完成后列出修改文件、测试结果和仍有风险。
```

小补丁审查：

```text
你是只读审查代理。
请检查这次改动是否满足目标，重点看行为回归、兼容风险和缺失测试。
输出按严重程度排列，必须引用文件和符号。
```

架构裁决：

```text
你是只读架构裁决代理。
问题：...
候选方案：...
输出：推荐方案、反对方案的具体风险、需要的验证入口。
不要改代码。
```

## 项目代理速查

| 代理 | 模型 | 权限 | 用途 |
| --- | --- | --- | --- |
| `task_planner` | 5.4 | read-only | 把模糊目标拆成任务、依赖和验收 |
| `async_dispatch_coordinator` | 5.4-mini | read-only | 安排并行批次和合并规则 |
| `stackup_explorer` | 5.3-codex | read-only | 定位真实代码路径 |
| `dsl_worker` | 5.3-codex | write | DSL、规则加载、编译器、相关测试 |
| `localization_worker` | 5.3-codex | write | lang、命令反馈、配置文本、本地化测试 |
| `runtime_hook_worker` | 5.3-codex | write | 已裁定的 Mixin/ASM/运行时接线 |
| `network_protocol_worker` | 5.3-codex | write | 网络包、序列化、客户端同步 |
| `dev_automation_worker` | 5.3-codex | write | 自动化探针、runServer 矩阵、回归文档 |
| `forge_runtime_validator` | 5.4-mini | write | 构建、启动、日志和回归验证 |
| `client_config_triage` | 5.4-mini | read-only | 客户端显示、配置、命令反馈定位 |
| `gregtech_compat_researcher` | 5.4-mini | read-only | GT、矿辞、Forge 事实核查 |
| `gregtech_compat_validator` | 5.4 | write | GT 运行时兼容验证 |
| `core_compat_architect` | 5.4 | read-only | 核心兼容链与动态 ASM 边界裁决 |
| `mixin_asm_migration_specialist` | 5.4 | write | 固定目标迁移方案与高风险 Mixin/ASM 修改 |
| `small_patch_reviewer` | 5.4-mini | read-only | 1 到 3 文件小补丁审查 |
| `reviewer` | 5.4 | read-only | 最终代码审查 |
| `release_docs_curator` | 5.4-mini | write | changelog、文档瘦身、发布清单 |

## 推荐流程

小任务：

1. 父代理直接读上下文。
2. 派 `small_patch_reviewer` 或对应 mini 代理做只读确认。
3. 父代理或单个实现代理改动。
4. 跑定点测试。

复杂兼容问题：

1. `task_planner` 拆任务。
2. `stackup_explorer`、`gregtech_compat_researcher`、`client_config_triage` 等只读代理并行定位。
3. `core_compat_architect` 或 `mixin_asm_migration_specialist` 裁决方案。
4. 单个实现代理写入。
5. `forge_runtime_validator` 验证。
6. `reviewer` 做最终审查。

发布前：

1. `release_docs_curator` 收敛文档和 changelog。
2. `forge_runtime_validator` 跑可用的构建与回归。
3. `reviewer` 检查范围漂移和缺失测试。
> **已过时**
