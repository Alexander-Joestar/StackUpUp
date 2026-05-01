# StackUpUp Markdown gate 与 state 设计

## 概述

本设计为 StackUpUp 引入一种单文件规则容器格式。
同一个文件里同时保存面向人的说明文字、可持久化的 observable bool 状态，以及已编译的规则块，同时保持解析器和运行时热路径尽量轻。

目标使用者是玩家、整合包作者，以及由脚本驱动的整合逻辑。
运行时必须保持 server/world 级别作用域，并且不能在热路径上解析 Markdown。

## 目标

- 允许作者把规则、注释和持久化 gate 状态写在同一个文件里。
- 只在 rules 区域把 Markdown 标题当成结构标记。
- gate 表达式必须保持纯函数、无副作用。
- 通过 `state("name")` 读取 observable bool，并通过外部写入 API 修改它。
- 支持整合包阶段、任务阶段这类联动逻辑，但不通过动态注入 rule 来实现。
- 保持编译后的规则快照稳定、可缓存。

## 非目标

- 不把这个格式做成通用脚本语言。
- 不允许外部脚本在运行时直接注入规则。
- 不做按玩家或按队伍的 gate 评估。
- 不在每 tick 的堆叠限制查询里解析 Markdown。
- 不在 gate 表达式里暴露任意可变变量。

## 文件模型

每个规则集使用一个 Markdown 文件，例如 `config/stackupup/main.su.md`。

文件按顶层区域分块。第一版至少支持：

- `# state`
- `# rules`

其他顶层标题默认只作为文档内容，或者作为未来扩展点，除非显式赋予语义。

### 区域规则

`state`
- 存放 observable bool 的声明和值。
- 使用简单的行解析。
- 示例：`phase1 = true`
- 这个区域里不允许再出现有语义的嵌套标题。

`rules`
- 存放带 gate 的 Markdown 标题。
- 存放包含 StackUpUp DSL 的 fenced code block。
- 普通 Markdown 文本全部忽略。

### Markdown 语法政策

只有三种 Markdown 构件具有语义：

- `#` 标题
- `-` 列表项，用于 state 声明
- fenced code block

其他全部都作为文档内容，由作者自由书写，编译器忽略并在写回时保留。

语义行本身不能混写注释：

- `# state 这里是注释` 不合法
- `- phase1 = true 这里是注释` 不合法
- `````stackupup 这里是注释` 不合法

如果要给 if 块、state 或规则块写说明，应单独放在语义行的上一行或下一行。
规则代码块内部仍沿用 StackUpUp DSL 已有的注释语法。

## rules 区域语法

在 rules 区域里，标题表示 gate 作用域。

示例：

```md
# rules

## always

```stackupup
item = minecraft:egg -> 128
```

### state("phase1") && modLoaded("storagenetwork")

```stackupup
mod = storagenetwork -> 256
```
```

### 作用域语义

- 标题深度表示嵌套层级。
- 子标题继承父标题 gate，并通过逻辑 AND 合并。
- 同级标题是独立分支。
- `always` 表示无条件启用。
- 只有 `stackupup` 和 `su` 代码块中的内容会作为规则。
- 其他语言的代码块忽略。

## gate 表达式设计

gate 表达式必须是纯布尔表达式。
它们只能包含：

- 函数调用
- 布尔运算符

当前内置函数：

- `state("name")`
- `modLoaded("modid"[, "modid"...])` — 接受多个 mod ID，全部加载才为 true

### gate 约束

- gate 表达式里不允许裸变量。
- 所有 observable bool 读取都必须通过 `state("name")`。
- `modLoaded` 是编译期常量（模组加载后不变），求值时短路优化可跳过其右侧。
- 不允许副作用。
- gate 评估过程中不能触发规则编译或文件写回。

## state 区域设计

state 区域存放与规则同文件的 observable bool。
这样整合包作者能直接看到阶段进度，而不必默认拆成第二个文件。

推荐语法：

```md
# state

- phase1 = true
- expert_mode = false
- gregtech_age = false
```

说明：

