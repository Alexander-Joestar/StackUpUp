# StackUpUp v0.2.1 — Agent Card

MC 1.12.2 stack control, Kotlin + MixinBooter + min ASM. Server-only testing.

## Architecture
Kotlin: rules/config/runtime/automation. Java: coremod (no Kotlin stdlib risk).
Rule files: `config/stackupup/main.su` / `user.su` / `<save>/data/stackupup/world.su`
Syntax ref: `example.su` (auto-overwritten, not parsed). Reload: `/stackupup reload`.

DSL v2 fields: item, mod, type, ore, meta/metadata, size. `&&` > `||`. No parens.  
Actions: `-> 128` (set), `-> +N`, `-> *N`, `-> /N`. Chained: `-> *2 -> +10`.

## Key files
`StackUpUp.kt` `RuleRuntimeCoordinator.kt` `StackLimitHooks.kt` `limit/StackLimitService.kt` `rules/parse/DslParser.kt` `rules/compile/RuleConditionCompiler.kt` `dev/DevAutomationServerDriver.kt`

`src/main/java/io/alexjoest/stackupup/core/` — 9 Java coremod files (NO Kotlin stdlib)
`src/main/kotlin/io/alexjoest/stackupup/mixin/` — 20 early + 12 late Mixin targets

## Rules pipeline
`StackContext` → `StackContextResolver` → `StackLimitService` (ConcurrentHashMap cache) → rule chain eval  
`size` = baseLimit. `ItemMixin` for normal items, `ItemStackMixin` falls back.

## Compat strategy
Fixed targets → late Mixin. `FixedCompatTargets.java` is single source of truth.  
Dynamic ASM only for runtime-discovered IInventory/IItemHandler/Slot. `CompatibilityLimitPatch.planFor()` is sole entry.

## Test
```bash
./gradlew test && ./gradlew build
```
`runServerAutoTestMatrix` covers: GT metadata, RS extraction, CyclopsCore/ColossalChests/Forge wrappers.
Guardrail tests: `CompatibilityLimitPatchTest` `DynamicCompatTransformerTest` `ItemStackPatchTest` `MixinBooterIntegrationTest` etc.

## Constraints
- `core/` must not reintroduce Kotlin collections/heavy abstractions
- Constructor `@ModifyConstant` handlers must be `static`
- Prefer `MixinExtras`; do not fall back to `@Redirect` or ASM
- No double-application of rules on same path
- `dev` probes may use reflection/proxies but must not consolidate into one giant file
