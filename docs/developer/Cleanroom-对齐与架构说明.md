# StackUpUp Architecture (CleanroomMC 1.12.2)

Kotlin (rules/config/runtime/automation) + Java (coremod, no stdlib) + MixinBooter + min ASM.

## Runtime pipeline
`StackContext` → `StackContextResolver` → `StackLimitService` (CHM cache) → rule chain eval

Unified input: `itemId modId metadata type baseLimit oreNames`.

## Item/Slot entry points
Items: `ItemMixin` (normal) + `ItemStackMixin` (override fallback).  
Slots: `resolveDynamicSlotLimit` + `resolveItemHandlerSlotLimit`.  
`SlotItemHandler#getItemStackLimit` must consider both `maxStackSize` AND `getSlotStackLimit()`.

## Config & rules
Directory: `config/stackupup/`. Files: `main.su` / `user.su` / `<save>/data/stackupup/world.su`.  
Syntax ref: `example.su` (auto-overwritten, not parsed). Reload: `/stackupup reload`.

## DSL v2 bounds
Fields: item mod type ore meta/metadata size.  
Ops: `= != > >= < <=`. Lists: `field in [...]`. Range: `2 < size < 64`.  
Logic: `&&` > `||` (no parens). Action chain: `-> 128 / +32 / *2 / /2` then chain.

## Mixin / ASM boundary
Fixed targets → late Mixin (32 total: 20 early + 12 late). `FixedCompatTargets.java` = single truth.  
Dynamic ASM only for runtime-discovered IInventory/IItemHandler/Slot.  
`CompatibilityLimitPatch.planFor()` = sole decision entry.

## Test
```bash
./gradlew test && ./gradlew build
```
Covers: GT metadata, RS extraction, CyclopsCore/Chests/Forge wrappers.
