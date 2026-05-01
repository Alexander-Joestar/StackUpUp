# 2026-04-28 Session Summary

## Dragon / BrandonsCore

- 已修复空输入槽插入时吞掉 64 以上物品的问题：在 `Container.mergeItemStack` / click put 后按原始尝试数量恢复 remainder。
- 已加入安全插入策略：空槽只按声明 slot limit 接收，已有物品时才使用动态上限，避免未知模组空槽吞物品。
- 当前 GUI 重开显示 64、取出恢复完整数量，判断为客户端容器/Tile 显示副本被 64 上限夹断；服务器真实堆叠仍保留完整数量。
- 最终修复方向：不再 early mixin BrandonsCore 外部类，避免加载时序崩溃；改为客户端收到 `SPacketSetSlot` / `SPacketWindowItems` 后按服务器传入数量恢复本地 slot 显示副本。

## Rule Markdown Gates

- 规则文件使用 `.md` 容器，规则正文只解析 fenced code block：`stackupup` / `su`。
- Markdown 标题表示 gate 作用域；标题嵌套表示继承条件并取 AND；同级标题表示独立分支。
- 首批 gate：`always`、`modLoaded(id[, id...])`、`gate(name)`。
- 未来可扩展：`gameStages(...)`、`ftbQuestCompleted(...)`。
- 普通 Markdown 文本全部视为说明，不参与解析。
- 规则模板预先写好；脚本或外部系统只切换全服统一 gate 状态，不动态注入规则。
- gate 变化时只在启用块签名变化后重建 `RuleSnapshot` 并清空缓存；热路径不解析 Markdown。

## Runtime Constraints

- 堆叠规则保持 world/server-wide，不做 per-player 上限。
- 不做文件变化自动刷新，避免整合包中额外性能波动。
- 服务端测试优先；客户端只在 GUI/渲染/同步问题必要时验证。
