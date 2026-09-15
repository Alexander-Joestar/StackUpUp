# StackUpUp

**让 Minecraft 1.12.2 的物品突破 64 堆叠上限。**

[![CurseForge](https://img.shields.io/badge/CurseForge-StackUpUp-orange)](https://www.curseforge.com/minecraft/mc-mods/stackupup)

StackUpUp 是面向 Minecraft 1.12.2 整合包作者和服主的堆叠上限模组，用文本规则控制物品堆叠数量，优先覆盖原版库存路径、`metadata` 子物品、矿物辞典匹配，以及常见模组里写死 `64` 的兼容点。

English documentation: [README.en.md](README.en.md)

## 当前状态

- 目标版本：Minecraft **1.12.2** + Forge **14.23.5.2847**
- 当前版本：**0.2.4**
- 规则系统：DSL v2，支持 `.su` 与带 `state` / `gate` 的 `.su.md`
- 兼容层：MixinBooter **11.17** + CleanMix **0.7.2**；通过 `IMixinConnector` 注册 Mixin 配置，ASM 仅保留为旧兼容/早期加载兜底；10.7 与旧 loader 仅为历史基线

## 下载

从 [CurseForge 下载 StackUpUp](https://www.curseforge.com/minecraft/mc-mods/stackupup)。

## 安装

1. 安装 Minecraft **1.12.2** 与 Forge **14.23.5.2847**。
2. 安装依赖：
   - [MixinBooter](https://www.curseforge.com/minecraft/mc-mods/mixin-booter) **11.17**
   - [Forgelin-Continuous](https://www.curseforge.com/minecraft/mc-mods/forgelin-continuous) **2.3.0.0**
   当前项目构建验证使用上述版本；其他版本未由本仓库验证。
3. 将 StackUpUp 的 jar 放入 `mods/` 文件夹。
4. 启动一次游戏或服务端，让模组生成配置与规则目录。

## 规则文件

规则目录为 `config/stackupup/`。主入口推荐 `config/stackupup/main.su`；需要写说明、状态开关或按条件启用规则时，改用 Markdown 容器 `config/stackupup/main.su.md`。

规则按序求值：后命中的规则在先前结果上继续覆盖或叠加。当前有效评估顺序是：

1. `<save>/data/stackupup/main.su.md`。
2. `config/stackupup/*.su.md`，按文件名排序，排除示例文件。
3. 旧兼容文件 `config/stackupup-rules.su`，仅在 `main.su` 不存在时加载。
4. `<save>/data/stackupup/world.su`。
5. `config/stackupup/*.su`，按文件名排序，排除 `user.su` 和示例文件；`main.su` 也属于这一步。
6. `config/stackupup/user.su`。

详细写法见 [docs/DSL-v2-规则示例.md](docs/DSL-v2-%E8%A7%84%E5%88%99%E7%A4%BA%E4%BE%8B.md)。

## 命令

命令：`/stackupup reload`、`/stackupup edit`、`/stackupup state get <name>`、`/stackupup state set <name> <value>`。

- `reload`：重新加载规则。
- `edit`：打开主规则文件，仅客户端可用。
- `state get <name>`：读取当前存档 `<save>/data/stackupup/main.su.md` 中的 state；其他 `.su.md` 不共享这些 state。
- `state set <name> <value>`：修改同一文件中的 state；`<value>` 不区分大小写，接受 `true` / `false` 以及 `1` / `0`、`yes` / `no`、`on` / `off`；仅当该写入使任一 gate 的求值结果实际变化时才触发 `reload`。

若 `config/stackupup/main.su` 尚不存在而旧 `config/stackupup-rules.su` 存在，首次 DSL `reload` 会把旧文件纳入 legacy fallback 并创建主文件；此后 `reload` 可能不再加载旧文件，其中的规则可能不再生效，迁移行为仍未定型。

## DSL 示例

```su
item = minecraft:egg -> 256
item = * -> 128
mod = thermal -> 1024
ore = ingotSteel -> 2048
material = steel -> 2048
item in [minecraft:egg, minecraft:snowball] -> 128
item = gregtech:gt.metaitem.01 && meta = 11305 -> 512
item = gregtech:gt.metaitem.01@11305 -> 512
item = minecraft:wool:14 -> 512
2 < size < 64 -> 256
tab = buildingBlocks -> 256
```

动作可串联，在已有结果上继续调整，例如 `item = gregtech:gt.metaitem.01@11305 && ore = ingotSteel -> 1024`、`material = steel && mod = gregtech -> 1024`、`ore = ingotSteel -> 512 -> *2`。

`material` 是可选匹配字段，仅在 GregTech 已加载且物品可解析出 GT material 时有值；GT 未加载、解析失败或非 GT 材料物品时，所有 `material` 条件（含 `!=` 与列表匹配）都不命中。请使用 material registry name：GT 原生材料可写 `steel` 这类名称，需跨 mod 精确区分时用 `modid:name` 格式；不在示例中列未经验证的具体材料。它不表示支持所有 GT 物品。

`category` 字段曾短暂出现，但 1.12.2 下不再需要，建议不要使用；例如 `category = potion -> 32`、`category = enchanted_book -> 16`。

## 客户端显示

大堆叠数量会在物品槽位里自动缩放或缩写；0.2.4 新增 `alwaysCompactNumbers` 配置项，可在适配槽位和字体缩放前强制使用短文本，例如 `1-999`、`1K-99K`、`0.1M-99M`、`0.1B-2.1B`。

若槽位中的短数字不够清楚，可在配置里开启 Tooltip 堆叠显示，以 `数量/上限` 展示当前数量和最大堆叠上限。

## 兼容性

StackUpUp 对遵循原版堆叠语义的模组通常直接生效；对自行写死 `64`、绕过 `ItemStack#getMaxStackSize()` 或有特殊库存逻辑的模组，可能需要额外补丁。

核心安全原则：**对外广告容量不能大于真实写入容量。** 动态 ASM 只保留给未知旧式 `IInventory` / `Slot` 路径，未知 `IItemHandler` 的动态 ASM 已故意禁用；即使其 `getSlotLimit()` 字面返回 64，也不能据此证明真实写入容量可以扩大，否则 vanilla 可能投入超出库存真实承受能力的物品，触发截断、吞物品或与模组溢出逻辑冲突。

对已登记并尝试加载的目标，StackUpUp 通过 `IMixinConnector` 注册 Mixin 配置，作用于真实 `getInventoryStackLimit()` / `getSlotLimit()` 等容量入口，再让槽位上限跟随；第三方真实写入路径缺源码时为“无源码不可判定”，不能仅凭目标类名或注册结果推断写入能力。旧 ASM 仅作历史兼容和早期加载兜底，不再是新增兼容首选。AE2 方案 A 已归档：热路径原样透传 `insertItem`，运行期依赖 Forge remainder 契约和边界探针，不使用 JSONL 守恒报告，也不采用写入后回填。

当前已登记并尝试加载的 late mixin 目标包括：

- Applied Energistics 2
- Actually Additions
- BrandonsCore
- CyclopsCore
- Ender IO
- IC2
- Mantle
- Refined Storage
- Simple Storage Network
- IntegratedDynamics
- LimeLib
- ImmersiveEngineering

更完整的实现说明见 [docs/StackUpUp-实现与兼容性说明.md](docs/StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md)。

## 和原版 StackUp 的区别

与 StackUp 相比，StackUpUp 锁定 Minecraft **1.12.2**，规则升级为 **DSL v2**（按物品、metadata、矿物辞典、创造标签页分层配置），兼容层以 **MixinBooter + Mixin** 为主（ASM 仅作旧兼容和早期加载兜底），客户端数量显示增加缩放、缩写和 Tooltip 辅助显示。

## 开发与验证

常用验证命令：

```powershell
.\gradlew.bat test
.\gradlew.bat compileTestKotlin
.\gradlew.bat spotlessCheck
```

仓库还提供本地开发自动验收任务，在服务端或客户端启动后验证规则是否生效；详见 [docs/runServer-自动化回归.md](docs/runServer-%E8%87%AA%E5%8A%A8%E5%8C%96%E5%9B%9E%E5%BD%92.md)。

## 依赖升级备忘

这些是备忘，不是待办：

- Gradle wrapper 还是 9.4.0；升级影响整个构建，要改再说。
- `cursegradle` 插件还挂在 `build.gradle.kts:17`；移除会影响发布流程。
- `idea-ext` 还是 1.1.7，能升但收益不大。
- `asm`/`guava`/`gson` 三个 annotationProcessor（`build.gradle.kts:57-59`）**别删**——CleanMix 0.7.2 是 MixinBooter 11.17 生成 refmap 必需的，删了 refmap 就没了。

## 来源

StackUpUp 脱胎于 [StackUp](https://github.com/asiekierka/StackUp)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/stackup)，LGPLv3）。
