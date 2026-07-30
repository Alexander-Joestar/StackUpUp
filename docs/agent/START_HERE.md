# StackUpUp Agent Entry

本页只提供未来代理的文档入口，不复制规则正文或会话信息；T2–T14 当前仍处于规划阶段，T14 是规划、证据审查和迁移准入任务，不代表生产实现完成。

## 现行工程规范

- [AGENTS.md](../../AGENTS.md)：规定代理边界、证据记录、变更租约和验证要求； **状态：现行项目规范。**
- [2026-04-18-hard-rules.md](2026-04-18-hard-rules.md)：集中索引 coremod、Mixin、自动化和规则内核的硬门槛；
  **状态：现行硬约束。**

## 当前架构决策

- [compatibility-decision-record.md](compatibility-decision-record.md)：记录兼容层选型、容量安全取舍和已知限制；
  **状态：现行短决策记录。**
- [mixin-生态与注入最佳实践.md](mixin-%E7%94%9F%E6%80%81%E4%B8%8E%E6%B3%A8%E5%85%A5%E6%9C%80%E4%BD%B3%E5%AE%9E%E8%B7%B5.md)
  ：面向代理整理 Mixin 生态、MixinBooter 10.7/11 边界、CleanMix、两版 MixinExtras、Sponge Mixin、注入器选择以及
  Shadow/Redirect 审查； **状态：现行研究/代理参考，不是生产实现完成证明。**
- [借鉴仓库与重构对照.md](%E5%80%9F%E9%89%B4%E4%BB%93%E5%BA%93%E4%B8%8E%E9%87%8D%E6%9E%84%E5%AF%B9%E7%85%A7.md)：提供
  StackUp 与 biggerstacks-Unofficial 的只读对照及 StackUpUp 重构借鉴边界；
  **状态：现行研究/代理参考，不是生产实现完成证明。**

## 任务规划

- [重构任务清单.md](%E9%87%8D%E6%9E%84%E4%BB%BB%E5%8A%A1%E6%B8%85%E5%8D%95.md)：定义 T2–T14 的依赖、证据、写入范围和验收门；T14
  是规划、证据审查和迁移准入任务，不代表生产实现完成。 **状态：仅为计划，未表示生产实现已完成。**

## 用户文档

- [README.md](../../README.md)：提供中文安装、配置、命令和兼容性入口； **状态：现行发布说明。**
- [README.en.md](../../README.en.md)：提供英文安装、配置和使用入口； **状态：现行发布说明。**
- [CHANGELOG.md](../../CHANGELOG.md)：记录版本发布历史和变更； **状态：版本历史，不是工程规范。**
- [DSL-v2-规则示例.md](../DSL-v2-%E8%A7%84%E5%88%99%E7%A4%BA%E4%BE%8B.md)：提供 `.su` / `.su.md` 的用户语法示例；
  **状态：现行示例，以源码和测试为准。**
- [StackUpUp-实现与兼容性说明.md](../StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md)
  ：记录当前实现、兼容性边界和已知限制； **状态：现行实现说明。**
- [runServer-自动化回归.md](../runServer-%E8%87%AA%E5%8A%A8%E5%8C%96%E5%9B%9E%E5%BD%92.md)：记录当前 `runServer`
  自动化入口和回归方式； **状态：现行回归说明。**

## 历史/删除说明

- 不再保留双重根规范，旧文档已合并或移除。
- 旧 ASM/coremod、迁移、Cleanroom、Markdown gate、remainder 及其他历史文档已移除，本页不再建立链接。
