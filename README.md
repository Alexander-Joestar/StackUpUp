# StackUpUp

**让 Minecraft 1.12.2 的物品突破 64 上限。**

[![CurseForge](https://img.shields.io/badge/CurseForge-StackUpUp-orange)](https://www.curseforge.com/minecraft/mc-mods/stackupup)

面向 1.12.2 整合包作者和服主的堆叠上限模组。用简单的文本规则文件控制任意物品的堆叠数量，支持 GregTech CEu 等大量使用 metadata 的模组。

## 下载

**[CurseForge 下载页](https://www.curseforge.com/minecraft/mc-mods/stackupup)**

## 安装

1. 需要 Minecraft **1.12.2** + **Forge 14.23.5.2847**
2. 安装依赖：[MixinBooter](https://www.curseforge.com/minecraft/mc-mods/mixin-booter)（≥ 10.0）、[Forgelin-Continuous](https://www.curseforge.com/minecraft/mc-mods/forgelin-continuous)（≥ 2.1.0.0）
3. 将 StackUpUp 的 jar 放入 `mods/` 文件夹

## 怎么用

规则文件在 `config/stackupup/` 目录下，支持两种格式：

| 格式 | 文件 | 说明 |
|------|------|------|
| `.su` | `main.su`、`user.su` 等 | 纯 DSL 文本规则，全局配置级别 |
| `.su.md` | `main.su.md` 等 | Markdown 格式，支持 state 变量和 gate 条件，适合整合包分发 |

主入口文件是 `config/stackupup/main.su`。修改后在游戏内输入：

```
/stackupup reload    重载规则
/stackupup edit      打开主规则文件（仅限客户端）
```

## 规则示例

```text
# 普通物品
item = minecraft:egg -> 256

# 所有可堆叠物品（排除工具、盔甲、桶等 baseSize=1 的物品）
item = * -> 128

# 按模组统一
mod = thermal -> 1024

# 按矿物辞典
ore = ingotSteel -> 2048

# 列表匹配
item in [minecraft:egg, minecraft:snowball] -> 128

# metadata 精确匹配（完整写法）
item = gregtech:gt.metaitem.01 && meta = 11305 -> 512

# metadata 精确匹配（@简写，等价于上面）
item = gregtech:gt.metaitem.01@11305 -> 512

# 冒号简写（适用于只有两级的物品 ID）
item = minecraft:wool:14 -> 512

# 数值范围
2 < size < 64 -> 256
100 < meta < 300 -> 512

# 按创造模式标签页
tab = buildingBlocks -> 256

# 按物品类别（药水瓶、附魔书）
~~category = potion -> 32~~（1.12.2无需category）
~~category = enchanted_book -> 16~~（1.12.2无需category）

# 多条件组合
item = gregtech:gt.metaitem.01@11305 && ore = ingotSteel -> 1024

# 运算符链：先设值，再翻倍
ore = ingotSteel -> 512 -> *2
```

更多写法见 [docs/DSL-v2-规则示例.md](docs/DSL-v2-%E8%A7%84%E5%88%99%E7%A4%BA%E4%BE%8B.md)。

## 和原版 StackUp 的区别

- 目标版本锁定 **1.12.2**，不做多版本兼容
- 规则系统使用 **DSL v2**，支持 `item + meta + ore` 组合
- 兼容层优先走 **MixinBooter + Mixin**，逐步收缩旧 ASM
- 不提供自定义字段注册 API；需要按类别匹配的场景，通过矿物辞典（`ore = ...`）或 `tab` 等字段即可覆盖

## 兼容性

StackUpUp 尽量在更上层统一兼容，不围绕单个模组堆特判。对遵循原版堆叠语义的模组通常直接生效；对自行写死 64 或有特殊库存逻辑的模组可能需要额外补丁。详细说明见 [docs/StackUpUp-实现与兼容性说明.md](docs/StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md)。

## 来源

脱胎于 [StackUp](https://github.com/asiekierka/StackUp)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/stackup)，LGPLv3）。StackUpUp 是在其基础上面向 1.12.2 长期维护与现代化迁移的分支。
