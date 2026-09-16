# StackUpUp Agent Entry

> 状态：PARTIAL（基线已同步；T8.0、T8.2、T13、T15、T16 仍有未闭合项）

当前基线：MixinBooter **11.17** + CleanMix **0.7.2**，manifest `MixinConnector` 装载 `StackUpUpMixinConnector`（`IMixinConnector`）；10.7 与 `IEarlyMixinLoader`/`ILateMixinLoader` 仅为历史基线。

## 入口

- 规范：[AGENTS.md](../../AGENTS.md)（唯一规范）；硬门槛：[2026-04-18-hard-rules.md](2026-04-18-hard-rules.md)。
- 任务与状态：[重构任务清单.md](%E9%87%8D%E6%9E%84%E4%BB%BB%E5%8A%A1%E6%B8%85%E5%8D%95.md)。
- 决策：[compatibility-decision-record.md](compatibility-decision-record.md)；Mixin 生态：[mixin-生态与注入最佳实践.md](mixin-%E7%94%9F%E6%80%81%E4%B8%8E%E6%B3%A8%E5%85%A5%E6%9C%80%E4%BD%B3%E5%AE%9E%E8%B7%B5.md)；对照研究：[借鉴仓库与重构对照.md](%E5%80%9F%E9%89%B4%E4%BB%93%E5%BA%93%E4%B8%8E%E9%87%8D%E6%9E%84%E5%AF%B9%E7%85%A7.md)；编排：[子代理库.md](%E5%AD%90%E4%BB%A3%E7%90%86%E5%BA%93.md)。
- 用户文档：[README.md](../../README.md)、[README.en.md](../../README.en.md)、[CHANGELOG.md](../../CHANGELOG.md)、[DSL-v2-规则示例.md](../DSL-v2-%E8%A7%84%E5%88%99%E7%A4%BA%E4%BE%8B.md)、[StackUpUp-实现与兼容性说明.md](../StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md)、[runServer-自动化回归.md](../runServer-%E8%87%AA%E5%8A%A8%E5%8C%96%E5%9B%9E%E5%BD%92.md)。

## 未闭合项

- T8.0 真实 F3+T 前后对比未做（PARTIAL）；T8.2 等 T8.0 基线（BLOCKED）。
- T13 收缩矩阵未闭合（BLOCKED）；T15 结构已落地、验证未完成（PARTIAL）；T16 未开始（UNKNOWN）。
- T14.1–T14.6 与 T14.7 是审计参考材料与已知限制清单，不是待办任务，也不是准入门。
- 第三方 jar 无源码项保持 `无源码不可判定`；未实际运行的验证不得写成通过。
