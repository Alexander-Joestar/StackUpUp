# Rule Markdown Gates Notes

StackUpUp 的规则容器已经收敛为单文件 Markdown 方案：每个规则集一个 `.su.md` 文件，固定分成两个顶层区域：`# state` 和 `# rules`。

## 文件模型

- `config/`：模板和默认文件，作为只读来源。
- `world/<save>/data/stackupup/`：运行时可写副本，状态修改只落在这里。
- 同一规则集在 `config/` 与 `world/` 下保持同名结构。

## 文件格式

```md
# state
- phase1 = true
- expert_mode = false

# rules
## state("phase1") && modLoaded("storagenetwork")

```stackupup
item = minecraft:egg -> 128
```
```

## 规则约定

- `# state` 只声明布尔状态，使用 `- name = true/false`。
- `# rules` 只放规则分组和 fenced code block。
- 规则块统一使用 ```stackupup。
- gate 表达式只支持 `state("name")`、`modLoaded("modid"[, "modid"...])` 和 `&&` / `||` / `!`。
- gate 写法不引入额外括号优先级，直接依赖 Kotlin 的短路求值。
- `modLoaded` 在加载时可视为常量，接受多个 mod ID（全部加载才为 true）。

## 状态与 API

- `/stackupup state get <name>`
- `/stackupup state set <name> <true|false>`
- `StackUpUp.getState(name)`
- `StackUpUp.setState(name, value)`

## 运行时模型

- Markdown 仅在加载、热重载或状态变更时解析。
- 编译结果进入 `RuleSnapshot`，热路径由 `StackLimitService.resolve()` 直接消费。
- gate 计算与规则匹配不回读 Markdown 源文本。