- 前导 `-` 只是为了让它看起来更像 Markdown 列表。
- 解析器也应该接受直接写 `name = true/false`，方便作者手写。
- 未知的 state key 在 reload 时应给 warning，而不是静默忽略。
- `state` 区域里的无法识别行，除非看起来像错误的赋值，否则都视为文档内容。

## 持久化模型

observable bool 必须是 world/server 级别作用域。
权威值保存在 `world/<save>/data/stackupup/` 下的 Markdown 文件里，通过 `initWorldMarkdown()` 从 config 模板复制。

### 持久化规则

- state 区域是默认值和当前值的来源。
- 外部命令和脚本只修改 state 值，不直接改规则块。
- 当 gate 状态没变化时，reload 应尽量保留现有已编译快照。
- 如果 state 写回失败，运行时内存状态可以继续变化，但必须给出 warning。
- state 写回必须尽量保留非语义行注释。

### 外部写入来源

预期写入者包括：

- 命令
- 脚本模组
- 任务系统事件
- 世界事件处理器

这些写入者不属于 StackUpUp 的规则引擎内部。

## 运行时模型

运行时采用两阶段模型：

1. 把 Markdown 容器解析成区域和语义块。
2. 只把当前启用的规则块编译成 `RuleSnapshot`。

### 变更触发

只在以下情况重建已编译快照：

- 规则文件变化
- 世界加载
- observable state 变化
- 模组存在性变化导致 active gate 结果变化

触发策略使用被动事件，不使用定时轮询：

1. `StackUpUp.setState(name, value)` 是唯一写入入口，写入后立即触发 reload
2. 不监听文件变动，只在 reload 时读取文件

state 变化与 gate 复算之间采用两阶段事件模型：

1. state 写入只负责发布"已变化"事件，并合并同一 tick 内的多次变更
2. gate 复算只处理受影响的依赖集合
3. 仅当 gate 结果集合发生变化时才触发 reload

这样可以避免把每次布尔写入都直接变成一次完整规则重载。

### 缓存策略

- 如果启用块签名不变，就复用当前快照。
- 如果签名变化，就重建快照并替换当前 `RuleRuntime`。
- 热路径只能读取已编译后的运行时状态。

## 错误处理

解析器应尽量软失败。

必须满足：

- 无效的 state 行发 warning
- 未知 gate 视严重程度发 warning 或编译错误
- 格式错误的 fenced rule block 会被忽略，并报告源位置
- 缺少结束 fence 或标题时，不能把世界加载直接搞崩

warning 必须保留足够上下文，方便作者快速修正文件。

## 兼容性与扩展性

该设计应支持后续扩展，而无需改变基本文件形状。

未来可能加入的 gate 函数：

- `gameStage("id")`
- `gameStages("id")`
- `questCompleted("id")`
- `permission("id")`

未来可能加入的区域：

- `docs`
- `meta`
- `imports`

这些未来区域都应保持可选，并且不能破坏已有文件。

## 解析器策略

推荐解析器形态：

- 轻量级自定义行扫描器
- 区域感知状态机
- 只在 rules 区域里追踪标题深度
- fenced code block 提取器
- 独立的 state 行解析器
- 不支持 gate 括号，降低解析器复杂度

这样比完整 Markdown 渲染器更小，因为这个功能只需要一小部分 Markdown 语义。

## 测试策略

应补充以下测试：

- `state` 区域解析
- 标题嵌套与 gate 继承
- `state("x")` 读取
- `modLoaded("x")` 读取
- gate 表达式拒绝括号
- 未启用块不会进入编译结果
- gate 状态不变时签名保持稳定
- 格式错误块被安全忽略
- 写回 state 时保留非语义行注释
- state 变更仅触发一次合并后的 gate 复算
- gate 复算结果不变时不会触发 reload

## 已确认取舍

- 第一版 gate 表达式不支持括号。
- state 保留在 Markdown 文件中，不默认拆出 companion `.state` 文件。
- 可以有多个 Markdown 规则文件。
- state 写回时必须保留注释行。
