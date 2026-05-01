# StackUpUp Agent Entry

MC 1.12.2 stack control mod (Kotlin + MixinBooter). DSL v2 rules, server-only validation.

Read order: `stackupup-agent.md` → `Cleanroom.md` → `ASM-迁移状态.md` → `runServer-自动化回归.md` → `remainder-system.md`

## Core files
`StackUpUp.kt` `RuleRuntimeCoordinator.kt` `StackLimitHooks.kt` `limit/StackLimitService.kt` `rules/parse/DslParser.kt` `rules/compile/RuleConditionCompiler.kt` `dev/DevAutomationServerDriver.kt`

## Verify
```bash
./gradlew test && ./gradlew build
```
Windows: `\gradlew.bat runServerAutoTestMatrix`

Priority: compress boilerplate, migrate fixed targets to Mixin, keep dynamic ASM minimal, no regressions.
