# DSL v2 迁移说明

## 适用范围

本说明适用于 StackUpUp 在 **Minecraft 1.12.2** 环境下的新规则系统。

目标：

1. 支持 `metadata` 级别的堆叠规则。
2. 支持 `GregTech gt.metaitem.*` 一类物品。
3. 支持矿物辞典匹配。

## 规则文件位置

默认文件位置：

`config/stackup/rules.su`

可通过配置项 `rulesFileName` 修改文件名，但仍建议放在 `config/stackup/` 目录下。

## 与旧脚本的关系

当前版本处于过渡阶段：

1. 旧版 StackUp 脚本仍可继续使用。
2. DSL v2 已接入加载与重载流程。
3. 后续版本会逐步把行为主路径迁移到 DSL v2 与新的运行时堆叠服务。

因此建议：

1. 新规则优先写在 `rules.su`。
2. 老规则逐步迁移，不要继续扩写旧 DSL。

## 已支持的核心语法

```text
item = minecraft:egg -> 64
item = minecraft:*_ball -> 128
mod = thermal -> 1024
ore = ingotSteel -> 2048

item in [minecraft:egg, minecraft:snowball, gregtech:gt.metaitem.01@11305] -> 1024
mod in [thermal, ic2, enderio] -> 1024

size > 2 && size < 64 -> 1024
2 < size < 64 -> 1024

item = gregtech:gt.metaitem.01 && meta = 11305 -> 512
ore = ingotSteel || ore = ingotIron -> 1024
```

## 重要限制

1. 仅支持 1.12.2 的矿物辞典，不支持标签。
2. 不支持任意脚本执行。
3. 不支持正则表达式，只支持 `*` 通配。
4. 规则按文件顺序执行。

## 重载方式

进入游戏后执行：

```text
/stackup reload
```

控制台会输出 DSL 规则加载数量与错误信息。
