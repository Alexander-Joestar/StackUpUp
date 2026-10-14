# StackUpUp

**Raise item stack limits beyond 64 for Minecraft 1.12.2.**

[![CurseForge](https://img.shields.io/badge/CurseForge-StackUpUp-orange)](https://www.curseforge.com/minecraft/mc-mods/stackupup)

StackUpUp is a stack-limit mod for Minecraft 1.12.2 modpacks and servers. It lets pack authors describe stack-size
rules in text files, with first-class support for metadata items, Ore Dictionary names, vanilla inventory paths, and
compatibility patches for common hard-coded `64` limits.

Chinese README: [README.md](README.md) (Chinese)

## Status

- Target: Minecraft **1.12.2** + Forge **14.23.5.2847**
- Current version: **0.2.4**
- Rule system: DSL v2, using `.su` files or Markdown `.su.md` containers with `state` and `gate`
- Compatibility layer: [MixinBooter](https://github.com/CleanroomMC/MixinBooter) **11.17** ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mixin-booter)) + [CleanMix](https://github.com/CleanroomMC/CleanMix) **0.7.2**; `IMixinConnector` registers always-on early/baseline and mod-presence-selected ordinary late Mixin configs, then adds separate capacity configs when inventory expansion is enabled. `StackUpUpCore` conditionally registers the narrow `InventoryStackLimitTransformer` only when the startup switch is enabled and there is no conflict; it targets getters on non-Minecraft `IInventory` classes. MixinBooter 10.7 and the old loaders are historical baselines only

## Download

Download StackUpUp from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/stackupup).

## Installation

1. Install Minecraft **1.12.2** and Forge **14.23.5.2847**.
2. Install the required dependencies:
    - [MixinBooter](https://github.com/CleanroomMC/MixinBooter) **11.17** ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mixin-booter))
    - [Forgelin-Continuous](https://github.com/CleanroomMC/Forgelin-Continuous) **2.3.0.0** ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/forgelin-continuous))
      Current project build validation uses these versions; other versions have not been verified by this repository.
3. Put the StackUpUp jar into the `mods/` folder.
4. Start the game or server once so the config and rule directories are generated.

## Related Projects

- Infrastructure: [Cleanroom Loader](https://github.com/CleanroomMC/Cleanroom), [MixinBooter](https://github.com/CleanroomMC/MixinBooter) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mixin-booter)), [CleanMix](https://github.com/CleanroomMC/CleanMix), [Forgelin-Continuous](https://github.com/CleanroomMC/Forgelin-Continuous) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/forgelin-continuous)), and [RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle).
- Storage and automation: [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2)), [AE2 Supergiant](https://github.com/CleanroomMC/Applied-Energistics-2), [Refined Storage](https://github.com/refinedmods/refinedstorage) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/refined-storage)), [Simple Storage Network](https://github.com/Lothrazar/StorageNetwork) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/simple-storage-network)), and [Integrated Dynamics](https://github.com/CyclopsMC/IntegratedDynamics) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/integrated-dynamics)).
- Industry and technology: [NuclearCraft: Overhauled](https://github.com/tomdodd4598/NuclearCraft) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/nuclearcraft-overhauled)), [TechReborn](https://github.com/TechReborn/TechReborn) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/techreborn)), [RebornCore](https://github.com/TechReborn/RebornCore) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/reborncore)), [Ender IO](https://github.com/SleepyTrousers/EnderIO-1.5-1.12) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/ender-io)), [GregTech CEu](https://github.com/GregTechCEu/GregTech) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/gregtech-ce-unofficial)), [IndustrialCraft 2](https://www.curseforge.com/minecraft/mc-mods/industrial-craft), and [ProjectE](https://github.com/sinkillerj/ProjectE) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/projecte)).
- Building and inventories: [Mantle](https://github.com/SlimeKnights/Mantle) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mantle)), [Tinkers' Construct](https://github.com/SlimeKnights/TConstruct) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/tinkers-construct)), [LimeLib](https://www.curseforge.com/minecraft/mc-mods/limelib), [Immersive Engineering](https://github.com/BluSunrize/ImmersiveEngineering) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/immersive-engineering)), [Colossal Chests](https://github.com/CyclopsMC/ColossalChests) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/colossal-chests)), [Actually Additions](https://github.com/Ellpeck/ActuallyAdditions) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/actually-additions)), and [Brandon's Core](https://github.com/bradleyq/BrandonsCore) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/brandons-core)).

## Rule Files

Rules live under `config/stackupup/`. The recommended entry point is `config/stackupup/main.su`; use the Markdown
container `config/stackupup/main.su.md` when you want comments, state switches, or conditional gates in the same file.

Later matching rules continue from or override the result produced by earlier rules. The current effective evaluation
order is:

1. `<save>/data/stackupup/main.su.md`.
2. `config/stackupup/*.su.md`, sorted by file name, excluding example files.
3. Legacy `config/stackupup-rules.su`, only when `main.su` does not exist.
4. `<save>/data/stackupup/world.su`.
5. `config/stackupup/*.su`, sorted by file name, excluding `user.su` and example files; `main.su` is included in this step.
6. `config/stackupup/user.su`.

See [docs/DSL-v2-规则示例.md](docs/DSL-v2-%E8%A7%84%E5%88%99%E7%A4%BA%E4%BE%8B.md) (Chinese) for syntax examples.

## Commands

Commands: `/stackupup reload`, `/stackupup edit`, `/stackup state get <name>`, `/stackupup state set <name> <value>`, `/stackupup inspect [slot N]`, and `/stackupup inspect block <x> <y> <z> <none|down|up|north|south|west|east> [slot N]`.

- `inspect` read-only inspects the server player's currently open container; `inspect slot N` focuses on one container slot. Console/command-block senders can use `inspect block x y z none|down|up|north|south|west|east [slot N]` in a loaded chunk to query an ordinary inventory or an item-handler capability on the selected side.
- By default, at most 8 slots are listed. The report shows Container, Slot, inventory/handler class names and object IDs local to that report, plus Forge `ownedPackages`-based modId candidates where possible. Multiple candidates are marked ambiguous; unknown ownership is UNKNOWN. Class names are diagnostic evidence only and cannot currently be used as blacklist settings. `InvWrapper.getInv()` is a supported public delegate accessor; other inaccessible handler delegates are explicitly UNKNOWN rather than reflectively inspected.
- Output includes a copyable candidate snippet for `compat.inventoryStackLimitBlacklist.modIds` only when there is one supported modId candidate; the command does not edit configuration. An item's registered mod is not necessarily the inventory owner, so it is never used to infer inventory modId. Inspection does not insert/extract, simulate writes, or prove real capacity or conservation. A modId blacklist may affect other inventory classes recognized by that mod's `ownedPackages`; add only reviewed entries.

- `reload`: reload rule files.
- `edit`: open the main rule file on the client.
- `state get <name>`: read a state from the current save's `<save>/data/stackupup/main.su.md`; other `.su.md` files do not share these states.
- `state set <name> <value>`: update a state in the same file; `<value>` is case-insensitive and accepts `true` / `false`, `1` / `0`, `yes` / `no`, and `on` / `off`; a `reload` is triggered only when the write actually changes the evaluation result of some gate.

If `config/stackupup/main.su` is absent while the legacy `config/stackupup-rules.su` exists, the first DSL-enabled
`reload` includes the legacy file through the fallback and creates the main file; later `reload`s may then no longer
load the legacy file, so its rules may stop taking effect (migration behavior is not finalized).

## DSL Examples

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

Actions can be chained to keep transforming the current result, e.g. `item = gregtech:gt.metaitem.01@11305 && ore = ingotSteel -> 1024`,
`material = steel && mod = gregtech -> 1024`, `ore = ingotSteel -> 512 -> *2`.

`material` is an optional match field: it only has a value when GregTech is loaded and the item can be resolved to a GT
material; when GT is not loaded, resolution fails, or the item is not a GT material item, every `material` condition
(including `!=` and list matches) is treated as not matched. Use the material registry name: native GT materials can use
names such as `steel`; use the `modid:name` format when you need to distinguish materials across mods, without treating
unverified concrete material IDs as examples. This does not promise support for every GT item.

The old `category` field is not needed on 1.12.2 and should not be used, e.g. `category = potion -> 32`,
`category = enchanted_book -> 16`.

## Client Display

Large stack counts are automatically scaled or abbreviated in inventory slots; version 0.2.4 adds `alwaysCompactNumbers`,
which forces capped compact text before fitting and any font scaling, e.g. `1-999`, `1K-99K`, `0.1M-99M`, `0.1B-2.1B`.

If abbreviated slot text is not enough context, enable the tooltip stack display option to show the current count and
max stack limit as `count/limit`.

## Configuration

After the first game or server launch, the configuration file is `config/stackupup.cfg`; its groups match the in-game
configuration screen. `/stackupup reload` does not re-read the startup inventory-capacity switch or blacklist; restart
after changing either setting.

- `general.maxStackSize`: global compatibility stack limit, default `64`; rule results and compatibility entry points are clamped to this value.
- `compat.vanilla.craftingSlotLimit`: workbench grid and crafting-result slot limit, default `64`. `0` keeps vanilla/default behavior; values above `0` use the custom limit. This is mainly a Performance setting: holding Shift to batch-craft very large stacks can cause severe lag, not a balance switch. The effective limit can still be reduced by `general.maxStackSize` and item/recipe limits.
- `compat.nuclearcraft.speedUpgradeLimit` and `compat.nuclearcraft.energyUpgradeLimit`: NuclearCraft speed/energy upgrade stack limits, default `0` (keep NuclearCraft's native behavior); values above `0` use a custom limit. These settings have no effect when NuclearCraft is not loaded.
- `compat.inventoryExpansion.enabled`: startup inventory-capacity expansion switch, default `true` for new or missing configuration (broad-default); explicitly set `false` only as a restart-time emergency fallback. When enabled it registers the narrow `IInventory#getInventoryStackLimit()` getter transformer and queues optional capacity Mixin configs. When off, those optional capacity hooks are skipped; ItemStack rules, always-loaded early/baseline configs, and ordinary late configs remain. The dedicated `InventoryWriteLimitMixin` in `mixins.stackupup.baseline.early.json` still calls `resolveInventoryWriteLimit`; neither this switch nor `modIds` disables that writer-safety path.
- `compat.inventoryStackLimitBlacklist.modIds`: selectively bypass capacity expansion for listed mods and skip their capacity-late configs; an empty list is a no-op. `/stackupup inspect` can suggest a candidate, but is not a safety proof.

The blacklist uses Forge `ownedPackages` and exact package boundaries for the actual receiver or supported public owner. Merely extending a listed superclass or implementing its interface does not automatically exclude an unlisted addon. Shared objects without a public owner, private delegates, and receiverless constructor paths can remain UNKNOWN. Startup fails if a loaded mod has no attributable packages; an ID for a mod that is not loaded has no effect. This is not a whole-mod disable switch. MixinBooter's `general.blacklistedConfigs`, by contrast, suppresses an entire named config (which may contain safety Mixins) and is not a capacity-only blacklist.

## Compatibility

StackUpUp usually works out of the box for mods that follow vanilla stack-size semantics. Mods that hard-code `64`,
bypass `ItemStack#getMaxStackSize()`, or implement custom inventory logic may need targeted patches.

The core safety rule is: **advertised capacity must not be larger than real write capacity.** Unknown `IItemHandler`
implementations are not dynamically expanded. Even when an unknown `IItemHandler#getSlotLimit()` literally returns 64,
that is not proof that the real write capacity can be raised. Advertising a higher value in that case lets vanilla push
too many items into storage that cannot actually accept them, which can cause truncation, item loss, or conflicts with the
mod's own overflow handling.

The current architecture combines a narrow coremod getter transformer with separated Mixin profiles. `StackUpUpCore`
registers `InventoryStackLimitTransformer` only when `compat.inventoryExpansion.enabled=true` and there is no detected
conflict; it targets `getInventoryStackLimit()I` (including the 1.12 SRG name) on concrete non-Minecraft `IInventory`
classes. It is not the removed broad dynamic ASM replacement layer. Through `IMixinConnector`,
`StackUpUpMixinConnector` always queues ordinary early and baseline early configs, and queues ordinary late configs when
the target mod is present. It adds `mixins.stackupup.capacity.early.json` and `mixins.stackupup.capacity.late.*.json` only
when expansion is enabled; `modIds` skips only capacity-late configs for listed mods.

Some ordinary late modules in the connector are listed below; configs are queued only when the target mod is present, and registration does not verify its real write path:

- [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2))
- [Applied Energistics 2 Supergiant](https://github.com/CleanroomMC/Applied-Energistics-2)
- [Actually Additions](https://github.com/Ellpeck/ActuallyAdditions) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/actually-additions))
- [Brandon's Core](https://github.com/bradleyq/BrandonsCore) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/brandons-core))
- [Colossal Chests](https://github.com/CyclopsMC/ColossalChests) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/colossal-chests))
- CyclopsCore
- [Ender IO](https://github.com/SleepyTrousers/EnderIO-1.5-1.12) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/ender-io))
- [GregTech CEu](https://github.com/GregTechCEu/GregTech) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/gregtech-ce-unofficial))
- [IndustrialCraft 2](https://www.curseforge.com/minecraft/mc-mods/industrial-craft)
- [Mantle](https://github.com/SlimeKnights/Mantle) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/mantle))
- [NuclearCraft: Overhauled](https://github.com/tomdodd4598/NuclearCraft) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/nuclearcraft-overhauled))
- [TechReborn](https://github.com/TechReborn/TechReborn) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/techreborn)), via [RebornCore](https://github.com/TechReborn/RebornCore) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/reborncore))
- [Refined Storage](https://github.com/refinedmods/refinedstorage) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/refined-storage))
- [Simple Storage Network](https://github.com/Lothrazar/StorageNetwork) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/simple-storage-network))
- [Integrated Dynamics](https://github.com/CyclopsMC/IntegratedDynamics) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/integrated-dynamics))
- [LimeLib](https://www.curseforge.com/minecraft/mc-mods/limelib)
- [Immersive Engineering](https://github.com/BluSunrize/ImmersiveEngineering) ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/immersive-engineering))
- IC2 Classic: `mixins.stackupup.late.ic2classic.json` is an ordinary config queued when the mod is present. Its `SlotBaseMixin` caps the reported slot limit at native 64 and is not affected by the capacity switch or an `ic2` entry in `modIds`; only its separate `mixins.stackupup.capacity.late.ic2classic.json` config is subject to those controls.
- Farmer's Delight Legacy: the separate optional `mixins.stackupup.capacity.late.farmersdelight.json` Mixin currently names only Basket, Cabinet, Cooking Pot, and Skillet inventory targets. This does not cover every inventory or prove real write capacity.

These are source registration/target details, not third-party runtime validation or blanket compatibility promises.

### MixinBooter config-level blacklist

`compat.inventoryStackLimitBlacklist.modIds` selectively bypasses capacity expansion. MixinBooter's blacklist instead
suppresses a whole Mixin config and may disable safety behavior in that config. Use it only when the entire config should
be disabled; edit `config/mixinbooter.cfg` and add its file name to `blacklistedConfigs` in the `general` section:

```
general {
    # Mixin configurations that should never be loaded.
    S:blacklistedConfigs <
        mixins.stackupup.late.enderio.json
     >
}
```

- The entry is the **config file name** (including `.json`), not a toggle name. MixinBooter matches the whole string
  exactly, so `enderio` or `EnderIO` will not match.
- Restart the game or server afterwards: MixinBooter reads this config and applies the blacklist during the coremod
  phase, so a runtime reload such as `/stackupup reload` will neither apply nor undo it.
- Only the named config is suppressed; ordinary and capacity configs for the same mod are separate, so blacklisting
  one does not automatically suppress the other. Restart after changing it. Do not blacklist
  `mixins.stackupup.early.json` or `mixins.stackupup.baseline.early.json`, as that disables base or dedicated writer-safety paths.

The source currently registers 19 ordinary late configs; separate capacity configs are registered and queued only
when inventory expansion is enabled:

```
mixins.stackupup.late.actuallyadditions.json
mixins.stackupup.late.ae2.json
mixins.stackupup.late.ae2supergiant.json
mixins.stackupup.late.brandonscore.json
mixins.stackupup.late.colossalchests.json
mixins.stackupup.late.cyclopscore.json
mixins.stackupup.late.enderio.json
mixins.stackupup.late.farmersdelight.json
mixins.stackupup.late.gregtech.json
mixins.stackupup.late.ic2.json
mixins.stackupup.late.ic2classic.json
mixins.stackupup.late.immersiveengineering.json
mixins.stackupup.late.integrateddynamics.json
mixins.stackupup.late.limelib.json
mixins.stackupup.late.mantle.json
mixins.stackupup.late.nuclearcraft.json
mixins.stackupup.late.refinedstorage.json
mixins.stackupup.late.storagenetwork.json
mixins.stackupup.late.techreborn.json
```

For implementation notes, see [docs/StackUpUp-实现与兼容性说明.md](docs/StackUpUp-%E5%AE%9E%E7%8E%B0%E4%B8%8E%E5%85%BC%E5%AE%B9%E6%80%A7%E8%AF%B4%E6%98%8E.md) (Chinese).

## Differences From StackUp

Compared with StackUp, StackUpUp targets Minecraft **1.12.2**, uses **DSL v2** rules designed for item IDs, metadata,
Ore Dictionary names, and creative-tab matching, centers compatibility on **MixinBooter + Mixin**, and adds client display
scaling, abbreviation, and tooltip support for very large stack counts.

## Development And Verification

**The build requires JDK 25 and Gradle 9.2+.** Minecraft is wired in through RetroFuturaGradle (RFG) **2.0.2**, whose
plugin classes are compiled to class file 69 (Java 25) and which itself requires Gradle >= 9.2. Running Gradle on JDK 21
or lower fails during configuration (`UnsupportedClassVersionError: com/gtnewhorizons/retrofuturagradle/UserDevPlugin
... class file version 69.0`); the build never starts. The bundled wrapper is Gradle 9.4.0, which satisfies the
minimum, so no wrapper change is needed.

This constrains the **Gradle JVM used for builds** only: the project Java toolchain stays pinned to 8
(`build-logic/convention/src/main/kotlin/jvm.gradle.kts:9`), and the dev client is launched by that Java 8 toolchain.
The mod's runtime requirements are unchanged.

Common verification commands (with `JAVA_HOME` pointing at JDK 25):

```powershell
.\gradlew.bat test
.\gradlew.bat compileTestKotlin
.\gradlew.bat spotlessCheck
```

The repository also includes local development auto-test tasks for server and client rule checks.
See [docs/runServer-自动化回归.md](docs/runServer-%E8%87%AA%E5%8A%A8%E5%8C%96%E5%9B%9E%E5%BD%92.md) (Chinese).

## Origin

StackUpUp descends from [StackUp](https://github.com/asiekierka/StackUp)
([CurseForge](https://www.curseforge.com/minecraft/mc-mods/stackup), LGPLv3).
