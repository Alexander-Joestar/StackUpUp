# StackUpUp Agent Entry

> 状态：PARTIAL（当前基线已同步；T8.0、T14 与 T16 仍有未闭合项）

本页只提供代理接手入口，不复制规则正文或会话信息。当前构建事实是 MixinBooter **11.13** + CleanMix **0.7.1**；项目通过 manifest 的 `MixinConnector` 装载 `StackUpUpMixinConnector`（`IMixinConnector`）。10.7 与 `IEarlyMixinLoader`/`ILateMixinLoader` 仅保留为历史基线。

## 接手执行顺序

- 先读根级 [AGENTS.md](../../AGENTS.md)（唯一规范）与 [2026-04-18-hard-rules.md](2026-04-18-hard-rules.md)（硬门槛），再用 `jj st` / `jj diff` 检查工作副本；未提交改动受保护，不执行恢复、清理或提交；
- 先按 [重构任务清单.md](%E9%87%8D%E6%9E%84%E4%BB%BB%E5%8A%A1%E6%B8%85%E5%8D%95.md) 当前状态表和 DAG 选择任务。T8.0 为 PARTIAL，T8.2 为 BLOCKED；T12 方案 A 已归档且不再作为 T13 依赖；T13 BLOCKED；T14.1–T14.6 PARTIAL、T14.7 为 BLOCKED/PARTIAL；T15 的结构与验证分开记录；T16 为 UNKNOWN（尚未开始）；
- 每个任务开工前声明目标、租约、禁止事项、证据、安全不变量和验证；状态只用 PASS/FAIL/BLOCKED/UNKNOWN/PARTIAL，作者不得自审。容量证据不足写 `无源码不可判定`，运行/复核未完成不得写成通过。

## 现行工程规范

- [AGENTS.md](../../AGENTS.md)：规定代理边界、证据记录、变更租约和验证要求；现行项目规范。
- [2026-04-18-hard-rules.md](2026-04-18-hard-rules.md)：集中索引 coremod、Mixin、自动化和规则内核的硬门槛；现行硬约束。
- [子代理库.md](%E5%AD%90%E4%BB%A3%E7%90%86%E5%BA%93.md)：主代理编排时选用的可复用子代理定义（事实审查/写入路径复核/调用点盘点/Mixin inventory/字节码护栏/运行时验证/网络研究/文档收口/Terra 裁决/独立复核）；现行编排参考。

## 当前架构决策

- [compatibility-decision-record.md](compatibility-decision-record.md)：记录兼容层选型、容量安全取舍和已知限制；现行短决策记录。
- [mixin-生态与注入最佳实践.md](mixin-%E7%94%9F%E6%80%81%E4%B8%8E%E6%B3%A8%E5%85%A5%E6%9C%80%E4%BD%B3%E5%AE%9E%E8%B7%B5.md)：记录当前 MixinBooter 11.13、CleanMix 0.7.1、`IMixinConnector`、注入器选择和证据边界；10.7/旧 loader 仅作历史对照，不是当前注册方式，也不是生产实现完成证明。
- [借鉴仓库与重构对照.md](%E5%80%9F%E9%89%B4%E4%BB%93%E5%BA%93%E4%B8%8E%E9%87%8D%E6%9E%84%E5%AF%B9%E7%85%A7.md)：提供 StackUp 与 biggerstacks-Unofficial 的只读对照及 StackUpUp 重构借鉴边界；现行研究/代理参考，不是生产实现完成证明。

## 任务规划与状态

- [重构任务清单.md](%E9%87%8D%E6%9E%84%E4%BB%BB%E5%8A%A1%E6%B8%85%E5%8D%95.md)：定义任务依赖、证据、写入范围和验收门；当前状态以清单状态表为准。T8.0 为 PARTIAL、T8.2 为 BLOCKED、T12 方案 A 已归档且不再作为 T13 依赖、T13 BLOCKED、T14.1–T14.6 PARTIAL、T14.7 BLOCKED/PARTIAL，T15 分开记录结构与验证，T16 为 UNKNOWN（尚未开始）。

## 用户文档

- [README.md](../../README.md)：提供中文安装、配置、命令和兼容性入口；现行发布说明。
- [README.en.md](../../README.en.md)：提供英文安装、配置和使用入口；现行发布说明。
- [CHANGELOG.md](../../CHANGELOG.md)：记录版本发布历史和变更；版本历史，不是工程规范。
- [DSL-v2-规则示例.md](../DSL-v2-%E8%A7%84%E5%88%99%E7%A4%BA%E4%BE%8B.md)：提供 `.su` / `.su.md` 的用户语法示例；现行示例，以源码和测试为准。
- [StackUpUp-实现与兼容性说明.md](../StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md)：记录当前实现、兼容性边界和已知限制；现行实现说明。
- [runServer-自动化回归.md](../runServer-%E8%87%AA%E5%8A%A8%E5%8C%96%E5%9B%9E%E5%BD%92.md)：记录当前 `runServer` 自动化入口和回归方式；现行回归说明。

## 历史/删除说明

- 不再保留双重根规范，旧文档已合并或移除；旧 ASM/coremod、迁移、Cleanroom、Markdown gate、remainder 及其他历史文档已移除，本页不再建立链接。
