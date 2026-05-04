# DSL v2 迁移说明

本文面向已经用过旧版 StackUp 规则、现在准备迁到 StackUpUp 新规则系统的玩家和整合包作者。

## 1. 两种格式

StackUpUp 支持两种规则格式，DSL 语法完全相同：

| 格式 | 主入口 | 说明 |
|------|--------|------|
| `.su` | `config/stackupup/main.su` | 纯 DSL 文本规则，全局配置级别 |
| `.su.md` | `config/stackupup/*.su.md` | Markdown 容器，支持 state 变量和 gate 条件 |

主入口文件是 `config/stackupup/main.su`，如果不存在会自动创建带示例的模板。

两种格式可以共存，都会被加载。区别在于 `.su.md` 额外支持 state 声明和 gate 条件表达式。

## 2. 从旧 `.su` 到 DSL v2

旧版 StackUp 的 `.su` 文件和 StackUpUp 的 `.su` 文件使用相同的文件扩展名，但 DSL v2 的语法已经完全不同。

迁移不是破坏性升级：

1. 老整合包里的旧 `.su` 格式规则需要改写 DSL 语法，但文件本身不需要改名。
2. DSL v2 的语法更强，支持 `item + meta + ore` 组合、列表匹配、条件组合等。
3. 如果不需要 state 和 gate，直接用 `.su` 就够了。
4. 如果需要阶段控制，可以用 `.su.md` 格式。

## 3. 为什么要迁移

旧思路最大的限制，是很难优雅处理：

1. 同一个物品 ID，下挂很多 `metadata` 子物品。
2. 需要按矿物辞典统一处理。
3. 需要把多个条件组合起来匹配。
4. 需要把整合包阶段或进度状态持久化下来。

DSL v2 把规则匹配统一到 `item`、`meta`、`ore`、`mod`、`size`、`state()`、`modLoaded()` 这些字段。

## 4. 常见迁移写法

### 单物品

```text
item = minecraft:egg -> 256
```

### 模组统一

```text
mod = thermal -> 1024
```

### metadata 精确匹配

```text
# 完整写法
item = gregtech:gt.metaitem.01 && meta = 11305 -> 512

# @简写（等价于上面）
item = gregtech:gt.metaitem.01@11305 -> 512

# 冒号简写（适用于只有两级的物品 ID）
item = minecraft:wool:14 -> 512
```

### 矿物辞典

```text
ore = ingotSteel -> 2048
```

### 列表匹配

```text
item in [minecraft:egg, minecraft:snowball] -> 128
```

### 用 `.su.md` 做阶段控制

```text
# state
- expert_mode = false

# rules
## state("expert_mode") && modLoaded("thermal")

mod = thermal -> 2048
```

## 5. 新格式的关键变化

```text
旧：只有 .su 格式，没有 state/gate 支持
新：.su 是主入口，.su.md 额外支持 state 和 gate
```

```text
旧：把 gate 逻辑放在别的文件里或靠外部约定
新：在 .su.md 的标题里直接写 gate 表达式
```

```text
旧：state 往往分散在别处
新：state 直接写在 .su.md 的 # state 区域里，和规则同文件保存
```

## 6. 兼容性说明

1. `.su` 始终是主入口格式，不会被淘汰。
2. `.su.md` 是可选的能力扩展，适合需要 state/gate 的场景。
3. 同一个整合包里两种格式可以共存，都会被加载。
4. 世界级 `.su.md` 的 state 随存档独立保存，`config` 侧的是默认模板。

## 7. 注释

```text
# 单行注释
// 单行注释
/* 块注释 */
```

## 8. 重载方式

```text
/stackupup reload              重载规则
/stackupup edit                打开主规则文件（仅限客户端）
/stackupup state get phase1    查看 state
/stackupup state set phase1 true  设置 state
```

## 9. 建议

1. 简单场景直接用 `.su`，不需要搬去 `.su.md`。
2. 需要阶段控制或整合包分发时才用 `.su.md`。
3. 精确指定某个 metadata 变体时用 `item@meta` 简写，比 `&& meta = ...` 更简洁。
4. gate 表达式尽量短，复杂逻辑拆成多个标题层次。
5. 多条规则都命中时，后面的规则会覆盖或叠加前面的结果。
