# StackUpUp

**让 Minecraft 1.12.2 的物品突破 64 上限。**

[![CurseForge](https://img.shields.io/badge/CurseForge-StackUpUp-orange)](https://www.curseforge.com/minecraft/mc-mods/stackupup)

面向 1.12.2 整合包作者和服主的堆叠上限模组。用文本规则控制物品堆叠数量，重点照顾 `metadata` 子物品和常规库存路径。

## 下载

**[CurseForge 下载页](https://www.curseforge.com/minecraft/mc-mods/stackupup)**

## 安装

1. 需要 Minecraft **1.12.2** + **Forge 14.23.5.2847**
2. 安装依赖：[MixinBooter](https://www.curseforge.com/minecraft/mc-mods/mixin-booter)（≥ 10.0）、[Forgelin-Continuous](https://www.curseforge.com/minecraft/mc-mods/forgelin-continuous)（≥ 2.1.0.0）
3. 将 StackUpUp 的 jar 放入 `mods/` 文件夹

## 用法

规则文件在 `config/stackupup/`。主入口是 `config/stackupup/main.su`，需要 `state` / `gate` 时再用 `.su.md`。

```
/stackupup reload    重载规则
/stackupup edit      打开主规则文件（仅限客户端）
```

## 示例

```text
item = minecraft:egg -> 256
item = * -> 128
mod = thermal -> 1024
ore = ingotSteel -> 2048
item in [minecraft:egg, minecraft:snowball] -> 128
item = gregtech:gt.metaitem.01 && meta = 11305 -> 512
item = gregtech:gt.metaitem.01@11305 -> 512
item = minecraft:wool:14 -> 512
2 < size < 64 -> 256
tab = buildingBlocks -> 256
~~category = potion -> 32~~（1.12.2无需category）
~~category = enchanted_book -> 16~~（1.12.2无需category）
item = gregtech:gt.metaitem.01@11305 && ore = ingotSteel -> 1024
ore = ingotSteel -> 512 -> *2
```

更多写法见 [docs/DSL-v2-规则示例.md](docs/DSL-v2-%E8%A7%84%E5%88%99%E7%A4%BA%E4%BE%8B.md)。

## 和原版 StackUp 的区别

- 目标版本锁定 **1.12.2**
- 规则系统使用 **DSL v2**
- 兼容层以 **MixinBooter + Mixin** 为主

## 兼容性

对遵循原版堆叠语义的模组通常直接生效；对自行写死 `64` 或有特殊库存逻辑的模组可能需要额外补丁。见 [docs/StackUpUp-实现与兼容性说明.md](docs/StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md)。

## 来源

脱胎于 [StackUp](https://github.com/asiekierka/StackUp)（[CurseForge](https://www.curseforge.com/minecraft/mc-mods/stackup)，LGPLv3）。
